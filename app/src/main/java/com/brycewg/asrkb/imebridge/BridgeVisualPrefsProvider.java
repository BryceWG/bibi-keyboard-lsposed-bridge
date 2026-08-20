/*
 * Cross-process visual prefs for hooked IME processes.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Hooked IME processes often cannot read the module SharedPreferences file under
 * modern SELinux. Expose the same settings via ContentProvider instead.
 */
public final class BridgeVisualPrefsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.brycewg.asrkb.imebridge.visualprefs";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/config");

    public static final String COLUMN_WIDTH_DP = BridgeVisualPrefs.KEY_WIDTH_DP;
    public static final String COLUMN_HEIGHT_DP = BridgeVisualPrefs.KEY_HEIGHT_DP;
    public static final String COLUMN_HOST_TARGET = BridgeVisualPrefs.KEY_HOST_TARGET;
    public static final String COLUMN_SHOW_RECORDING_AREA = BridgeVisualPrefs.KEY_SHOW_RECORDING_AREA;
    public static final String COLUMN_SHOW_WAVEFORM_ONLY_WHILE_RECORDING =
        BridgeVisualPrefs.KEY_SHOW_WAVEFORM_ONLY_WHILE_RECORDING;
    public static final String COLUMN_TAP_TO_TOGGLE_RECORDING =
        BridgeVisualPrefs.KEY_TAP_TO_TOGGLE_RECORDING;
    public static final String COLUMN_TRIGGER_DELAY_MS = BridgeVisualPrefs.KEY_TRIGGER_DELAY_MS;
    public static final String COLUMN_LONG_PRESS_SWITCH_IME =
        BridgeVisualPrefs.KEY_LONG_PRESS_SWITCH_IME;
    public static final String COLUMN_SWITCH_IME_TARGET_ID =
        BridgeVisualPrefs.KEY_SWITCH_IME_TARGET_ID;
    public static final String COLUMN_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH =
        BridgeVisualPrefs.KEY_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH;
    public static final String COLUMN_MODULE_VERSION = "module_version";

    private static final int CODE_CONFIG = 1;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "config", CODE_CONFIG);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        if (MATCHER.match(uri) != CODE_CONFIG) return null;
        BridgeVisualPrefs.VisualConfig config = BridgeVisualPrefs.readForSettings(getContext());
        MatrixCursor cursor = new MatrixCursor(new String[] {
            COLUMN_WIDTH_DP,
            COLUMN_HEIGHT_DP,
            COLUMN_HOST_TARGET,
            COLUMN_SHOW_RECORDING_AREA,
            COLUMN_SHOW_WAVEFORM_ONLY_WHILE_RECORDING,
            COLUMN_TAP_TO_TOGGLE_RECORDING,
            COLUMN_TRIGGER_DELAY_MS,
            COLUMN_LONG_PRESS_SWITCH_IME,
            COLUMN_SWITCH_IME_TARGET_ID,
            COLUMN_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH,
            COLUMN_MODULE_VERSION
        });
        cursor.addRow(new Object[] {
            config.widthDp,
            config.heightDp,
            config.hostTarget,
            config.showRecordingArea ? 1 : 0,
            config.showWaveformOnlyWhileRecording ? 1 : 0,
            config.tapToToggleRecording ? 1 : 0,
            config.triggerDelayMs,
            config.longPressSwitchIme ? 1 : 0,
            config.switchImeTargetId,
            config.hideIdleWaveformInImeSwitch ? 1 : 0,
            BridgeContract.MODULE_VERSION
        });
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        if (MATCHER.match(uri) != CODE_CONFIG) return null;
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".config";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    static BridgeVisualPrefs.VisualConfig configFromCursor(Cursor cursor) {
        if (cursor == null || !cursor.moveToFirst()) return null;
        int width = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_WIDTH_DP));
        int height = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HEIGHT_DP));
        String host = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HOST_TARGET));
        boolean show = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SHOW_RECORDING_AREA)) != 0;
        boolean recordingOnly = cursor.getInt(
            cursor.getColumnIndexOrThrow(COLUMN_SHOW_WAVEFORM_ONLY_WHILE_RECORDING)
        ) != 0;
        boolean tapToToggle = cursor.getInt(
            cursor.getColumnIndexOrThrow(COLUMN_TAP_TO_TOGGLE_RECORDING)
        ) != 0;
        int triggerDelayMs = cursor.getInt(
            cursor.getColumnIndexOrThrow(COLUMN_TRIGGER_DELAY_MS)
        );
        boolean longPressSwitchIme = optionalBoolean(cursor, COLUMN_LONG_PRESS_SWITCH_IME);
        String switchImeTargetId = optionalString(cursor, COLUMN_SWITCH_IME_TARGET_ID);
        boolean hideIdleWaveformInImeSwitch =
            optionalBoolean(cursor, COLUMN_HIDE_IDLE_WAVEFORM_IN_IME_SWITCH);
        return new BridgeVisualPrefs.VisualConfig(
            width,
            height,
            host,
            show,
            recordingOnly,
            tapToToggle,
            triggerDelayMs,
            longPressSwitchIme,
            switchImeTargetId,
            hideIdleWaveformInImeSwitch
        );
    }

    private static boolean optionalBoolean(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && cursor.getInt(index) != 0;
    }

    private static String optionalString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0) return "";
        String value = cursor.getString(index);
        return value == null ? "" : value;
    }
}
