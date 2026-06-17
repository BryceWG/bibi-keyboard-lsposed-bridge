/*
 * Xposed entrypoint that bridges BiBi ASR text into a hooked input method.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ImeBridgeHook implements IXposedHookLoadPackage {
    private static final String TAG = "BiBiImeBridge";
    private static final Map<InputMethodService, BridgeReceiver> RECEIVERS = new WeakHashMap<>();

    private static WeakReference<InputMethodService> activeServiceRef = new WeakReference<>(null);
    private static EditorInfo activeEditorInfo;
    private static boolean imeWindowVisible;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;
        if ("com.brycewg.asrkb".equals(lpparam.packageName) ||
            "com.brycewg.asrkb.imebridge".equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedBridge.log(TAG + ": module " + BridgeContract.MODULE_VERSION +
                " loading for " + lpparam.packageName);
            hookInputMethodService(lpparam.packageName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to install hooks for " + lpparam.packageName + ": " + t);
        }
    }

    private static void hookInputMethodService(final String packageName) {
        XposedHelpers.findAndHookMethod(InputMethodService.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service == null) return;
                registerBridgeReceiver(service, packageName);
            }
        });

        XposedHelpers.findAndHookMethod(
            InputMethodService.class,
            "onStartInput",
            EditorInfo.class,
            boolean.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    InputMethodService service = asImeService(param.thisObject);
                    if (service == null) return;
                    activeServiceRef = new WeakReference<>(service);
                    activeEditorInfo = param.args != null && param.args.length > 0
                        ? (EditorInfo) param.args[0]
                        : null;
                    imeWindowVisible = safeIsInputViewShown(service);
                    registerBridgeReceiver(service, packageName);
                }
            }
        );

        XposedHelpers.findAndHookMethod(InputMethodService.class, "onFinishInput", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service != null && service == activeServiceRef.get()) {
                    activeEditorInfo = null;
                }
            }
        });

        XposedHelpers.findAndHookMethod(InputMethodService.class, "onWindowShown", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service == null) return;
                activeServiceRef = new WeakReference<>(service);
                imeWindowVisible = true;
                registerBridgeReceiver(service, packageName);
                sendImeWindowVisibility(service, packageName, true);
            }
        });

        XposedHelpers.findAndHookMethod(InputMethodService.class, "onWindowHidden", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service == null) return;
                if (service == activeServiceRef.get()) {
                    imeWindowVisible = false;
                }
                sendImeWindowVisibility(service, packageName, false);
            }
        });

        XposedHelpers.findAndHookMethod(InputMethodService.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service == null) return;
                unregisterBridgeReceiver(service);
                if (service == activeServiceRef.get()) {
                    activeServiceRef = new WeakReference<>(null);
                    activeEditorInfo = null;
                    imeWindowVisible = false;
                }
            }
        });
    }

    private static InputMethodService asImeService(Object value) {
        return value instanceof InputMethodService ? (InputMethodService) value : null;
    }

    private static synchronized void registerBridgeReceiver(InputMethodService service, String packageName) {
        if (RECEIVERS.containsKey(service)) return;
        BridgeReceiver receiver = new BridgeReceiver(packageName);
        IntentFilter filter = new IntentFilter();
        filter.addAction(BridgeContract.ACTION_QUERY_STATUS);
        filter.addAction(BridgeContract.ACTION_INSERT_TEXT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                service.registerReceiver(
                    receiver,
                    filter,
                    BridgeContract.PERMISSION,
                    null,
                    Context.RECEIVER_EXPORTED
                );
            } else {
                service.registerReceiver(receiver, filter, BridgeContract.PERMISSION, null);
            }
            RECEIVERS.put(service, receiver);
            XposedBridge.log(TAG + ": receiver registered for " + packageName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to register receiver for " + packageName + ": " + t);
        }
    }

    private static synchronized void unregisterBridgeReceiver(InputMethodService service) {
        BridgeReceiver receiver = RECEIVERS.remove(service);
        if (receiver == null) return;
        try {
            service.unregisterReceiver(receiver);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to unregister receiver: " + t);
        }
    }

    private static void sendImeWindowVisibility(Context context, String packageName, boolean visible) {
        if (context == null) return;
        try {
            Intent intent = new Intent(BridgeContract.ACTION_IME_WINDOW_VISIBILITY_CHANGED);
            intent.setPackage(BridgeContract.MAIN_APP_PACKAGE);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(BridgeContract.EXTRA_PROTOCOL_VERSION, BridgeContract.PROTOCOL_VERSION);
            intent.putExtra(BridgeContract.EXTRA_TARGET_PACKAGE, packageName);
            intent.putExtra(BridgeContract.EXTRA_IME_WINDOW_VISIBLE, visible);
            context.sendBroadcast(intent, BridgeContract.PERMISSION);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to send IME visibility: " + t);
        }
    }

    private static boolean isImeWindowVisible(InputMethodService service) {
        return imeWindowVisible || safeIsInputViewShown(service);
    }

    private static boolean safeIsInputViewShown(InputMethodService service) {
        if (service == null) return false;
        try {
            return service.isInputViewShown();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": isInputViewShown failed: " + t);
            return false;
        }
    }

    private static final class BridgeReceiver extends BroadcastReceiver {
        private final String packageName;

        BridgeReceiver(String packageName) {
            this.packageName = packageName;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                finish(BridgeContract.RESULT_BAD_REQUEST, "empty intent");
                return;
            }
            int protocol = intent.getIntExtra(BridgeContract.EXTRA_PROTOCOL_VERSION, 0);
            if (protocol != BridgeContract.PROTOCOL_VERSION) {
                finish(BridgeContract.RESULT_PROTOCOL_MISMATCH, "protocol mismatch");
                return;
            }

            String action = intent.getAction();
            if (BridgeContract.ACTION_QUERY_STATUS.equals(action)) {
                handleQueryStatus();
            } else if (BridgeContract.ACTION_INSERT_TEXT.equals(action)) {
                handleInsertText(intent);
            } else {
                finish(BridgeContract.RESULT_BAD_REQUEST, "unknown action");
            }
        }

        private void handleQueryStatus() {
            InputMethodService service = activeServiceRef.get();
            InputConnection inputConnection = getInputConnection(service);
            Bundle extras = new Bundle();
            extras.putString(BridgeContract.EXTRA_TARGET_PACKAGE, packageName);
            extras.putBoolean(BridgeContract.EXTRA_HAS_INPUT_CONNECTION, inputConnection != null);
            extras.putBoolean(BridgeContract.EXTRA_IS_SENSITIVE_FIELD, isSensitiveField(activeEditorInfo));
            extras.putBoolean(BridgeContract.EXTRA_IME_WINDOW_VISIBLE, isImeWindowVisible(service));
            setResultExtras(extras);
            finish(BridgeContract.RESULT_OK, "ready");
        }

        private void handleInsertText(Intent intent) {
            CharSequence value = intent.getCharSequenceExtra(BridgeContract.EXTRA_TEXT);
            if (value == null || value.length() == 0) {
                finish(BridgeContract.RESULT_BAD_REQUEST, "empty text");
                return;
            }
            if (isSensitiveField(activeEditorInfo)) {
                finish(BridgeContract.RESULT_SENSITIVE_FIELD, "sensitive field");
                return;
            }

            InputMethodService service = activeServiceRef.get();
            if (service == null) {
                finish(BridgeContract.RESULT_NO_ACTIVE_IME, "no active ime");
                return;
            }

            InputConnection inputConnection = getInputConnection(service);
            if (inputConnection == null) {
                finish(BridgeContract.RESULT_NO_INPUT_CONNECTION, "no input connection");
                return;
            }

            int cursorPosition = intent.getIntExtra(BridgeContract.EXTRA_CURSOR_POSITION, 1);
            boolean ok;
            try {
                ok = inputConnection.commitText(value, cursorPosition);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": commitText failed: " + t);
                ok = false;
            }
            finish(ok ? BridgeContract.RESULT_OK : BridgeContract.RESULT_COMMIT_FAILED, ok ? "ok" : "commit failed");
        }

        private InputConnection getInputConnection(InputMethodService service) {
            if (service == null) return null;
            try {
                return service.getCurrentInputConnection();
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": getCurrentInputConnection failed: " + t);
                return null;
            }
        }

        private boolean isSensitiveField(EditorInfo editorInfo) {
            if (editorInfo == null) return false;
            int variation = editorInfo.inputType & InputType.TYPE_MASK_VARIATION;
            int inputClass = editorInfo.inputType & InputType.TYPE_MASK_CLASS;
            if (inputClass == InputType.TYPE_CLASS_TEXT) {
                return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
            }
            if (inputClass == InputType.TYPE_CLASS_NUMBER) {
                return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
            }
            return false;
        }

        private void finish(int code, String message) {
            Bundle extras = getResultExtras(true);
            extras.putString(BridgeContract.EXTRA_MESSAGE, message);
            setResultCode(code);
            setResultData(message);
            setResultExtras(extras);
        }
    }
}
