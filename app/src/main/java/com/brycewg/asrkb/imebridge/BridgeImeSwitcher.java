/*
 * Switches the hooked IME to a user-selected enabled input method.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

final class BridgeImeSwitcher {
    static final int RESULT_OK = 1;
    static final int RESULT_EMPTY_TARGET = -1;
    static final int RESULT_ALREADY_CURRENT = -2;
    static final int RESULT_FAILED = -3;

    private static final String TAG = "BiBiImeBridge";

    static final class Option {
        final String id;
        final String label;

        Option(String id, String label) {
            this.id = id == null ? "" : id;
            this.label = label == null || label.trim().isEmpty() ? this.id : label.trim();
        }
    }

    private BridgeImeSwitcher() {
    }

    static int evaluate(String targetId, String currentId) {
        String trimmed = BridgeVisualPrefs.normalizeSwitchImeTargetId(targetId);
        if (trimmed.isEmpty()) return RESULT_EMPTY_TARGET;
        if (trimmed.equals(currentId)) return RESULT_ALREADY_CURRENT;
        return RESULT_OK;
    }

    static int warningMessageResFor(int result) {
        if (result == RESULT_EMPTY_TARGET) return R.string.bridge_toast_ime_not_selected;
        if (result == RESULT_FAILED) return R.string.bridge_toast_ime_switch_failed;
        return 0;
    }

    static List<Option> enabledOptions(Context context) {
        if (context == null) return Collections.emptyList();
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm == null) return Collections.emptyList();
        List<InputMethodInfo> enabled;
        try {
            enabled = imm.getEnabledInputMethodList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
        if (enabled == null || enabled.isEmpty()) return Collections.emptyList();
        List<Option> options = new ArrayList<>(enabled.size());
        for (InputMethodInfo info : enabled) {
            if (info == null || info.getId() == null || info.getId().isEmpty()) continue;
            CharSequence label;
            try {
                label = info.loadLabel(context.getPackageManager());
            } catch (Throwable t) {
                label = null;
            }
            options.add(new Option(
                info.getId(),
                label == null ? info.getId() : label.toString()
            ));
        }
        return options;
    }

    static String currentInputMethodId(Context context) {
        if (context == null) return "";
        try {
            String id = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
            );
            return id == null ? "" : id;
        } catch (Throwable t) {
            return "";
        }
    }

    static void switchTo(InputMethodService service, String targetId) {
        if (service == null) return;
        int result = evaluate(targetId, currentInputMethodId(service));
        if (result != RESULT_OK) {
            int messageRes = warningMessageResFor(result);
            if (messageRes != 0) BridgeUserNotifier.show(service, messageRes);
            return;
        }
        try {
            service.switchInputMethod(BridgeVisualPrefs.normalizeSwitchImeTargetId(targetId));
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": switchInputMethod failed: " + t);
            BridgeUserNotifier.show(service, R.string.bridge_toast_ime_switch_failed);
        }
    }
}
