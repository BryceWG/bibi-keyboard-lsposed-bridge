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
    static final int MIN_TRIGGER_DELAY_MS = 0;
    static final int MAX_TRIGGER_DELAY_MS = 1000;
    static final int DEFAULT_TRIGGER_DELAY_MS = 0;
    private static final int BASE_BOTTOM_MARGIN_DP = 8;

    private static final String TAG = "BiBiImeBridge";
    private static final String PREF_NAME = "bridge_visual";
    private static final String HOOK_CACHE_PREF_NAME = "bridge_visual_hook_cache";
    static final String KEY_WIDTH_DP = "capture_width_dp";
    static final String KEY_HEIGHT_DP = "capture_height_dp";
    static final String KEY_HOST_TARGET = "host_target";
    static final String KEY_SHOW_RECORDING_AREA = "show_recording_area";
    static final String KEY_SHOW_WAVEFORM_ONLY_WHILE_RECORDING = "show_waveform_only_while_recording";
    static final String KEY_TAP_TO_TOGGLE_RECORDING = "tap_to_toggle_recording";
    static final String KEY_TRIGGER_DELAY_MS = "trigger_delay_ms";
    static final String KEY_LONG_PRESS_SWITCH_IME = "long_press_switch_ime";
    static final String KEY_SWITCH_IME_TARGET_ID = "switch_ime_target_id";
    static final String KEY_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH = "hide_idle_waveform_in_ime_switch";

    private BridgeVisualPrefs() {
    }

    static VisualConfig defaults() {
        return new VisualConfig(
            DEFAULT_WIDTH_DP,
            DEFAULT_HEIGHT_DP,
            BridgeContract.HOST_TARGET_AUTO,
            true,
            false,
            false,
            DEFAULT_TRIGGER_DELAY_MS
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
            prefs.getBoolean(KEY_SHOW_RECORDING_AREA, true),
            prefs.getBoolean(KEY_SHOW_WAVEFORM_ONLY_WHILE_RECORDING, false),
            prefs.getBoolean(KEY_TAP_TO_TOGGLE_RECORDING, false),
            prefs.getInt(KEY_TRIGGER_DELAY_MS, DEFAULT_TRIGGER_DELAY_MS),
            prefs.getBoolean(KEY_LONG_PRESS_SWITCH_IME, false),
            prefs.getString(KEY_SWITCH_IME_TARGET_ID, ""),
            prefs.getBoolean(KEY_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH, false)
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
            .putBoolean(
                KEY_SHOW_WAVEFORM_ONLY_WHILE_RECORDING,
                config.showWaveformOnlyWhileRecording
            )
            .putBoolean(KEY_TAP_TO_TOGGLE_RECORDING, config.tapToToggleRecording)
            .putInt(KEY_TRIGGER_DELAY_MS, config.triggerDelayMs)
            .putBoolean(KEY_LONG_PRESS_SWITCH_IME, config.longPressSwitchIme)
            .putString(KEY_SWITCH_IME_TARGET_ID, config.switchImeTargetId)
            .putBoolean(KEY_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH, config.hideIdleWaveformInImeSwitch)
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
            if (cursor == null) return fallbackHookConfig(readHookCache(context));
            try {
                VisualConfig config = BridgeVisualPrefsProvider.configFromCursor(cursor);
                if (config == null) return fallbackHookConfig(readHookCache(context));
                saveHookCache(context, config);
                logHook("visual prefs loaded via ContentProvider" +
                    " showRecordingArea=" + config.showRecordingArea +
                    " size=" + config.widthDp + "x" + config.heightDp);
                return config;
            } finally {
                cursor.close();
            }
        } catch (Throwable t) {
            logHook("ContentProvider visual prefs failed: " + t);
            return fallbackHookConfig(readHookCache(context));
        }
    }

    static VisualConfig fallbackHookConfig(VisualConfig cached) {
        return cached != null ? cached : defaults();
    }

    private static VisualConfig readHookCache(Context context) {
        try {
            return readHookCache(context.getSharedPreferences(
                HOOK_CACHE_PREF_NAME,
                Context.MODE_PRIVATE
            ));
        } catch (Throwable t) {
            logHook("hook config cache read failed: " + t);
            return null;
        }
    }

    static VisualConfig readHookCache(SharedPreferences prefs) {
        if (!prefs.contains(KEY_HOST_TARGET)) return null;
        return new VisualConfig(
            prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP),
            prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP),
            prefs.getString(KEY_HOST_TARGET, BridgeContract.HOST_TARGET_AUTO),
            prefs.getBoolean(KEY_SHOW_RECORDING_AREA, true),
            prefs.getBoolean(KEY_SHOW_WAVEFORM_ONLY_WHILE_RECORDING, false),
            prefs.getBoolean(KEY_TAP_TO_TOGGLE_RECORDING, false),
            prefs.getInt(KEY_TRIGGER_DELAY_MS, DEFAULT_TRIGGER_DELAY_MS),
            prefs.getBoolean(KEY_LONG_PRESS_SWITCH_IME, false),
            prefs.getString(KEY_SWITCH_IME_TARGET_ID, ""),
            prefs.getBoolean(KEY_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH, false)
        );
    }

    private static void saveHookCache(Context context, VisualConfig config) {
        try {
            saveHookCache(
                context.getSharedPreferences(HOOK_CACHE_PREF_NAME, Context.MODE_PRIVATE),
                config
            );
        } catch (Throwable t) {
            logHook("hook config cache write failed: " + t);
        }
    }

    static void saveHookCache(SharedPreferences prefs, VisualConfig config) {
        prefs.edit()
            .putInt(KEY_WIDTH_DP, config.widthDp)
            .putInt(KEY_HEIGHT_DP, config.heightDp)
            .putString(KEY_HOST_TARGET, config.hostTarget)
            .putBoolean(KEY_SHOW_RECORDING_AREA, config.showRecordingArea)
            .putBoolean(
                KEY_SHOW_WAVEFORM_ONLY_WHILE_RECORDING,
                config.showWaveformOnlyWhileRecording
            )
            .putBoolean(KEY_TAP_TO_TOGGLE_RECORDING, config.tapToToggleRecording)
            .putInt(KEY_TRIGGER_DELAY_MS, config.triggerDelayMs)
            .putBoolean(KEY_LONG_PRESS_SWITCH_IME, config.longPressSwitchIme)
            .putString(KEY_SWITCH_IME_TARGET_ID, config.switchImeTargetId)
            .putBoolean(KEY_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH, config.hideIdleWaveformInImeSwitch)
            .apply();
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

    static int clampTriggerDelayMs(int value) {
        if (value < MIN_TRIGGER_DELAY_MS) return MIN_TRIGGER_DELAY_MS;
        if (value > MAX_TRIGGER_DELAY_MS) return MAX_TRIGGER_DELAY_MS;
        return value;
    }

    static int bottomMarginDp(VisualConfig config) {
        if (config == null) return BASE_BOTTOM_MARGIN_DP;
        int extra = Math.round((clampHeightDp(config.heightDp) - MIN_HEIGHT_DP) * 0.65f);
        return BASE_BOTTOM_MARGIN_DP + extra;
    }

    static String normalizeSwitchImeTargetId(String value) {
        return value == null ? "" : value.trim();
    }

    static final class VisualConfig {
        final int widthDp;
        final int heightDp;
        final String hostTarget;
        final boolean showRecordingArea;
        final boolean showWaveformOnlyWhileRecording;
        final boolean tapToToggleRecording;
        final int triggerDelayMs;
        final boolean longPressSwitchIme;
        final String switchImeTargetId;
        final boolean hideIdleWaveformInImeSwitch;

        VisualConfig(int widthDp, int heightDp) {
            this(widthDp, heightDp, BridgeContract.HOST_TARGET_AUTO, true, false, false,
                DEFAULT_TRIGGER_DELAY_MS, false, "", false);
        }

        VisualConfig(
            int widthDp,
            int heightDp,
            String hostTarget,
            boolean showRecordingArea,
            boolean showWaveformOnlyWhileRecording,
            boolean tapToToggleRecording,
            int triggerDelayMs
        ) {
            this(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                false,
                "",
                false
            );
        }

        VisualConfig(
            int widthDp,
            int heightDp,
            String hostTarget,
            boolean showRecordingArea,
            boolean showWaveformOnlyWhileRecording,
            boolean tapToToggleRecording
        ) {
            this(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                DEFAULT_TRIGGER_DELAY_MS,
                false,
                "",
                false
            );
        }

        VisualConfig(
            int widthDp,
            int heightDp,
            String hostTarget,
            boolean showRecordingArea,
            boolean showWaveformOnlyWhileRecording,
            boolean tapToToggleRecording,
            int triggerDelayMs,
            boolean longPressSwitchIme,
            String switchImeTargetId
        ) {
            this(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                false
            );
        }

        VisualConfig(
            int widthDp,
            int heightDp,
            String hostTarget,
            boolean showRecordingArea,
            boolean showWaveformOnlyWhileRecording,
            boolean tapToToggleRecording,
            int triggerDelayMs,
            boolean longPressSwitchIme,
            String switchImeTargetId,
            boolean hideIdleWaveformInImeSwitch
        ) {
            this.widthDp = clampWidthDp(widthDp);
            this.heightDp = clampHeightDp(heightDp);
            this.hostTarget = BridgeContract.normalizeHostTarget(hostTarget);
            this.showRecordingArea = showRecordingArea;
            this.showWaveformOnlyWhileRecording = showWaveformOnlyWhileRecording;
            this.tapToToggleRecording = tapToToggleRecording;
            this.triggerDelayMs = clampTriggerDelayMs(triggerDelayMs);
            this.longPressSwitchIme = longPressSwitchIme;
            this.switchImeTargetId = normalizeSwitchImeTargetId(switchImeTargetId);
            this.hideIdleWaveformInImeSwitch = hideIdleWaveformInImeSwitch;
        }

        boolean isImeSwitchMode() {
            return showRecordingArea && longPressSwitchIme;
        }

        boolean recordingGesturesEnabled() {
            return showRecordingArea && !longPressSwitchIme;
        }

        boolean hideIdleWaveform() {
            return isImeSwitchMode()
                ? hideIdleWaveformInImeSwitch
                : showWaveformOnlyWhileRecording;
        }

        VisualConfig withSize(int widthDp, int heightDp) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withHostTarget(String hostTarget) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withShowRecordingArea(boolean showRecordingArea) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withShowWaveformOnlyWhileRecording(boolean enabled) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                enabled,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withTapToToggleRecording(boolean enabled) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                enabled,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withTriggerDelayMs(int delayMs) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                delayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withLongPressSwitchIme(boolean enabled) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                enabled,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withSwitchImeTargetId(String switchImeTargetId) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }

        VisualConfig withHideIdleWaveformInImeSwitch(boolean enabled) {
            return copy(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                enabled
            );
        }

        private VisualConfig copy(
            int widthDp,
            int heightDp,
            String hostTarget,
            boolean showRecordingArea,
            boolean showWaveformOnlyWhileRecording,
            boolean tapToToggleRecording,
            int triggerDelayMs,
            boolean longPressSwitchIme,
            String switchImeTargetId,
            boolean hideIdleWaveformInImeSwitch
        ) {
            return new VisualConfig(
                widthDp,
                heightDp,
                hostTarget,
                showRecordingArea,
                showWaveformOnlyWhileRecording,
                tapToToggleRecording,
                triggerDelayMs,
                longPressSwitchIme,
                switchImeTargetId,
                hideIdleWaveformInImeSwitch
            );
        }
    }
}
