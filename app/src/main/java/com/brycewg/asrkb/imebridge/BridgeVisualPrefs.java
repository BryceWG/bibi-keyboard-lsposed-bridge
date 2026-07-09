/*
 * Module-owned visual preferences for the IME bridge capture strip.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.lang.reflect.Method;

final class BridgeVisualPrefs {
    static final int MIN_WIDTH_DP = 120;
    static final int MAX_WIDTH_DP = 280;
    static final int DEFAULT_WIDTH_DP = 190;
    static final int MIN_HEIGHT_DP = 24;
    static final int MAX_HEIGHT_DP = 72;
    static final int DEFAULT_HEIGHT_DP = 32;
    private static final int BASE_BOTTOM_MARGIN_DP = 8;

    private static final String MODULE_PACKAGE = "com.brycewg.asrkb.imebridge";
    private static final String PREF_NAME = "bridge_visual";
    private static final String KEY_WIDTH_DP = "capture_width_dp";
    private static final String KEY_HEIGHT_DP = "capture_height_dp";

    private BridgeVisualPrefs() {
    }

    static VisualConfig defaults() {
        return new VisualConfig(DEFAULT_WIDTH_DP, DEFAULT_HEIGHT_DP);
    }

    static VisualConfig readForSettings(Context context) {
        if (context == null) return defaults();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new VisualConfig(
            prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP),
            prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP)
        );
    }

    static void saveForSettings(Context context, VisualConfig config) {
        if (context == null || config == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_WIDTH_DP, clampWidthDp(config.widthDp));
        editor.putInt(KEY_HEIGHT_DP, clampHeightDp(config.heightDp));
        editor.commit();
        makePrefsReadableBestEffort(context);
    }

    static VisualConfig readForHook() {
        try {
            Class<?> prefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
            Object prefs = prefsClass
                .getConstructor(String.class, String.class)
                .newInstance(MODULE_PACKAGE, PREF_NAME);
            invokeNoArgIfPresent(prefsClass, prefs, "makeWorldReadable");
            invokeNoArgIfPresent(prefsClass, prefs, "reload");
            Method getInt = prefsClass.getMethod("getInt", String.class, int.class);
            int widthDp = (Integer) getInt.invoke(prefs, KEY_WIDTH_DP, DEFAULT_WIDTH_DP);
            int heightDp = (Integer) getInt.invoke(prefs, KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP);
            return new VisualConfig(widthDp, heightDp);
        } catch (Throwable ignored) {
            return defaults();
        }
    }

    static int clampWidthDp(int value) {
        if (value < MIN_WIDTH_DP) return MIN_WIDTH_DP;
        if (value > MAX_WIDTH_DP) return MAX_WIDTH_DP;
        return value;
    }

    static int clampHeightDp(int value) {
        if (value < MIN_HEIGHT_DP) return MIN_HEIGHT_DP;
        if (value > MAX_HEIGHT_DP) return MAX_HEIGHT_DP;
        return value;
    }

    static int bottomMarginDp(VisualConfig config) {
        if (config == null) return BASE_BOTTOM_MARGIN_DP;
        int extra = Math.round((clampHeightDp(config.heightDp) - MIN_HEIGHT_DP) * 0.65f);
        return BASE_BOTTOM_MARGIN_DP + extra;
    }

    private static void invokeNoArgIfPresent(Class<?> cls, Object target, String methodName) {
        try {
            cls.getMethod(methodName).invoke(target);
        } catch (Throwable ignored) {
            // Optional across Xposed implementations.
        }
    }

    private static void makePrefsReadableBestEffort(Context context) {
        try {
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, PREF_NAME + ".xml");
            prefsDir.setExecutable(true, false);
            prefsDir.setReadable(true, false);
            prefsFile.setReadable(true, false);
        } catch (Throwable ignored) {
            // LSPatch/modern Android may reject this; hook side falls back to defaults.
        }
    }

    static final class VisualConfig {
        final int widthDp;
        final int heightDp;

        VisualConfig(int widthDp, int heightDp) {
            this.widthDp = clampWidthDp(widthDp);
            this.heightDp = clampHeightDp(heightDp);
        }
    }
}
