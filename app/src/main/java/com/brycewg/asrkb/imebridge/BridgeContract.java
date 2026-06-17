/*
 * Shared wire constants for the IME bridge module.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class BridgeContract {
    static final int PROTOCOL_VERSION = 1;
    static final String MODULE_VERSION = "0.1.1";

    static final String MAIN_APP_PACKAGE = "com.brycewg.asrkb";
    static final String PERMISSION = "com.brycewg.asrkb.permission.IME_BRIDGE";
    static final String ACTION_QUERY_STATUS = "com.brycewg.asrkb.imebridge.action.QUERY_STATUS";
    static final String ACTION_INSERT_TEXT = "com.brycewg.asrkb.imebridge.action.INSERT_TEXT";
    static final String ACTION_IME_WINDOW_VISIBILITY_CHANGED =
        "com.brycewg.asrkb.imebridge.action.IME_WINDOW_VISIBILITY_CHANGED";

    static final String EXTRA_PROTOCOL_VERSION = "protocol_version";
    static final String EXTRA_REQUEST_ID = "request_id";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_CURSOR_POSITION = "cursor_position";
    static final String EXTRA_TARGET_PACKAGE = "target_package";
    static final String EXTRA_HAS_INPUT_CONNECTION = "has_input_connection";
    static final String EXTRA_IS_SENSITIVE_FIELD = "is_sensitive_field";
    static final String EXTRA_IME_WINDOW_VISIBLE = "ime_window_visible";
    static final String EXTRA_MESSAGE = "message";

    static final int RESULT_OK = 1;
    static final int RESULT_NO_RECEIVER = 0;
    static final int RESULT_PROTOCOL_MISMATCH = -2;
    static final int RESULT_NO_ACTIVE_IME = -3;
    static final int RESULT_NO_INPUT_CONNECTION = -4;
    static final int RESULT_SENSITIVE_FIELD = -5;
    static final int RESULT_COMMIT_FAILED = -6;
    static final int RESULT_BAD_REQUEST = -7;

    private BridgeContract() {
    }
}
