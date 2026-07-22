/*
 * Attaches the capture strip through the standard InputMethodService window.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.app.Dialog;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import de.robv.android.xposed.XposedBridge;

final class ImeWindowCaptureHost {
    private static final String TAG = "BiBiImeBridge";

    interface Listener extends BottomCaptureStripView.Listener {
        void onCaptureHostStatusChanged(BridgeCaptureStatus status);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final String targetPackage;
    private BottomCaptureStripView stripView;
    private FrameLayout attachedRoot;
    private BridgeCaptureStatus status = BridgeCaptureStatus.unsupported("not attached");
    private int attachRetries;
    private int lastStripHeightPx;
    private BridgeVisualPrefs.VisualConfig visualConfig = BridgeVisualPrefs.defaults();

    ImeWindowCaptureHost(Listener listener, String targetPackage) {
        this.listener = listener;
        this.targetPackage = targetPackage;
    }

    void setVisualConfig(BridgeVisualPrefs.VisualConfig config) {
        if (config != null) visualConfig = config;
    }

    void attachLater(final InputMethodService service) {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                attachNow(service);
            }
        });
    }

    void detach() {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                cancelPendingAttachWork();
                detachNow();
            }
        });
    }

    void updateCaptureStatus(final BridgeCaptureStatus captureStatus) {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                if (stripView != null) stripView.updateStatus(captureStatus);
            }
        });
    }

    private void runOnMain(Runnable action) {
        if (action == null) return;
        if (Looper.myLooper() == mainHandler.getLooper()) {
            action.run();
            return;
        }
        mainHandler.post(action);
    }

    private void cancelPendingAttachWork() {
        mainHandler.removeCallbacksAndMessages(null);
        attachRetries = 0;
    }

    BridgeCaptureStatus getStatus() {
        return status;
    }

    boolean isAttached() {
        return stripView != null && stripView.getParent() != null;
    }

    private void attachNow(InputMethodService service) {
        if (service == null) {
            setStatus(BridgeCaptureStatus.unsupported("no input method service"));
            return;
        }
        FrameLayout root = resolveDecorRoot(service);
        if (root == null) {
            detachNow();
            setStatus(BridgeCaptureStatus.unsupported("unsupported ime window root"));
            return;
        }
        if (!root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0) {
            if (attachRetries < 3) {
                attachRetries++;
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        attachNow(service);
                    }
                }, 50L);
            }
            setStatus(BridgeCaptureStatus.unsupported("ime window not ready"));
            return;
        }
        attachRetries = 0;
        BridgeVisualPrefs.VisualConfig config = visualConfig != null
            ? visualConfig
            : BridgeVisualPrefs.defaults();
        float density = service.getResources().getDisplayMetrics().density;
        if (stripView != null && stripView.getParent() == root) {
            applyVisualLayout(stripView, root, config, density);
            setStatus(BridgeCaptureStatus.ready("attached"));
            return;
        }
        detachNow();
        stripView = new BottomCaptureStripView(service, listener);
        applyVisualLayout(stripView, root, config, density);
        try {
            root.addView(stripView, buildLayoutParams(root, config, density));
            attachedRoot = root;
            lastStripHeightPx = Math.round(BridgeVisualPrefs.clampHeightDp(config.heightDp) * density);
            setStatus(BridgeCaptureStatus.ready("attached"));
        } catch (Throwable t) {
            stripView = null;
            attachedRoot = null;
            lastStripHeightPx = 0;
            setStatus(BridgeCaptureStatus.unsupported("attach failed: " + t.getClass().getSimpleName()));
        }
    }

    private void applyVisualLayout(
        BottomCaptureStripView strip,
        FrameLayout root,
        BridgeVisualPrefs.VisualConfig visualConfig,
        float density
    ) {
        if (strip == null || root == null || visualConfig == null) return;
        strip.setShowWaveformOnlyWhileRecording(
            visualConfig.showWaveformOnlyWhileRecording
        );
        FrameLayout.LayoutParams params = buildLayoutParams(root, visualConfig, density);
        lastStripHeightPx = params.height;
        ViewGroup.LayoutParams existing = strip.getLayoutParams();
        if (existing instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams current = (FrameLayout.LayoutParams) existing;
            if (current.width == params.width &&
                current.height == params.height &&
                current.gravity == params.gravity &&
                current.bottomMargin == params.bottomMargin) {
                return;
            }
        }
        strip.setLayoutParams(params);
    }

    private FrameLayout.LayoutParams buildLayoutParams(
        FrameLayout root,
        BridgeVisualPrefs.VisualConfig visualConfig,
        float density
    ) {
        int height = Math.round(BridgeVisualPrefs.clampHeightDp(visualConfig.heightDp) * density);
        int width = BottomCaptureStripView.computeStripWidthPx(
            root.getWidth(),
            density,
            visualConfig.widthDp
        );
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            width,
            Math.max(1, height),
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        params.bottomMargin = Math.round(BridgeVisualPrefs.bottomMarginDp(visualConfig) * density);
        return params;
    }

    private void detachNow() {
        if (stripView != null) {
            try {
                ViewGroup parent = (ViewGroup) stripView.getParent();
                if (parent != null) parent.removeView(stripView);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to detach bottom capture strip: " + t);
            }
        }
        stripView = null;
        attachedRoot = null;
        lastStripHeightPx = 0;
        attachRetries = 0;
        setStatus(BridgeCaptureStatus.unsupported("not attached"));
    }

    private FrameLayout resolveDecorRoot(InputMethodService service) {
        try {
            Dialog dialog = service.getWindow();
            if (dialog == null) return null;
            Window window = dialog.getWindow();
            if (window == null) return null;
            View decorView = window.getDecorView();
            if (!(decorView instanceof FrameLayout)) return null;
            return (FrameLayout) decorView;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": resolveDecorRoot failed: " + t);
            return null;
        }
    }

    private void setStatus(BridgeCaptureStatus status) {
        this.status = status;
        if (listener != null) listener.onCaptureHostStatusChanged(status);
        if (status != null && status.state != BridgeCaptureStatus.State.RECORDING) {
            logDiagnostic(status);
        }
    }

    private void logDiagnostic(BridgeCaptureStatus diagnosticStatus) {
        XposedBridge.log(TAG + ": capture diagnostic: " + BridgeCaptureDiagnostic.summary(
            targetPackage,
            diagnosticStatus,
            lastStripHeightPx,
            lastStripHeightPx > 0 ? "bottom_safe_area" : "none",
            diagnosticStatus == null ? "" : diagnosticStatus.message
        ));
    }
}
