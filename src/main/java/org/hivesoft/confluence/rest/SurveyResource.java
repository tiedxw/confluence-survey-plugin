/**
 * Copyright (c) 2006-2014, Confluence Community
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hivesoft.confluence.rest;

import au.com.bytecode.opencsv.CSVWriter;
import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext;
import com.atlassian.confluence.content.render.xhtml.XhtmlException;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.core.DefaultSaveContext;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.xhtml.api.MacroDefinition;
import com.atlassian.confluence.xhtml.api.MacroDefinitionHandler;
import com.atlassian.confluence.xhtml.api.MacroDefinitionUpdater;
import com.atlassian.confluence.xhtml.api.XhtmlContent;
import com.atlassian.extras.common.log.Logger;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import org.apache.commons.lang3.StringUtils;
import org.hivesoft.confluence.macros.survey.SurveyMacro;
import org.hivesoft.confluence.macros.survey.model.Survey;
import org.hivesoft.confluence.macros.utils.PermissionEvaluator;
import org.hivesoft.confluence.macros.utils.SurveyManager;
import org.hivesoft.confluence.macros.utils.SurveyUtils;
import org.hivesoft.confluence.macros.vote.VoteConfig;
import org.hivesoft.confluence.macros.vote.model.Ballot;
import org.hivesoft.confluence.macros.vote.model.Choice;
import org.hivesoft.confluence.macros.vote.model.Comment;
import org.hivesoft.confluence.rest.callbacks.TransactionCallbackAddAttachment;
import org.hivesoft.confluence.rest.representations.CSVExportRepresentation;
import org.hivesoft.confluence.rest.representations.LockRepresentation;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Path("/pages/{pageId}/surveys")
public class SurveyResource {
  private static final Logger.Log LOG = Logger.getInstance(SurveyResource.class);

  protected final TransactionTemplate transactionTemplate;
  protected final PageManager pageManager;
  protected final XhtmlContent xhtmlContent;
  protected final I18nResolver i18nResolver;
  protected final SurveyManager surveyManager;
  protected final PermissionEvaluator permissionEvaluator;

  public SurveyResource(TransactionTemplate transactionTemplate, PageManager pageManager, ContentPropertyManager contentPropertyManager, XhtmlContent xhtmlContent, I18nResolver i18nResolver, PermissionEvaluator permissionEvaluator) {
    this.transactionTemplate = transactionTemplate;
    this.pageManager = pageManager;
    this.permissionEvaluator = permissionEvaluator;
    this.surveyManager = new SurveyManager(contentPropertyManager, permissionEvaluator);
    this.xhtmlContent = xhtmlContent;
    this.i18nResolver = i18nResolver;
  }

  @GET
  @Path("/{surveyTitle}/export")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getCSVExportForSurvey(@PathParam("pageId") long pageId, @PathParam("surveyTitle") String inSurveyTitle) throws UnsupportedEncodingException {
    final String surveyTitle = URLDecoder.decode(inSurveyTitle, "UTF-8");
    final Page page = pageManager.getPage(pageId);

    if (page == null) {
      return Response.status(Response.Status.NOT_FOUND.getStatusCode()).entity("Specified page with id: " + pageId + " was not found").build();
    }

    if (!permissionEvaluator.canAttachFile(page)) {
      return Response.status(Response.Status.UNAUTHORIZED.getStatusCode()).entity("You are not authorized to add attachments and therefore cannot export surveys.").build();
    }

    final List<Survey> surveys = new ArrayList<Survey>();
    try {
      xhtmlContent.handleMacroDefinitions(page.getBodyAsString(), new DefaultConversionContext(page.toPageContext()), new MacroDefinitionHandler() {
        @Override
        public void handle(MacroDefinition macroDefinition) {
          if (SurveyMacro.SURVEY_MACRO.equals(macroDefinition.getName())) {
            final Map<String, String> parameters = macroDefinition.getParameters();
            String currentTitle = SurveyUtils.getTitleInMacroParameters(parameters);
            if (surveyTitle.equalsIgnoreCase(currentTitle)) {
              final Survey survey = surveyManager.reconstructSurveyFromPlainTextMacroBody(macroDefinition.getBodyText(), page.getContentEntityObject(), macroDefinition.getParameters());
              survey.setTitle(surveyTitle);
              surveys.add(survey);
            }
          }
        }
      });
    } catch (XhtmlException e) {
      final String message = "There was a problem while parsing the Xhtml content: " + e.getMessage() + " for surveyTitle: " + surveyTitle;
      LOG.error(message, e);
      return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
    }

    if (surveys.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Could not find the specified survey macro with title " + surveyTitle + " on the specified page!").build();
    }

    Calendar currentDate = new GregorianCalendar();
    final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'hhmmss");
    final String fileName = surveyTitle + "-summary-" + simpleDateFormat.format(currentDate.getTime()) + ".csv";

    final Survey survey = surveys.iterator().next();
    final StringWriter csvStringWriter = new StringWriter();
    CSVWriter writer = new CSVWriter(csvStringWriter, ';');

    writer.writeNext(new String[]{
            i18nResolver.getText("surveyplugin.survey.summary.header.question"),
            i18nResolver.getText("surveyplugin.vote.choices"),
            i18nResolver.getText("surveyplugin.vote.result"),
            i18nResolver.getText("surveyplugin.vote.voters"),
            i18nResolver.getText("surveyplugin.export.comments")
    });

    List<String> comments = new ArrayList<String>();
    for (Ballot ballot : survey.getBallots()) {
      for (Choice choice : ballot.getChoices()) {
        comments.clear();
        for (String voter : choice.getVoters()) {
          Comment comment = ballot.getCommentForUser(voter);
          if (comment != null) {
            comments.add(comment.getComment());
          }
        }
        String[] line = new String[]{ballot.getTitle(), choice.getDescription(), choice.getVoters().size() + " " + i18nResolver.getText("surveyplugin.survey.summary.votes") + ", " + ballot.getPercentageOfVoteForChoice(choice) + "%", StringUtils.join(choice.getVoters().toArray(), ","), StringUtils.join(comments.toArray(), ",")};
        writer.writeNext(line);
      }
    }

    try {
      writer.close();
    } catch (IOException e) {
      LOG.error("There was a problem while closing the stream", e);
    }

    final byte[] csvBytes = csvStringWriter.toString().getBytes("UTF-8");
    final Attachment addedAttachment = transactionTemplate.execute(new TransactionCallbackAddAttachment(pageManager, page, fileName, csvBytes));

    if (addedAttachment == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("There was a problem while trying to save the report as an Attachment").build();
    }
    return Response.ok(new CSVExportRepresentation(addedAttachment.getDownloadPath())).build();

  }

  @POST
  @Path("/{surveyTitle}/lock")
  @Produces(MediaType.APPLICATION_JSON)
  public Response setLocked(@PathParam("pageId") long pageId, @PathParam("surveyTitle") String inSurveyTitle) throws UnsupportedEncodingException {
    LOG.info("entered setLocked for pageId=" + pageId + " and surveyTitle=" + inSurveyTitle);

    final String surveyTitle = URLDecoder.decode(inSurveyTitle, "UTF-8");
    final Page page = pageManager.getPage(pageId);

    if (page == null) {
      return Response.status(Response.Status.NOT_FOUND.getStatusCode()).entity("Specified page with id: " + pageId + " was not found").build();
    }

    final LockRepresentation lockRepresentation = new LockRepresentation(false);

    try {
      page.setBodyAsString(xhtmlContent.updateMacroDefinitions(page.getBodyAsString(), new DefaultConversionContext(page.toPageContext()), new MacroDefinitionUpdater() {
        @Override
        public MacroDefinition update(MacroDefinition macroDefinition) {
          if (SurveyMacro.SURVEY_MACRO.equals(macroDefinition.getName())) {
            final Map<String, String> parameters = macroDefinition.getParameters();
            String currentTitle = SurveyUtils.getTitleInMacroParameters(parameters);
            LOG.info("surveyTitle for locking=" + surveyTitle + ", currentTitle to check is=" + currentTitle);
            if (surveyTitle.equalsIgnoreCase(currentTitle)) {
              //inverse the extracted state
              lockRepresentation.setLocked(!SurveyUtils.getBooleanFromString(parameters.get(VoteConfig.KEY_LOCKED), false));
              parameters.put(VoteConfig.KEY_LOCKED, String.valueOf(lockRepresentation.isLocked()));
              macroDefinition.setParameters(parameters);
            }
          }
          return macroDefinition;
        }
      }));
    } catch (XhtmlException e) {
      final String message = "There was a problem while parsing the Xhtml content: " + e.getMessage() + " for surveyTitle: " + surveyTitle;
      LOG.error(message, e);
      return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
    }
    //TODO: think of checking whether the user is in the proper permission list, e.g. managers ?!
    pageManager.saveContentEntity(page, new DefaultSaveContext(true, false, true));

    LOG.info("macroDefinitions have been updated. Returning: " + lockRepresentation.isLocked());
    return Response.ok(lockRepresentation).build();
  }

  @POST
  @Path("/{surveyTitle}/reset")
  @Produces(MediaType.APPLICATION_JSON)
  public Response resetVotes(@PathParam("pageId") long pageId, @PathParam("surveyTitle") String inSurveyTitle) throws UnsupportedEncodingException {
    final String surveyTitle = URLDecoder.decode(inSurveyTitle, "UTF-8");
    final Page page = pageManager.getPage(pageId);

    if (page == null) {
      return Response.status(Response.Status.NOT_FOUND.getStatusCode()).entity("Specified page with id: " + pageId + " was not found").build();
    }

    final List<Survey> surveys = new ArrayList<Survey>();
    try {
      xhtmlContent.handleMacroDefinitions(page.getBodyAsString(), new DefaultConversionContext(page.toPageContext()), new MacroDefinitionHandler() {
        @Override
        public void handle(MacroDefinition macroDefinition) {
          if (SurveyMacro.SURVEY_MACRO.equals(macroDefinition.getName())) {
            final Map<String, String> parameters = macroDefinition.getParameters();
            String currentTitle = SurveyUtils.getTitleInMacroParameters(parameters);
            if (surveyTitle.equalsIgnoreCase(currentTitle)) {
              final Survey survey = surveyManager.reconstructSurveyFromPlainTextMacroBody(macroDefinition.getBodyText(), page.getContentEntityObject(), macroDefinition.getParameters());
              survey.setTitle(surveyTitle);
              surveys.add(survey);
            }
          }
        }
      });
    } catch (XhtmlException e) {
      final String message = "There was a problem while parsing the Xhtml content: " + e.getMessage() + " for surveyTitle: " + surveyTitle;
      LOG.error(message, e);
      return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
    }

    if (surveys.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Could not find the specified survey macro with title " + surveyTitle + " on the specified page!").build();
    }

    //TODO: probably should add a proper permission check, e.g. is in the survey manager list

    final Survey survey = surveys.iterator().next();
    for (Ballot ballot : survey.getBallots()) {
      //todo: carry on
    }

    return Response.noContent().build();
  }
}
