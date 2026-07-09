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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ImeBridgeHook implements IXposedHookLoadPackage {
    private static final String TAG = "BiBiImeBridge";
    private static final Map<InputMethodService, List<BridgeReceiver>> RECEIVERS = new WeakHashMap<>();
    private static final Map<InputMethodService, CaptureRuntime> CAPTURE_RUNTIMES = new WeakHashMap<>();

    private static WeakReference<InputMethodService> activeServiceRef = new WeakReference<>(null);
    private static EditorInfo activeEditorInfo;
    private static boolean imeWindowVisible;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;
        if (BridgeContract.PACKAGE_OPEN_SOURCE.equals(lpparam.packageName) ||
            BridgeContract.PACKAGE_PRO.equals(lpparam.packageName) ||
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
                    boolean restarting = param.args != null &&
                        param.args.length > 1 &&
                        Boolean.TRUE.equals(param.args[1]);
                    activeServiceRef = new WeakReference<>(service);
                    activeEditorInfo = param.args != null && param.args.length > 0
                        ? (EditorInfo) param.args[0]
                        : null;
                    imeWindowVisible = safeIsInputViewShown(service);
                    registerBridgeReceiver(service, packageName);
                    attachCaptureRuntime(service, packageName);
                    if (!restarting) resetBridgeReceiverPreview(service);
                }
            }
        );

        XposedHelpers.findAndHookMethod(InputMethodService.class, "onFinishInput", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                resetBridgeReceiverPreview(service);
                cancelCaptureRuntime(service, "finish input");
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
                attachCaptureRuntime(service, packageName);
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
                detachCaptureRuntime(service, "window hidden");
                sendImeWindowVisibility(service, packageName, false);
            }
        });

        XposedHelpers.findAndHookMethod(InputMethodService.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service == null) return;
                resetBridgeReceiverPreview(service);
                destroyCaptureRuntime(service);
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
        List<BridgeReceiver> receivers = RECEIVERS.get(service);
        if (receivers == null) {
            receivers = new ArrayList<>();
            RECEIVERS.put(service, receivers);
        }
        for (String permission : BridgeContract.PERMISSIONS) {
            if (hasReceiverForPermission(receivers, permission)) continue;
            if (!isTrustedBridgePermission(service, permission)) continue;
            BridgeReceiver receiver = new BridgeReceiver(packageName, permission);
            try {
                registerBridgeReceiverWithPermission(service, receiver, createBridgeIntentFilter(), permission);
                receivers.add(receiver);
                XposedBridge.log(TAG + ": receiver registered for " + packageName +
                    " with " + permission);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to register receiver for " + packageName +
                    " with " + permission + ": " + t);
            }
        }
        if (receivers.isEmpty()) RECEIVERS.remove(service);
    }

    private static boolean hasReceiverForPermission(List<BridgeReceiver> receivers, String permission) {
        for (BridgeReceiver receiver : receivers) {
            if (receiver.isRegisteredForPermission(permission)) return true;
        }
        return false;
    }

    private static boolean isTrustedBridgePermission(Context context, String permission) {
        String expectedOwner = BridgeContract.ownerPackageForPermission(permission);
        if (expectedOwner == null) return false;
        try {
            String actualOwner = context.getPackageManager()
                .getPermissionInfo(permission, 0)
                .packageName;
            boolean trusted = expectedOwner.equals(actualOwner);
            if (!trusted) {
                XposedBridge.log(TAG + ": skip bridge receiver for " + permission +
                    ", owner is " + actualOwner + ", expected " + expectedOwner);
            }
            return trusted;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": skip bridge receiver for " + permission +
                ", permission owner unavailable: " + t);
            return false;
        }
    }

    private static synchronized void attachCaptureRuntime(InputMethodService service, String packageName) {
        if (service == null) return;
        CaptureRuntime runtime = CAPTURE_RUNTIMES.get(service);
        if (runtime == null) {
            runtime = new CaptureRuntime(service, packageName);
            CAPTURE_RUNTIMES.put(service, runtime);
        }
        runtime.attachLater();
    }

    private static synchronized void cancelCaptureRuntime(InputMethodService service, String reason) {
        CaptureRuntime runtime = service == null ? null : CAPTURE_RUNTIMES.get(service);
        if (runtime != null) runtime.cancel(reason);
    }

    private static synchronized void detachCaptureRuntime(InputMethodService service, String reason) {
        CaptureRuntime runtime = service == null ? null : CAPTURE_RUNTIMES.get(service);
        if (runtime != null) runtime.detach(reason);
    }

    private static synchronized void destroyCaptureRuntime(InputMethodService service) {
        CaptureRuntime runtime = service == null ? null : CAPTURE_RUNTIMES.remove(service);
        if (runtime != null) runtime.destroy();
    }

    private static synchronized CaptureRuntime getCaptureRuntime(InputMethodService service) {
        return service == null ? null : CAPTURE_RUNTIMES.get(service);
    }

    private static IntentFilter createBridgeIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BridgeContract.ACTION_QUERY_STATUS);
        filter.addAction(BridgeContract.ACTION_INSERT_TEXT);
        filter.addAction(BridgeContract.ACTION_BEGIN_SESSION);
        filter.addAction(BridgeContract.ACTION_CANCEL_SESSION);
        filter.addAction(BridgeContract.ACTION_SET_COMPOSING_TEXT);
        filter.addAction(BridgeContract.ACTION_FINISH_COMPOSING_TEXT);
        filter.addAction(BridgeContract.ACTION_QUERY_INPUT_CONTEXT);
        return filter;
    }

    private static void registerBridgeReceiverWithPermission(
        InputMethodService service,
        BridgeReceiver receiver,
        IntentFilter filter,
        String permission
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            service.registerReceiver(
                receiver,
                filter,
                permission,
                null,
                Context.RECEIVER_EXPORTED
            );
        } else {
            service.registerReceiver(receiver, filter, permission, null);
        }
    }

    private static synchronized void unregisterBridgeReceiver(InputMethodService service) {
        List<BridgeReceiver> receivers = RECEIVERS.remove(service);
        if (receivers == null) return;
        for (BridgeReceiver receiver : receivers) {
            try {
                service.unregisterReceiver(receiver);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to unregister receiver: " + t);
            }
        }
    }

    private static synchronized void resetBridgeReceiverPreview(InputMethodService service) {
        List<BridgeReceiver> receivers = RECEIVERS.get(service);
        if (receivers == null) return;
        for (BridgeReceiver receiver : receivers) {
            receiver.resetBridgeSessionState();
        }
    }

    private static void sendImeWindowVisibility(Context context, String imePackageName, boolean visible) {
        if (context == null) return;
        for (String appPackageName : BridgeContract.MAIN_APP_PACKAGES) {
            try {
                Intent intent = new Intent(BridgeContract.ACTION_IME_WINDOW_VISIBILITY_CHANGED);
                intent.setPackage(appPackageName);
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                intent.putExtra(BridgeContract.EXTRA_PROTOCOL_VERSION, BridgeContract.PROTOCOL_VERSION);
                intent.putExtra(BridgeContract.EXTRA_TARGET_PACKAGE, imePackageName);
                intent.putExtra(BridgeContract.EXTRA_IME_WINDOW_VISIBLE, visible);
                context.sendBroadcast(intent, BridgeContract.permissionForAppPackage(appPackageName));
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to send IME visibility to " + appPackageName + ": " + t);
            }
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

    private static boolean isSensitiveEditor(EditorInfo editorInfo) {
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

    private static final class CaptureRuntime implements
        ImeWindowCaptureHost.Listener,
        BridgeCaptureCoordinator.CaptureEnvironment,
        BridgeCaptureCoordinator.StatusListener {

        private final WeakReference<InputMethodService> serviceRef;
        private final String packageName;
        private final ImeWindowCaptureHost host;
        private final BridgeCaptureCoordinator coordinator;
        private BridgeCaptureStatus hostStatus = BridgeCaptureStatus.unsupported("not attached");
        private BridgeCaptureStatus captureStatus = BridgeCaptureStatus.unsupported("not attached");

        CaptureRuntime(InputMethodService service, String packageName) {
            this.serviceRef = new WeakReference<>(service);
            this.packageName = packageName;
            this.host = new ImeWindowCaptureHost(this, packageName);
            this.coordinator = new BridgeCaptureCoordinator(
                this,
                new BridgePcmSessionClient(service),
                new PcmAudioRecorder(service),
                this
            );
        }

        void attachLater() {
            InputMethodService service = serviceRef.get();
            if (service != null) host.attachLater(service);
        }

        void cancel(String reason) {
            coordinator.cancelActiveCapture(reason);
            host.updateCaptureStatus(BridgeCaptureStatus.ready(reason));
        }

        void detach(String reason) {
            coordinator.cancelActiveCapture(reason);
            host.detach();
        }

        void destroy() {
            coordinator.destroy();
            host.detach();
        }

        boolean supportsPcmRecording() {
            boolean hostReady = hostStatus.supportsPcmRecording() ||
                (host.isAttached() && isTransientHostUnsupported(hostStatus));
            return host.isAttached() &&
                hasInputConnection() &&
                !isSensitiveField() &&
                hostReady;
        }

        BridgeCaptureStatus currentStatus() {
            if (captureStatus != null && captureStatus.state == BridgeCaptureStatus.State.FAILED) {
                return captureStatus;
            }
            return hostStatus != null ? hostStatus : captureStatus;
        }

        @Override
        public void onCaptureHoldStarted() {
            coordinator.startCapture();
        }

        @Override
        public void onCaptureHoldReleased() {
            coordinator.finishCapture();
        }

        @Override
        public void onCaptureHoldCancelled() {
            coordinator.cancelActiveCapture("gesture cancelled");
        }

        @Override
        public void onCaptureHostStatusChanged(BridgeCaptureStatus status) {
            hostStatus = status;
            if (status != null && status.state == BridgeCaptureStatus.State.READY) {
                coordinator.markReady(status.message);
            } else if (isTerminalHostUnsupported(status)) {
                coordinator.markUnsupported(status.message);
            }
        }

        private boolean isTerminalHostUnsupported(BridgeCaptureStatus status) {
            if (status == null || status.state != BridgeCaptureStatus.State.UNSUPPORTED) return false;
            String message = status.message == null ? "" : status.message;
            return message.startsWith("attach failed") ||
                "not attached".equals(message) ||
                "no input method service".equals(message) ||
                "unsupported ime window root".equals(message);
        }

        private boolean isTransientHostUnsupported(BridgeCaptureStatus status) {
            if (status == null || status.state != BridgeCaptureStatus.State.UNSUPPORTED) return false;
            return "ime window not ready".equals(status.message);
        }

        @Override
        public boolean hasInputConnection() {
            InputMethodService service = serviceRef.get();
            if (service == null) return false;
            try {
                return service.getCurrentInputConnection() != null;
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": capture getCurrentInputConnection failed for " +
                    packageName + ": " + t);
                return false;
            }
        }

        @Override
        public boolean isSensitiveField() {
            return isSensitiveEditor(activeEditorInfo);
        }

        @Override
        public void onCaptureStatusChanged(BridgeCaptureStatus status) {
            captureStatus = status;
            host.updateCaptureStatus(status);
        }
    }

    private static final class BridgeReceiver extends BroadcastReceiver {
        private final String packageName;
        private final String permission;
        private boolean composingPreviewActive;
        private String activeSessionId;
        private String currentOperation;
        private String lastOperation;
        private int lastResultCode;
        private String lastError;
        private String composingEditorPackageName;
        private int composingEditorFieldId;
        private int composingEditorInputType;

        BridgeReceiver(String packageName, String permission) {
            this.packageName = packageName;
            this.permission = permission;
        }

        boolean isRegisteredForPermission(String permission) {
            return this.permission.equals(permission);
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
            currentOperation = action;
            if (BridgeContract.ACTION_QUERY_STATUS.equals(action)) {
                handleQueryStatus();
            } else if (BridgeContract.ACTION_BEGIN_SESSION.equals(action)) {
                handleBeginSession(intent);
            } else if (BridgeContract.ACTION_CANCEL_SESSION.equals(action)) {
                handleCancelSession(intent);
            } else if (BridgeContract.ACTION_INSERT_TEXT.equals(action)) {
                handleInsertText(intent);
            } else if (BridgeContract.ACTION_SET_COMPOSING_TEXT.equals(action)) {
                handleSetComposingText(intent);
            } else if (BridgeContract.ACTION_FINISH_COMPOSING_TEXT.equals(action)) {
                handleFinishComposingText(intent);
            } else if (BridgeContract.ACTION_QUERY_INPUT_CONTEXT.equals(action)) {
                handleQueryInputContext(intent);
            } else {
                finish(BridgeContract.RESULT_BAD_REQUEST, "unknown action");
            }
        }

        private void handleQueryStatus() {
            InputMethodService service = activeServiceRef.get();
            InputConnection inputConnection = getInputConnection(service);
            Bundle extras = new Bundle();
            fillStatusExtras(extras, service, inputConnection);
            setResultExtras(extras);
            finish(BridgeContract.RESULT_OK, "ready");
        }

        private void handleBeginSession(Intent intent) {
            String sessionId = intent.getStringExtra(BridgeContract.EXTRA_SESSION_ID);
            if (sessionId == null || sessionId.length() == 0) {
                finish(BridgeContract.RESULT_BAD_REQUEST, "empty session id");
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

            clearComposingPreviewIfOwned(inputConnection);
            activeSessionId = sessionId;
            finish(BridgeContract.RESULT_OK, "session started");
        }

        private void handleCancelSession(Intent intent) {
            String sessionId = intent.getStringExtra(BridgeContract.EXTRA_SESSION_ID);
            if (!isSessionAccepted(sessionId)) {
                finish(BridgeContract.RESULT_SESSION_MISMATCH, "session mismatch");
                return;
            }

            InputMethodService service = activeServiceRef.get();
            InputConnection inputConnection = getInputConnection(service);
            boolean ok = inputConnection == null || clearComposingPreviewIfOwned(inputConnection);
            activeSessionId = null;
            if (ok) {
                finish(BridgeContract.RESULT_OK, "session cancelled");
            } else {
                finish(BridgeContract.RESULT_COMPOSING_FAILED, "cancel composing failed");
            }
        }

        private void handleInsertText(Intent intent) {
            String sessionId = intent.getStringExtra(BridgeContract.EXTRA_SESSION_ID);
            if (!isSessionAccepted(sessionId)) {
                finish(BridgeContract.RESULT_SESSION_MISMATCH, "session mismatch");
                return;
            }

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
                if (composingPreviewActive) {
                    if (isComposingPreviewForActiveEditor()) {
                        ok = inputConnection.setComposingText(value, cursorPosition) &&
                            inputConnection.finishComposingText();
                        if (ok) resetComposingPreviewState();
                    } else {
                        resetComposingPreviewState();
                        ok = inputConnection.commitText(value, cursorPosition);
                    }
                } else {
                    ok = inputConnection.commitText(value, cursorPosition);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": insert text failed: " + t);
                ok = false;
            }
            if (ok && sessionId != null && sessionId.length() > 0) {
                activeSessionId = null;
            }
            finish(ok ? BridgeContract.RESULT_OK : BridgeContract.RESULT_COMMIT_FAILED, ok ? "ok" : "commit failed");
        }

        private void handleSetComposingText(Intent intent) {
            String sessionId = intent.getStringExtra(BridgeContract.EXTRA_SESSION_ID);
            if (!isSessionAccepted(sessionId)) {
                finish(BridgeContract.RESULT_SESSION_MISMATCH, "session mismatch");
                return;
            }

            CharSequence value = intent.getCharSequenceExtra(BridgeContract.EXTRA_TEXT);
            if (value == null) value = "";
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
                ok = inputConnection.setComposingText(value, cursorPosition);
                if (ok) {
                    if (value.length() > 0) {
                        rememberComposingEditor(activeEditorInfo);
                    } else {
                        resetComposingPreviewState();
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": setComposingText failed: " + t);
                ok = false;
            }
            finish(
                ok ? BridgeContract.RESULT_OK : BridgeContract.RESULT_COMPOSING_FAILED,
                ok ? "ok" : "set composing failed"
            );
        }

        private void handleFinishComposingText(Intent intent) {
            String sessionId = intent.getStringExtra(BridgeContract.EXTRA_SESSION_ID);
            if (!isSessionAccepted(sessionId)) {
                finish(BridgeContract.RESULT_SESSION_MISMATCH, "session mismatch");
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

            boolean ok;
            try {
                ok = inputConnection.finishComposingText();
                if (ok) resetComposingPreviewState();
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": finishComposingText failed: " + t);
                ok = false;
            }
            if (ok && sessionId != null && sessionId.length() > 0) {
                activeSessionId = null;
            }
            finish(
                ok ? BridgeContract.RESULT_OK : BridgeContract.RESULT_COMPOSING_FAILED,
                ok ? "ok" : "finish composing failed"
            );
        }

        private void handleQueryInputContext(Intent intent) {
            if (activeEditorInfo == null) {
                finish(BridgeContract.RESULT_NO_INPUT_CONNECTION, "no active editor");
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

            int maxChars = intent.getIntExtra(BridgeContract.EXTRA_MAX_CONTEXT_CHARS, 1500);
            if (maxChars < 0) maxChars = 0;
            if (maxChars > 10000) maxChars = 10000;

            String before = "";
            String after = "";
            try {
                CharSequence beforeSeq = inputConnection.getTextBeforeCursor(maxChars, 0);
                CharSequence afterSeq = inputConnection.getTextAfterCursor(maxChars, 0);
                before = trimBeforeCursor(beforeSeq, maxChars);
                after = trimAfterCursor(afterSeq, maxChars);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": query input context failed: " + t);
                finish(BridgeContract.RESULT_COMMIT_FAILED, "query input context failed");
                return;
            }

            Bundle extras = getResultExtras(true);
            extras.putString(BridgeContract.EXTRA_BEFORE_CURSOR, before);
            extras.putString(BridgeContract.EXTRA_AFTER_CURSOR, after);
            setResultExtras(extras);
            finish(BridgeContract.RESULT_OK, "ok");
        }

        private String trimBeforeCursor(CharSequence value, int maxChars) {
            if (value == null || value.length() == 0 || maxChars <= 0) return "";
            String text = value.toString();
            if (text.length() <= maxChars) return text;
            return text.substring(text.length() - maxChars);
        }

        private String trimAfterCursor(CharSequence value, int maxChars) {
            if (value == null || value.length() == 0 || maxChars <= 0) return "";
            String text = value.toString();
            if (text.length() <= maxChars) return text;
            return text.substring(0, maxChars);
        }

        private boolean isSessionAccepted(String sessionId) {
            return BridgeSessionGate.accepts(activeSessionId, sessionId);
        }

        private boolean clearComposingPreviewIfOwned(InputConnection inputConnection) {
            if (inputConnection == null) return false;
            if (!composingPreviewActive) {
                resetComposingPreviewState();
                return true;
            }
            if (!isComposingPreviewForActiveEditor()) {
                resetComposingPreviewState();
                return true;
            }
            try {
                boolean ok = inputConnection.setComposingText("", 1) &&
                    inputConnection.finishComposingText();
                resetComposingPreviewState();
                return ok;
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": clear composing preview failed: " + t);
                resetComposingPreviewState();
                return false;
            }
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

        private void rememberComposingEditor(EditorInfo editorInfo) {
            composingPreviewActive = true;
            composingEditorPackageName = editorInfo != null ? editorInfo.packageName : null;
            composingEditorFieldId = editorInfo != null ? editorInfo.fieldId : 0;
            composingEditorInputType = editorInfo != null ? editorInfo.inputType : 0;
        }

        private boolean isComposingPreviewForActiveEditor() {
            if (!composingPreviewActive || activeEditorInfo == null) return false;
            if (composingEditorFieldId != activeEditorInfo.fieldId) return false;
            if (composingEditorInputType != activeEditorInfo.inputType) return false;
            if (composingEditorPackageName == null) return activeEditorInfo.packageName == null;
            return composingEditorPackageName.equals(activeEditorInfo.packageName);
        }

        void resetComposingPreviewState() {
            composingPreviewActive = false;
            composingEditorPackageName = null;
            composingEditorFieldId = 0;
            composingEditorInputType = 0;
        }

        void resetBridgeSessionState() {
            activeSessionId = null;
            resetComposingPreviewState();
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

        private void fillStatusExtras(Bundle extras, InputMethodService service, InputConnection inputConnection) {
            CaptureRuntime captureRuntime = getCaptureRuntime(service);
            extras.putString(BridgeContract.EXTRA_TARGET_PACKAGE, packageName);
            extras.putString(BridgeContract.EXTRA_MODULE_VERSION, BridgeContract.MODULE_VERSION);
            extras.putBoolean(BridgeContract.EXTRA_HAS_INPUT_CONNECTION, inputConnection != null);
            extras.putBoolean(BridgeContract.EXTRA_IS_SENSITIVE_FIELD, isSensitiveField(activeEditorInfo));
            extras.putBoolean(BridgeContract.EXTRA_IME_WINDOW_VISIBLE, isImeWindowVisible(service));
            extras.putBoolean(BridgeContract.EXTRA_SUPPORTS_INSERT_TEXT, true);
            extras.putBoolean(BridgeContract.EXTRA_SUPPORTS_COMPOSING_PREVIEW, true);
            extras.putBoolean(BridgeContract.EXTRA_SUPPORTS_FINISH_COMPOSING_TEXT, true);
            extras.putBoolean(BridgeContract.EXTRA_SUPPORTS_SESSIONS, true);
            extras.putBoolean(
                BridgeContract.EXTRA_SUPPORTS_PCM_RECORDING,
                captureRuntime != null && captureRuntime.supportsPcmRecording()
            );
            extras.putBoolean(BridgeContract.EXTRA_SUPPORTS_INPUT_CONTEXT, true);
            if (activeSessionId != null) {
                extras.putString(BridgeContract.EXTRA_ACTIVE_SESSION_ID, activeSessionId);
            }
            if (lastOperation != null) {
                extras.putString(BridgeContract.EXTRA_LAST_OPERATION, lastOperation);
                extras.putInt(BridgeContract.EXTRA_LAST_RESULT_CODE, lastResultCode);
            }
            if (lastError != null && lastError.length() > 0) {
                extras.putString(BridgeContract.EXTRA_LAST_ERROR, lastError);
            } else if (captureRuntime != null) {
                BridgeCaptureStatus captureStatus = captureRuntime.currentStatus();
                if (captureStatus != null &&
                    captureStatus.message != null &&
                    captureStatus.message.length() > 0 &&
                    captureStatus.state != BridgeCaptureStatus.State.READY) {
                    extras.putString(BridgeContract.EXTRA_LAST_ERROR, captureStatus.message);
                }
            }
        }

        private void finish(int code, String message) {
            Bundle extras = getResultExtras(true);
            InputMethodService service = activeServiceRef.get();
            fillStatusExtras(extras, service, getInputConnection(service));
            extras.putString(BridgeContract.EXTRA_MESSAGE, message);
            lastOperation = currentOperation;
            lastResultCode = code;
            lastError = code == BridgeContract.RESULT_OK ? "" : message;
            if (lastOperation != null) {
                extras.putString(BridgeContract.EXTRA_LAST_OPERATION, lastOperation);
                extras.putInt(BridgeContract.EXTRA_LAST_RESULT_CODE, lastResultCode);
            }
            if (lastError.length() > 0) {
                extras.putString(BridgeContract.EXTRA_LAST_ERROR, lastError);
            }
            setResultCode(code);
            setResultData(message);
            setResultExtras(extras);
        }
    }
}
