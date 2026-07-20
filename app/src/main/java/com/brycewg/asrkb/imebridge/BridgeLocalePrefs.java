/*
 * Persists the settings-app display language for the bridge module.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.content.SharedPreferences;

final class BridgeLocalePrefs {
    static final String TAG_SYSTEM = "system";
    static final String TAG_EN = "en";
    static final String TAG_ZH_CN = "zh-CN";
    static final String TAG_ZH_TW = "zh-TW";

    private static final String PREF_NAME = "bridge_locale";
    private static final String KEY_LANGUAGE = "language_tag";

    private BridgeLocalePrefs() {
    }

    static String[] supportedTags() {
        return new String[]{TAG_SYSTEM, TAG_EN, TAG_ZH_CN, TAG_ZH_TW};
    }

    static String read(Context context) {
        if (context == null) return TAG_SYSTEM;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return normalize(prefs.getString(KEY_LANGUAGE, TAG_SYSTEM));
    }

    static void save(Context context, String languageTag) {
        if (context == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_LANGUAGE, normalize(languageTag));
        editor.commit();
    }

    static String normalize(String languageTag) {
        if (languageTag == null || languageTag.trim().isEmpty()) return TAG_SYSTEM;
        String tag = languageTag.trim();
        if (TAG_SYSTEM.equalsIgnoreCase(tag) || "auto".equalsIgnoreCase(tag)) {
            return TAG_SYSTEM;
        }
        if ("en".equalsIgnoreCase(tag) || "en-US".equalsIgnoreCase(tag) || "en-GB".equalsIgnoreCase(tag)) {
            return TAG_EN;
        }
        if ("zh-CN".equalsIgnoreCase(tag) ||
            "zh-Hans".equalsIgnoreCase(tag) ||
            "zh-Hans-CN".equalsIgnoreCase(tag) ||
            "zh_CN".equalsIgnoreCase(tag)) {
            return TAG_ZH_CN;
        }
        if ("zh-TW".equalsIgnoreCase(tag) ||
            "zh-Hant".equalsIgnoreCase(tag) ||
            "zh-Hant-TW".equalsIgnoreCase(tag) ||
            "zh_TW".equalsIgnoreCase(tag)) {
            return TAG_ZH_TW;
        }
        if ("zh".equalsIgnoreCase(tag)) {
            return TAG_ZH_CN;
        }
        return TAG_SYSTEM;
    }

    static boolean isSystem(String languageTag) {
        return TAG_SYSTEM.equals(normalize(languageTag));
    }
}
