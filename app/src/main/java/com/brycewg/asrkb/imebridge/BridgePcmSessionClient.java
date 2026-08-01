/*
 * Minimal Binder client for the main app bridge PCM session service.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class BridgePcmSessionClient implements BridgeCaptureCoordinator.SessionClient {
    private static final long BIND_TIMEOUT_MS = 700L;

    private final Context context;
    private IBinder binder;
    private ServiceConnection connection;
    private String activeSessionId;

    BridgePcmSessionClient(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult begin(String sessionId) {
        if (sessionId == null || sessionId.length() == 0) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BAD_REQUEST,
                "empty session id"
            );
        }
        BridgeCaptureCoordinator.OperationResult last = null;
        String[] hostPackages = BridgeHostRouting.packages();
        for (String appPackage : hostPackages) {
            unbind();
            BridgeCaptureCoordinator.OperationResult bound = bindTo(appPackage);
            if (!bound.isSuccess()) {
                last = bound;
                continue;
            }
            BridgeCaptureCoordinator.OperationResult result = transactString(
                BridgeContract.PCM_TRANSACTION_BEGIN,
                sessionId
            );
            if (result.isSuccess()) {
                activeSessionId = sessionId;
                return result;
            }
            last = result;
            // Do not mask an explicit configuration error with a later bind failure.
            if (hostPackages.length == 1 || stopsFallbackFor(result.code)) break;
        }
        unbind();
        if (last != null) {
            int warningMessageRes = warningMessageResFor(last.code);
            if (warningMessageRes != 0) BridgeUserNotifier.show(context, warningMessageRes);
            return last;
        }
        BridgeUserNotifier.show(context, R.string.bridge_toast_connection_failed);
        return BridgeCaptureCoordinator.OperationResult.error(
            BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
            "pcm service unavailable"
        );
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult writeFrame(
        String sessionId,
        byte[] pcm,
        int sampleRate,
        int channels
    ) {
        if (!isActiveSession(sessionId)) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_STALE_SESSION,
                "stale session"
            );
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BridgeContract.PCM_DESCRIPTOR);
            data.writeString(sessionId);
            data.writeByteArray(pcm == null ? new byte[0] : pcm);
            data.writeInt(sampleRate);
            data.writeInt(channels);
            if (!binder.transact(BridgeContract.PCM_TRANSACTION_WRITE_FRAME, data, reply, 0)) {
                return BridgeCaptureCoordinator.OperationResult.error(
                    BridgeContract.PCM_RESULT_UNSUPPORTED,
                    "unsupported transaction"
                );
            }
            reply.readException();
            return readResult(reply);
        } catch (Throwable t) {
            return transportFailure(t, "write frame failed");
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult finish(String sessionId) {
        if (!isActiveSession(sessionId)) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_STALE_SESSION,
                "stale session"
            );
        }
        BridgeCaptureCoordinator.OperationResult result = transactString(
            BridgeContract.PCM_TRANSACTION_FINISH,
            sessionId
        );
        activeSessionId = null;
        unbind();
        return result;
    }

    @Override
    public synchronized BridgeCaptureCoordinator.OperationResult cancel(String sessionId) {
        if (!isActiveSession(sessionId)) {
            return BridgeCaptureCoordinator.OperationResult.ok("already inactive");
        }
        BridgeCaptureCoordinator.OperationResult result = transactString(
            BridgeContract.PCM_TRANSACTION_CANCEL,
            sessionId
        );
        activeSessionId = null;
        unbind();
        return result;
    }

    @Override
    public synchronized void close() {
        activeSessionId = null;
        unbind();
    }

    private boolean isActiveSession(String sessionId) {
        return activeSessionId != null && activeSessionId.equals(sessionId) && binder != null;
    }

    static int warningMessageResFor(int resultCode) {
        if (resultCode == BridgeContract.PCM_RESULT_FEATURE_DISABLED) {
            return R.string.bridge_toast_feature_disabled;
        }
        if (resultCode == BridgeContract.PCM_RESULT_NO_INPUT_CONNECTION) {
            return R.string.bridge_toast_no_input_connection;
        }
        if (resultCode == BridgeContract.PCM_RESULT_SENSITIVE_FIELD) {
            return R.string.bridge_toast_sensitive_field;
        }
        if (resultCode == BridgeContract.PCM_RESULT_UNSUPPORTED) {
            return R.string.bridge_toast_protocol_mismatch;
        }
        if (resultCode == BridgeContract.PCM_RESULT_SESSION_UNAVAILABLE) {
            return R.string.bridge_toast_session_unavailable;
        }
        if (resultCode == BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE) {
            return R.string.bridge_toast_connection_failed;
        }
        return 0;
    }

    static boolean stopsFallbackFor(int resultCode) {
        return resultCode == BridgeContract.PCM_RESULT_FEATURE_DISABLED ||
            resultCode == BridgeContract.PCM_RESULT_NO_INPUT_CONNECTION ||
            resultCode == BridgeContract.PCM_RESULT_SENSITIVE_FIELD ||
            resultCode == BridgeContract.PCM_RESULT_SESSION_UNAVAILABLE;
    }

    private BridgeCaptureCoordinator.OperationResult bindTo(String appPackage) {
        if (context == null) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "no context"
            );
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final IBinder[] result = new IBinder[1];
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                result[0] = service;
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                boolean hadActiveSession;
                synchronized (BridgePcmSessionClient.this) {
                    if (connection != this) return;
                    hadActiveSession = activeSessionId != null;
                    binder = null;
                    activeSessionId = null;
                }
                if (hadActiveSession) {
                    BridgeUserNotifier.show(context, R.string.bridge_toast_connection_failed);
                }
            }
        };
        Intent intent = new Intent(BridgeContract.PCM_ACTION_BIND_SERVICE);
        intent.setPackage(appPackage);
        boolean requested;
        try {
            requested = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "bind failed: " + t.getClass().getSimpleName()
            );
        }
        if (!requested) {
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "bind rejected"
            );
        }
        boolean connected;
        try {
            connected = latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connected = false;
        }
        if (!connected || result[0] == null) {
            try {
                context.unbindService(serviceConnection);
            } catch (Throwable ignored) {
            }
            return BridgeCaptureCoordinator.OperationResult.error(
                BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE,
                "bind timeout"
            );
        }
        binder = result[0];
        connection = serviceConnection;
        return BridgeCaptureCoordinator.OperationResult.ok("bound");
    }

    private BridgeCaptureCoordinator.OperationResult transactString(int code, String sessionId) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BridgeContract.PCM_DESCRIPTOR);
            data.writeString(sessionId);
            if (!binder.transact(code, data, reply, 0)) {
                return BridgeCaptureCoordinator.OperationResult.error(
                    BridgeContract.PCM_RESULT_UNSUPPORTED,
                    "unsupported transaction"
                );
            }
            reply.readException();
            return readResult(reply);
        } catch (Throwable t) {
            return transportFailure(t, "transact failed");
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private BridgeCaptureCoordinator.OperationResult readResult(Parcel reply) {
        int code = reply.readInt();
        String message = reply.readString();
        boolean requestAudioFocus = reply.dataAvail() >= Integer.BYTES && reply.readInt() != 0;
        if (code == BridgeContract.PCM_RESULT_OK) {
            return BridgeCaptureCoordinator.OperationResult.ok(
                message == null ? "ok" : message,
                requestAudioFocus
            );
        }
        return BridgeCaptureCoordinator.OperationResult.error(
            code,
            message == null ? BridgeContract.pcmMessageForCode(code) : message
        );
    }

    private BridgeCaptureCoordinator.OperationResult transportFailure(Throwable error, String fallbackMessage) {
        int resultCode = transportFailureCodeFor(error);
        if (resultCode == BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE) {
            BridgeUserNotifier.show(context, R.string.bridge_toast_connection_failed);
        }
        return BridgeCaptureCoordinator.OperationResult.error(
            resultCode,
            error.getMessage() == null ? fallbackMessage : error.getMessage()
        );
    }

    static int transportFailureCodeFor(Throwable error) {
        return BridgeContract.PCM_RESULT_BRIDGE_UNAVAILABLE;
    }

    private void unbind() {
        if (connection != null && context != null) {
            try {
                context.unbindService(connection);
            } catch (Throwable ignored) {
            }
        }
        connection = null;
        binder = null;
    }
}
