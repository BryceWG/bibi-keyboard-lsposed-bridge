/*
 * Shared wire constants for the IME bridge module.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class BridgeContract {
    static final int PROTOCOL_VERSION = 1;
    static final String MODULE_VERSION = "0.2.5";

    static final String PACKAGE_OPEN_SOURCE = "com.brycewg.asrkb";
    static final String PACKAGE_PRO = "com.brycewg.asrkb.pro";
    static final String[] MAIN_APP_PACKAGES = {
        PACKAGE_PRO,
        PACKAGE_OPEN_SOURCE
    };
    static final String PERMISSION_OPEN_SOURCE = "com.brycewg.asrkb.permission.IME_BRIDGE";
    static final String PERMISSION_PRO = "com.brycewg.asrkb.pro.permission.IME_BRIDGE";
    static final String[] PERMISSIONS = {
        PERMISSION_OPEN_SOURCE,
        PERMISSION_PRO
    };
    static final String ACTION_QUERY_STATUS = "com.brycewg.asrkb.imebridge.action.QUERY_STATUS";
    static final String ACTION_INSERT_TEXT = "com.brycewg.asrkb.imebridge.action.INSERT_TEXT";
    static final String ACTION_BEGIN_SESSION = "com.brycewg.asrkb.imebridge.action.BEGIN_SESSION";
    static final String ACTION_CANCEL_SESSION = "com.brycewg.asrkb.imebridge.action.CANCEL_SESSION";
    static final String ACTION_SET_COMPOSING_TEXT =
        "com.brycewg.asrkb.imebridge.action.SET_COMPOSING_TEXT";
    static final String ACTION_FINISH_COMPOSING_TEXT =
        "com.brycewg.asrkb.imebridge.action.FINISH_COMPOSING_TEXT";
    static final String ACTION_QUERY_INPUT_CONTEXT =
        "com.brycewg.asrkb.imebridge.action.QUERY_INPUT_CONTEXT";
    static final String ACTION_IME_WINDOW_VISIBILITY_CHANGED =
        "com.brycewg.asrkb.imebridge.action.IME_WINDOW_VISIBILITY_CHANGED";
    static final String ACTION_SET_CLIPBOARD_TEXT =
        "com.brycewg.asrkb.imebridge.action.SET_CLIPBOARD_TEXT";
    static final String ACTION_GET_CLIPBOARD_TEXT =
        "com.brycewg.asrkb.imebridge.action.GET_CLIPBOARD_TEXT";
    static final String ACTION_START_CLIPBOARD_OBSERVE =
        "com.brycewg.asrkb.imebridge.action.START_CLIPBOARD_OBSERVE";
    static final String ACTION_STOP_CLIPBOARD_OBSERVE =
        "com.brycewg.asrkb.imebridge.action.STOP_CLIPBOARD_OBSERVE";
    static final String ACTION_CLIPBOARD_TEXT_CHANGED =
        "com.brycewg.asrkb.imebridge.action.CLIPBOARD_TEXT_CHANGED";
    static final String PCM_ACTION_BIND_SERVICE =
        "com.brycewg.asrkb.imebridge.action.BIND_PCM_SESSION_SERVICE";
    static final String PCM_DESCRIPTOR =
        "com.brycewg.asrkb.imebridge.ImeBridgePcmSessionService";
    static final String CLIPBOARD_SYNC_ACTION_BIND_SERVICE =
        "com.brycewg.asrkb.imebridge.action.BIND_CLIPBOARD_SYNC_RUNTIME";
    static final String CLIPBOARD_SYNC_DESCRIPTOR =
        "com.brycewg.asrkb.imebridge.ImeBridgeClipboardSyncService";

    static final String EXTRA_PROTOCOL_VERSION = "protocol_version";
    static final String EXTRA_REQUEST_ID = "request_id";
    static final String EXTRA_SESSION_ID = "session_id";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_CURSOR_POSITION = "cursor_position";
    static final String EXTRA_MAX_CONTEXT_CHARS = "max_context_chars";
    static final String EXTRA_BEFORE_CURSOR = "before_cursor";
    static final String EXTRA_AFTER_CURSOR = "after_cursor";
    static final String EXTRA_TARGET_PACKAGE = "target_package";
    static final String EXTRA_HAS_INPUT_CONNECTION = "has_input_connection";
    static final String EXTRA_IS_SENSITIVE_FIELD = "is_sensitive_field";
    static final String EXTRA_IME_WINDOW_VISIBLE = "ime_window_visible";
    static final String EXTRA_MODULE_VERSION = "module_version";
    static final String EXTRA_SUPPORTS_INSERT_TEXT = "supports_insert_text";
    static final String EXTRA_SUPPORTS_COMPOSING_PREVIEW = "supports_composing_preview";
    static final String EXTRA_SUPPORTS_FINISH_COMPOSING_TEXT = "supports_finish_composing_text";
    static final String EXTRA_SUPPORTS_SESSIONS = "supports_sessions";
    static final String EXTRA_SUPPORTS_PCM_RECORDING = "supports_pcm_recording";
    static final String EXTRA_SUPPORTS_INPUT_CONTEXT = "supports_input_context";
    static final String EXTRA_SUPPORTS_CLIPBOARD = "supports_clipboard";
    static final String EXTRA_IS_CLIPBOARD_SENSITIVE = "is_clipboard_sensitive";
    static final String EXTRA_CLIPBOARD_TEXT_CHARS = "clipboard_text_chars";
    static final String EXTRA_CLIPBOARD_SUBSCRIPTION_TOKEN = "clipboard_subscription_token";
    static final String EXTRA_ACTIVE_SESSION_ID = "active_session_id";
    static final String EXTRA_LAST_OPERATION = "last_operation";
    static final String EXTRA_LAST_RESULT_CODE = "last_result_code";
    static final String EXTRA_LAST_ERROR = "last_error";
    static final String EXTRA_MESSAGE = "message";

    static final int RESULT_OK = 1;
    static final int RESULT_NO_RECEIVER = 0;
    static final int RESULT_PROTOCOL_MISMATCH = -2;
    static final int RESULT_NO_ACTIVE_IME = -3;
    static final int RESULT_NO_INPUT_CONNECTION = -4;
    static final int RESULT_SENSITIVE_FIELD = -5;
    static final int RESULT_COMMIT_FAILED = -6;
    static final int RESULT_BAD_REQUEST = -7;
    static final int RESULT_COMPOSING_FAILED = -8;
    static final int RESULT_SESSION_MISMATCH = -9;
    static final int RESULT_CLIPBOARD_FAILED = -10;

    static final int PCM_TRANSACTION_BEGIN = android.os.IBinder.FIRST_CALL_TRANSACTION + 0;
    static final int PCM_TRANSACTION_WRITE_FRAME = android.os.IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int PCM_TRANSACTION_FINISH = android.os.IBinder.FIRST_CALL_TRANSACTION + 2;
    static final int PCM_TRANSACTION_CANCEL = android.os.IBinder.FIRST_CALL_TRANSACTION + 3;

    static final int CLIPBOARD_SYNC_TRANSACTION_ACTIVATE =
        android.os.IBinder.FIRST_CALL_TRANSACTION + 0;
    static final int CLIPBOARD_SYNC_TRANSACTION_DEACTIVATE =
        android.os.IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int CLIPBOARD_SYNC_TRANSACTION_WINDOW_HIDDEN =
        android.os.IBinder.FIRST_CALL_TRANSACTION + 2;

    static final int PCM_RESULT_OK = 1;
    static final int PCM_RESULT_FEATURE_DISABLED = -1;
    static final int PCM_RESULT_PACKAGE_MISMATCH = -2;
    static final int PCM_RESULT_BRIDGE_UNAVAILABLE = -3;
    static final int PCM_RESULT_NO_INPUT_CONNECTION = -4;
    static final int PCM_RESULT_SENSITIVE_FIELD = -5;
    static final int PCM_RESULT_BAD_REQUEST = -6;
    static final int PCM_RESULT_BUSY = -7;
    static final int PCM_RESULT_STALE_SESSION = -8;
    static final int PCM_RESULT_UNSUPPORTED = -9;

    static String permissionForAppPackage(String appPackageName) {
        if (PACKAGE_PRO.equals(appPackageName)) return PERMISSION_PRO;
        return PERMISSION_OPEN_SOURCE;
    }

    static String ownerPackageForPermission(String permission) {
        if (PERMISSION_OPEN_SOURCE.equals(permission)) return PACKAGE_OPEN_SOURCE;
        if (PERMISSION_PRO.equals(permission)) return PACKAGE_PRO;
        return null;
    }

    static String pcmMessageForCode(int code) {
        switch (code) {
            case PCM_RESULT_OK:
                return "ok";
            case PCM_RESULT_FEATURE_DISABLED:
                return "feature disabled";
            case PCM_RESULT_PACKAGE_MISMATCH:
                return "package mismatch";
            case PCM_RESULT_BRIDGE_UNAVAILABLE:
                return "bridge unavailable";
            case PCM_RESULT_NO_INPUT_CONNECTION:
                return "no input connection";
            case PCM_RESULT_SENSITIVE_FIELD:
                return "sensitive field";
            case PCM_RESULT_BAD_REQUEST:
                return "bad request";
            case PCM_RESULT_BUSY:
                return "busy";
            case PCM_RESULT_STALE_SESSION:
                return "stale session";
            case PCM_RESULT_UNSUPPORTED:
                return "unsupported";
            default:
                return "unknown: " + code;
        }
    }

    private BridgeContract() {
    }
}
