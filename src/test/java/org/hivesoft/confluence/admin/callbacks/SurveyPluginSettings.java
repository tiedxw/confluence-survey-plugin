package org.hivesoft.confluence.admin.callbacks;

import com.atlassian.sal.api.pluginsettings.PluginSettings;

import java.util.HashMap;
import java.util.Map;

public class SurveyPluginSettings implements PluginSettings {

    Map<String, Object> settings = new HashMap<String, Object>();

    @Override
    public Object get(String s) {
        return settings.get(s);
    }

    @Override
    public Object put(String s, Object o) {
        return settings.put(s, o);
    }

    @Override
    public Object remove(String s) {
        return settings.remove(s);
    }
}
