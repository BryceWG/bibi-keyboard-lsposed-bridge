/*
 * Applies the user-selected display language to settings Contexts.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

final class BridgeLocaleHelper {
    private BridgeLocaleHelper() {
    }

    static Context wrap(Context base) {
        if (base == null) return null;
        String tag = BridgeLocalePrefs.read(base);
        if (BridgeLocalePrefs.isSystem(tag)) return base;
        Locale locale = Locale.forLanguageTag(tag);
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().isEmpty()) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        LocaleList localeList = new LocaleList(locale);
        config.setLocales(localeList);
        LocaleList.setDefault(localeList);
        Locale.setDefault(locale);
        return base.createConfigurationContext(config);
    }
}
