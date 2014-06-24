package org.hivesoft.confluence.macros.utils;

import com.atlassian.user.User;
import org.hivesoft.confluence.macros.vote.VoteConfig;
import org.hivesoft.confluence.macros.vote.VoterStyle;

public class VoterRenderer {

  private static final String TEMPLATE_VOTER_LINK = "<a href=\"%s/display/%s\" class=\"url fn confluence-userlink\" data-username=\"%s\">%s</a>";

  // TODO for csv export
  public String render(String contextPath, User voter, VoteConfig config) {
    switch (config.getVisibleVotersWiki()) {
      case LINKED_LOGIN:
        return String.format(TEMPLATE_VOTER_LINK, contextPath, voter.getName(), voter.getName(), voter.getName());
      case LINKED_FULL:
        return String.format(TEMPLATE_VOTER_LINK, contextPath, voter.getName(), voter.getName(), voter.getFullName());
      case PLAIN_FULL:
        return voter.getFullName();
      case PLAIN_LOGIN:
      default:
        return voter.getName();
    }
  }
}
