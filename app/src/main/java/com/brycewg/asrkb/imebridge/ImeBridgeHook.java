/*
 * Xposed entrypoint that bridges BiBi ASR text into a hooked input method.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
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
    private static final HookInstallGate HOOK_INSTALL_GATE = new HookInstallGate();
    private static final Map<InputMethodService, List<BridgeReceiver>> RECEIVERS = new WeakHashMap<>();
    private static final Map<InputMethodService, CaptureRuntime> CAPTURE_RUNTIMES = new WeakHashMap<>();
    private static final ClipboardObserveRegistry CLIPBOARD_OBSERVERS = new ClipboardObserveRegistry();
    private static final Map<InputMethodService, BridgeClipboardSyncDispatcher> CLIPBOARD_SYNC_DISPATCHERS =
        new WeakHashMap<>();

    private static WeakReference<InputMethodService> activeServiceRef = new WeakReference<>(null);
    private static EditorInfo activeEditorInfo;
    private static boolean imeWindowVisible;
    private static boolean clipboardListenerRegistered;
    private static boolean suppressOwnClipboardChange;
    private static ClipboardManager.OnPrimaryClipChangedListener clipboardChangeListener;
    private static BridgeVisualPrefs.VisualConfig appliedVisualConfig = BridgeVisualPrefs.defaults();
    private static boolean appliedConfigInitialized;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;
        if (BridgeContract.PACKAGE_OPEN_SOURCE.equals(lpparam.packageName) ||
            BridgeContract.PACKAGE_PRO.equals(lpparam.packageName) ||
            "com.brycewg.asrkb.imebridge".equals(lpparam.packageName)) {
            return;
        }

        if (!HOOK_INSTALL_GATE.tryInstall(lpparam.packageName)) {
            XposedBridge.log(TAG + ": skip duplicate hook install for " + lpparam.packageName +
                ", already installed for " + HOOK_INSTALL_GATE.getInstalledPackage());
            return;
        }
        try {
            XposedBridge.log(TAG + ": module " + BridgeContract.MODULE_VERSION +
                " loading for " + lpparam.packageName);
            hookInputMethodService();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to install hooks for " + lpparam.packageName + ": " + t);
        }
    }

    private static void hookInputMethodService() {
        XposedHelpers.findAndHookMethod(InputMethodService.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service == null) return;
                activeServiceRef = new WeakReference<>(service);
                registerBridgeReceiver(service, resolveHostPackage(service));
                ensureClipboardObserver(service);
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
                    String hostPackage = resolveHostPackage(service);
                    registerBridgeReceiver(service, hostPackage);
                    if (BridgeVisualPrefs.shouldAttachCapture(
                        appliedConfigInitialized,
                        appliedVisualConfig.showRecordingArea
                    )) {
                        attachCaptureRuntime(service, hostPackage, appliedVisualConfig);
                    } else {
                        destroyCaptureRuntime(service);
                    }
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
                String hostPackage = resolveHostPackage(service);
                applyRuntimeConfigOnWindowShown(service, hostPackage);
                sendImeWindowVisibility(service, hostPackage, true);
                showClipboardSyncRuntime(service);
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
                sendImeWindowVisibility(service, resolveHostPackage(service), false);
                hideClipboardSyncRuntime(service);
            }
        });

        XposedHelpers.findAndHookMethod(InputMethodService.class, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                InputMethodService service = asImeService(param.thisObject);
                if (service == null) return;
                resetBridgeReceiverPreview(service);
                destroyCaptureRuntime(service);
                deactivateClipboardSyncRuntime(service);
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

    private static String resolveHostPackage(InputMethodService service) {
        String servicePackage = null;
        try {
            if (service != null) servicePackage = service.getPackageName();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": resolve host package from service failed: " + t);
        }
        return HOOK_INSTALL_GATE.resolveHostPackage(servicePackage);
    }

    private static synchronized void showClipboardSyncRuntime(InputMethodService service) {
        if (service == null) return;
        String targetImePackage = null;
        try {
            targetImePackage = service.getPackageName();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": resolve target ime package failed: " + t);
        }
        if (targetImePackage == null || targetImePackage.length() == 0) return;
        try {
            BridgeClipboardSyncDispatcher dispatcher = CLIPBOARD_SYNC_DISPATCHERS.get(service);
            if (dispatcher == null) {
                dispatcher = new BridgeClipboardSyncDispatcher(new BridgeClipboardSyncClient(service));
                CLIPBOARD_SYNC_DISPATCHERS.put(service, dispatcher);
            }
            // 目标包名是被 Hook 的第三方 IME；宿主 Pro/OSS 由 BridgeHostRouting 决定。
            // 不传 SyncClipboard 服务器地址或凭证。
            dispatcher.windowShown(targetImePackage);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clipboard sync runtime show error: " + t);
        }
    }

    private static synchronized void transitionClipboardSyncHosts(InputMethodService service) {
        if (service == null) return;
        BridgeClipboardSyncDispatcher dispatcher = CLIPBOARD_SYNC_DISPATCHERS.get(service);
        if (dispatcher == null) return;
        try {
            // Barrier close only; onWindowShown will reconnect via showClipboardSyncRuntime.
            dispatcher.transitionHosts(null);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clipboard sync host transition error: " + t);
        }
    }

    private static synchronized void applyRuntimeConfigOnWindowShown(
        InputMethodService service,
        String hostPackage
    ) {
        BridgeVisualPrefs.VisualConfig config = BridgeVisualPrefs.readForHook(service);
        boolean hostChanged = appliedConfigInitialized &&
            !config.hostTarget.equals(appliedVisualConfig.hostTarget);
        BridgeHostRouting.apply(config.hostTarget);
        CLIPBOARD_OBSERVERS.retainAllowedHosts();
        syncBridgeReceivers(service, hostPackage);

        if (hostChanged) {
            cancelCaptureRuntime(service, "host target changed");
            transitionClipboardSyncHosts(service);
        }

        appliedVisualConfig = config;
        appliedConfigInitialized = true;
        XposedBridge.log(TAG + ": applied visual prefs host=" + config.hostTarget +
            " showRecordingArea=" + config.showRecordingArea +
            " showWaveformOnlyWhileRecording=" + config.showWaveformOnlyWhileRecording +
            " tapToToggleRecording=" + config.tapToToggleRecording +
            " size=" + config.widthDp + "x" + config.heightDp);

        if (!config.showRecordingArea) {
            destroyCaptureRuntime(service);
        } else {
            attachCaptureRuntime(service, hostPackage, config);
        }
    }

    private static synchronized void hideClipboardSyncRuntime(InputMethodService service) {
        if (service == null) return;
        BridgeClipboardSyncDispatcher dispatcher = CLIPBOARD_SYNC_DISPATCHERS.get(service);
        if (dispatcher == null) return;
        try {
            dispatcher.windowHidden();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clipboard sync window hidden notify error: " + t);
        }
    }

    private static synchronized void deactivateClipboardSyncRuntime(InputMethodService service) {
        if (service == null) return;
        BridgeClipboardSyncDispatcher dispatcher = CLIPBOARD_SYNC_DISPATCHERS.remove(service);
        if (dispatcher == null) return;
        try {
            dispatcher.destroy();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clipboard sync runtime deactivate error: " + t);
        }
    }

    private static synchronized void registerBridgeReceiver(InputMethodService service, String packageName) {
        syncBridgeReceivers(service, packageName);
    }

    private static synchronized void syncBridgeReceivers(InputMethodService service, String packageName) {
        if (service == null) return;
        List<BridgeReceiver> receivers = RECEIVERS.get(service);
        if (receivers == null) {
            receivers = new ArrayList<>();
            RECEIVERS.put(service, receivers);
        }
        String[] desiredPermissions = BridgeHostRouting.permissions();
        // Unregister receivers that are no longer allowed for the current host mode.
        for (int i = receivers.size() - 1; i >= 0; i--) {
            BridgeReceiver receiver = receivers.get(i);
            if (isDesiredPermission(desiredPermissions, receiver.permission())) continue;
            try {
                service.unregisterReceiver(receiver);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to unregister stale receiver: " + t);
            }
            receivers.remove(i);
        }
        for (String permission : desiredPermissions) {
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

    private static boolean isDesiredPermission(String[] desiredPermissions, String permission) {
        if (permission == null) return false;
        for (String desired : desiredPermissions) {
            if (permission.equals(desired)) return true;
        }
        return false;
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

    private static synchronized void attachCaptureRuntime(
        InputMethodService service,
        String packageName,
        BridgeVisualPrefs.VisualConfig visualConfig
    ) {
        if (service == null) return;
        CaptureRuntime runtime = CAPTURE_RUNTIMES.get(service);
        if (runtime == null) {
            runtime = new CaptureRuntime(service, packageName);
            CAPTURE_RUNTIMES.put(service, runtime);
        }
        runtime.setVisualConfig(visualConfig);
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
        filter.addAction(BridgeContract.ACTION_SET_CLIPBOARD_TEXT);
        filter.addAction(BridgeContract.ACTION_GET_CLIPBOARD_TEXT);
        filter.addAction(BridgeContract.ACTION_START_CLIPBOARD_OBSERVE);
        filter.addAction(BridgeContract.ACTION_STOP_CLIPBOARD_OBSERVE);
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
        detachClipboardObserver(service);
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
        for (String appPackageName : BridgeHostRouting.packages()) {
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

    private static ClipboardManager clipboardManager(Context context) {
        if (context == null) return null;
        try {
            return (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": clipboard manager unavailable: " + t);
            return null;
        }
    }

    private static boolean writeClipboardText(Context context, CharSequence value) {
        ClipboardManager clipboard = clipboardManager(context);
        if (clipboard == null || value == null || value.length() == 0) return false;
        suppressOwnClipboardChange = true;
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("SyncClipboard", value));
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setPrimaryClip failed: " + t);
            suppressOwnClipboardChange = false;
            return false;
        } finally {
            suppressOwnClipboardChange = false;
        }
    }

    private static String readClipboardText(Context context) {
        ClipboardManager clipboard = clipboardManager(context);
        if (clipboard == null) return null;
        ClipData clip;
        try {
            clip = clipboard.getPrimaryClip();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getPrimaryClip failed: " + t);
            return null;
        }
        if (clip == null || clip.getItemCount() <= 0) return null;
        try {
            CharSequence text = clip.getItemAt(0).getText();
            if (text == null || text.length() == 0) return null;
            return text.toString();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": read clipboard text failed: " + t);
            return null;
        }
    }

    private static boolean isClipboardSensitive(Context context) {
        ClipboardManager clipboard = clipboardManager(context);
        if (clipboard == null) return false;
        ClipData clip;
        try {
            clip = clipboard.getPrimaryClip();
        } catch (Throwable t) {
            return false;
        }
        if (clip == null) return false;
        ClipDescription description = clip.getDescription();
        if (description == null) return false;
        try {
            PersistableBundle extras = description.getExtras();
            if (extras == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false);
            }
            return extras.getBoolean("android.content.extra.IS_SENSITIVE", false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized void ensureClipboardObserver(Context context) {
        if (!CLIPBOARD_OBSERVERS.hasSubscribers()) return;
        if (clipboardListenerRegistered) return;
        ClipboardManager clipboard = clipboardManager(context);
        if (clipboard == null) return;
        if (clipboardChangeListener == null) {
            clipboardChangeListener = () -> {
                if (suppressOwnClipboardChange) {
                    suppressOwnClipboardChange = false;
                    return;
                }
                if (!CLIPBOARD_OBSERVERS.hasSubscribers()) return;
                InputMethodService service = activeServiceRef.get();
                Context ctx = service != null ? service : context;
                boolean sensitive = isClipboardSensitive(ctx);
                String text = sensitive ? "" : readClipboardText(ctx);
                if (!sensitive && (text == null || text.length() == 0)) return;
                String imePackage = service != null ? service.getPackageName() : null;
                sendClipboardTextChanged(ctx, imePackage, text, sensitive);
            };
        }
        try {
            clipboard.addPrimaryClipChangedListener(clipboardChangeListener);
            clipboardListenerRegistered = true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addPrimaryClipChangedListener failed: " + t);
        }
    }

    private static synchronized void detachClipboardObserver(Context context) {
        if (!clipboardListenerRegistered) return;
        ClipboardManager clipboard = clipboardManager(context);
        if (clipboard != null && clipboardChangeListener != null) {
            try {
                clipboard.removePrimaryClipChangedListener(clipboardChangeListener);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": removePrimaryClipChangedListener failed: " + t);
            }
        }
        clipboardListenerRegistered = false;
    }

    private static void sendClipboardTextChanged(
        Context context,
        String imePackageName,
        String text,
        boolean sensitive
    ) {
        if (context == null || (!sensitive && text == null)) return;
        for (ClipboardObserveRegistry.Subscription subscription : CLIPBOARD_OBSERVERS.snapshot()) {
            String appPackageName = subscription.appPackageName;
            try {
                Intent intent = new Intent(BridgeContract.ACTION_CLIPBOARD_TEXT_CHANGED);
                intent.setPackage(appPackageName);
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                intent.putExtra(BridgeContract.EXTRA_PROTOCOL_VERSION, BridgeContract.PROTOCOL_VERSION);
                intent.putExtra(BridgeContract.EXTRA_TARGET_PACKAGE, imePackageName);
                if (!sensitive) intent.putExtra(BridgeContract.EXTRA_TEXT, text);
                intent.putExtra(
                    BridgeContract.EXTRA_CLIPBOARD_TEXT_CHARS,
                    sensitive ? 0 : text.length()
                );
                intent.putExtra(BridgeContract.EXTRA_IS_CLIPBOARD_SENSITIVE, sensitive);
                intent.putExtra(
                    BridgeContract.EXTRA_CLIPBOARD_SUBSCRIPTION_TOKEN,
                    subscription.token
                );
                context.sendBroadcast(intent, BridgeContract.permissionForAppPackage(appPackageName));
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to send clipboard change to " + appPackageName + ": " + t);
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

        void setVisualConfig(BridgeVisualPrefs.VisualConfig visualConfig) {
            host.setVisualConfig(visualConfig);
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

        String permission() {
            return permission;
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
            } else if (BridgeContract.ACTION_SET_CLIPBOARD_TEXT.equals(action)) {
                handleSetClipboardText(intent);
            } else if (BridgeContract.ACTION_GET_CLIPBOARD_TEXT.equals(action)) {
                handleGetClipboardText();
            } else if (BridgeContract.ACTION_START_CLIPBOARD_OBSERVE.equals(action)) {
                handleStartClipboardObserve(intent);
            } else if (BridgeContract.ACTION_STOP_CLIPBOARD_OBSERVE.equals(action)) {
                handleStopClipboardObserve();
            } else {
                finish(BridgeContract.RESULT_BAD_REQUEST, "unknown action");
            }
        }

        private void handleSetClipboardText(Intent intent) {
            CharSequence value = intent.getCharSequenceExtra(BridgeContract.EXTRA_TEXT);
            if (value == null || value.length() == 0) {
                finish(BridgeContract.RESULT_BAD_REQUEST, "empty text");
                return;
            }
            InputMethodService service = activeServiceRef.get();
            if (service == null) {
                finish(BridgeContract.RESULT_NO_ACTIVE_IME, "no active ime");
                return;
            }
            boolean ok = writeClipboardText(service, value);
            finish(
                ok ? BridgeContract.RESULT_OK : BridgeContract.RESULT_CLIPBOARD_FAILED,
                ok ? "ok" : "clipboard write failed"
            );
        }

        private void handleGetClipboardText() {
            InputMethodService service = activeServiceRef.get();
            if (service == null) {
                finish(BridgeContract.RESULT_NO_ACTIVE_IME, "no active ime");
                return;
            }
            String text = readClipboardText(service);
            if (text == null || text.length() == 0) {
                finish(BridgeContract.RESULT_CLIPBOARD_FAILED, "empty clipboard");
                return;
            }
            Bundle extras = getResultExtras(true);
            extras.putString(BridgeContract.EXTRA_TEXT, text);
            extras.putInt(BridgeContract.EXTRA_CLIPBOARD_TEXT_CHARS, text.length());
            extras.putBoolean(
                BridgeContract.EXTRA_IS_CLIPBOARD_SENSITIVE,
                isClipboardSensitive(service)
            );
            setResultExtras(extras);
            finish(BridgeContract.RESULT_OK, "ok");
        }

        private void handleStartClipboardObserve(Intent intent) {
            InputMethodService service = activeServiceRef.get();
            if (service == null) {
                finish(BridgeContract.RESULT_NO_ACTIVE_IME, "no active ime");
                return;
            }
            String appPackageName = BridgeContract.ownerPackageForPermission(permission);
            String token = intent.getStringExtra(BridgeContract.EXTRA_CLIPBOARD_SUBSCRIPTION_TOKEN);
            if (!CLIPBOARD_OBSERVERS.subscribe(appPackageName, token)) {
                finish(BridgeContract.RESULT_BAD_REQUEST, "invalid clipboard subscription");
                return;
            }
            ensureClipboardObserver(service);
            finish(
                clipboardListenerRegistered
                    ? BridgeContract.RESULT_OK
                    : BridgeContract.RESULT_CLIPBOARD_FAILED,
                clipboardListenerRegistered ? "observing" : "observe failed"
            );
        }

        private void handleStopClipboardObserve() {
            String appPackageName = BridgeContract.ownerPackageForPermission(permission);
            CLIPBOARD_OBSERVERS.unsubscribe(appPackageName);
            InputMethodService service = activeServiceRef.get();
            if (!CLIPBOARD_OBSERVERS.hasSubscribers()) {
                detachClipboardObserver(service);
            }
            finish(BridgeContract.RESULT_OK, "stopped");
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
            extras.putBoolean(BridgeContract.EXTRA_SUPPORTS_CLIPBOARD, true);
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
