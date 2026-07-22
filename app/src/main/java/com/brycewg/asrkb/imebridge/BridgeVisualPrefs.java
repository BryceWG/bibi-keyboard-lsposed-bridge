/*
 * Module-owned preferences for capture-strip visuals and host routing.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

final class BridgeVisualPrefs {
    static final int MIN_WIDTH_DP = 120;
    static final int MAX_WIDTH_DP = 280;
    static final int DEFAULT_WIDTH_DP = 190;
    static final int MIN_HEIGHT_DP = 24;
    static final int MAX_HEIGHT_DP = 72;
    static final int DEFAULT_HEIGHT_DP = 32;
    private static final int BASE_BOTTOM_MARGIN_DP = 8;

    private static final String TAG = "BiBiImeBridge";
    private static final String PREF_NAME = "bridge_visual";
    static final String KEY_WIDTH_DP = "capture_width_dp";
    static final String KEY_HEIGHT_DP = "capture_height_dp";
    static final String KEY_HOST_TARGET = "host_target";
    static final String KEY_SHOW_RECORDING_AREA = "show_recording_area";

    private BridgeVisualPrefs() {
    }

    static VisualConfig defaults() {
        return new VisualConfig(
            DEFAULT_WIDTH_DP,
            DEFAULT_HEIGHT_DP,
            BridgeContract.HOST_TARGET_AUTO,
            true
        );
    }

    /** Capture strip should wait for the first onWindowShown prefs load. */
    static boolean shouldAttachCapture(boolean appliedConfigInitialized, boolean showRecordingArea) {
        return appliedConfigInitialized && showRecordingArea;
    }

    static VisualConfig readForSettings(Context context) {
        if (context == null) return defaults();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new VisualConfig(
            prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP),
            prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP),
            prefs.getString(KEY_HOST_TARGET, BridgeContract.HOST_TARGET_AUTO),
            prefs.getBoolean(KEY_SHOW_RECORDING_AREA, true)
        );
    }

    static void saveForSettings(Context context, VisualConfig config) {
        if (context == null || config == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_WIDTH_DP, clampWidthDp(config.widthDp))
            .putInt(KEY_HEIGHT_DP, clampHeightDp(config.heightDp))
            .putString(KEY_HOST_TARGET, BridgeContract.normalizeHostTarget(config.hostTarget))
            .putBoolean(KEY_SHOW_RECORDING_AREA, config.showRecordingArea)
            .apply();
    }

    static VisualConfig readForHook(Context context) {
        if (context == null) return defaults();
        try {
            android.database.Cursor cursor = context.getContentResolver().query(
                BridgeVisualPrefsProvider.CONTENT_URI,
                null,
                null,
                null,
                null
            );
            if (cursor == null) return defaults();
            try {
                VisualConfig config = BridgeVisualPrefsProvider.configFromCursor(cursor);
                if (config == null) return defaults();
                logHook("visual prefs loaded via ContentProvider" +
                    " showRecordingArea=" + config.showRecordingArea +
                    " size=" + config.widthDp + "x" + config.heightDp);
                return config;
            } finally {
                cursor.close();
            }
        } catch (Throwable t) {
            logHook("ContentProvider visual prefs failed: " + t);
            return defaults();
        }
    }

    private static void logHook(String message) {
        Log.w(TAG, message);
        try {
            Class<?> bridge = Class.forName("de.robv.android.xposed.XposedBridge");
            bridge.getMethod("log", String.class).invoke(null, TAG + ": " + message);
        } catch (Throwable ignored) {
            // Settings process has no XposedBridge.
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

    static final class VisualConfig {
        final int widthDp;
        final int heightDp;
        final String hostTarget;
        final boolean showRecordingArea;

        VisualConfig(int widthDp, int heightDp) {
            this(widthDp, heightDp, BridgeContract.HOST_TARGET_AUTO, true);
        }

        VisualConfig(int widthDp, int heightDp, String hostTarget, boolean showRecordingArea) {
            this.widthDp = clampWidthDp(widthDp);
            this.heightDp = clampHeightDp(heightDp);
            this.hostTarget = BridgeContract.normalizeHostTarget(hostTarget);
            this.showRecordingArea = showRecordingArea;
        }

        VisualConfig withSize(int widthDp, int heightDp) {
            return new VisualConfig(widthDp, heightDp, hostTarget, showRecordingArea);
        }

        VisualConfig withHostTarget(String hostTarget) {
            return new VisualConfig(widthDp, heightDp, hostTarget, showRecordingArea);
        }

        VisualConfig withShowRecordingArea(boolean showRecordingArea) {
            return new VisualConfig(widthDp, heightDp, hostTarget, showRecordingArea);
        }
    }
}
