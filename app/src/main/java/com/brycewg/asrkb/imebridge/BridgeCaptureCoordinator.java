/*
 * Coordinates bottom-strip gesture, IME-process recording, and PCM session calls.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BridgeCaptureCoordinator {
    interface CaptureEnvironment {
        boolean hasInputConnection();
        boolean isSensitiveField();
    }

    interface StatusListener {
        void onCaptureStatusChanged(BridgeCaptureStatus status);
    }

    interface SessionClient {
        OperationResult begin(String sessionId);
        OperationResult writeFrame(String sessionId, byte[] pcm, int sampleRate, int channels);
        OperationResult finish(String sessionId);
        OperationResult cancel(String sessionId);
        void close();
    }

    interface AudioRecorder {
        boolean start(String sessionId, AudioRecorderCallback callback);
        void stop();
        void cancel();
        boolean isRecording();
    }

    interface AudioRecorderCallback {
        void onPcmFrame(String sessionId, byte[] pcm, int sampleRate, int channels, int amplitude);
        void onRecorderError(String sessionId, String message);
    }

    static final class OperationResult {
        final int code;
        final String message;

        private OperationResult(int code, String message) {
            this.code = code;
            this.message = message == null ? "" : message;
        }

        static OperationResult ok(String message) {
            return new OperationResult(BridgeContract.PCM_RESULT_OK, message);
        }

        static OperationResult error(int code, String message) {
            return new OperationResult(code, message);
        }

        boolean isSuccess() {
            return code == BridgeContract.PCM_RESULT_OK;
        }
    }

    private enum State {
        IDLE,
        STARTING,
        RECORDING,
        FINISHING,
        CANCELLING
    }

    private final CaptureEnvironment environment;
    private final SessionClient sessionClient;
    private final AudioRecorder recorder;
    private final StatusListener statusListener;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final Object lock = new Object();

    private State state = State.IDLE;
    private String activeSessionId;
    private boolean finishAfterStart;
    private boolean cancelAfterStart;
    private BridgeCaptureStatus lastStatus = BridgeCaptureStatus.unsupported("not attached");

    BridgeCaptureCoordinator(
        CaptureEnvironment environment,
        SessionClient sessionClient,
        AudioRecorder recorder,
        StatusListener statusListener
    ) {
        this(
            environment,
            sessionClient,
            recorder,
            statusListener,
            Executors.newSingleThreadExecutor(),
            true
        );
    }

    BridgeCaptureCoordinator(
        CaptureEnvironment environment,
        SessionClient sessionClient,
        AudioRecorder recorder,
        StatusListener statusListener,
        Executor executor
    ) {
        this(environment, sessionClient, recorder, statusListener, executor, false);
    }

    private BridgeCaptureCoordinator(
        CaptureEnvironment environment,
        SessionClient sessionClient,
        AudioRecorder recorder,
        StatusListener statusListener,
        Executor executor,
        boolean ownsExecutor
    ) {
        this.environment = environment;
        this.sessionClient = sessionClient;
        this.recorder = recorder;
        this.statusListener = statusListener;
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
    }

    void markReady(String message) {
        updateStatus(BridgeCaptureStatus.ready(message));
    }

    void markUnsupported(String message) {
        cancelActiveCapture(message);
        updateStatus(BridgeCaptureStatus.unsupported(message));
    }

    BridgeCaptureStatus getLastStatus() {
        return lastStatus;
    }

    boolean supportsPcmRecording() {
        return lastStatus.supportsPcmRecording();
    }

    void startCapture() {
        synchronized (lock) {
            if (state != State.IDLE) return;
            if (environment == null || !environment.hasInputConnection()) {
                updateStatusLocked(BridgeCaptureStatus.failed("no input connection"));
                return;
            }
            if (environment.isSensitiveField()) {
                updateStatusLocked(BridgeCaptureStatus.failed("sensitive field"));
                return;
            }
            state = State.STARTING;
            finishAfterStart = false;
            cancelAfterStart = false;
            activeSessionId = UUID.randomUUID().toString();
            updateStatusLocked(BridgeCaptureStatus.starting("starting"));
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                beginAndStartRecorder();
            }
        });
    }

    void finishCapture() {
        String sessionId;
        synchronized (lock) {
            if (state == State.STARTING) {
                if (cancelAfterStart) return;
                finishAfterStart = true;
                return;
            }
            if (state != State.RECORDING || activeSessionId == null) return;
            state = State.FINISHING;
            sessionId = activeSessionId;
            updateStatusLocked(BridgeCaptureStatus.finishing());
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                finishSession(sessionId);
            }
        });
    }

    void cancelActiveCapture(String reason) {
        String sessionId;
        synchronized (lock) {
            if (state == State.STARTING) {
                cancelAfterStart = true;
                finishAfterStart = false;
                updateStatusLocked(BridgeCaptureStatus.cancelling(reason));
                return;
            }
            if (state == State.IDLE || activeSessionId == null) {
                sessionClient.close();
                return;
            }
            if (state == State.CANCELLING || state == State.FINISHING) return;
            state = State.CANCELLING;
            sessionId = activeSessionId;
            updateStatusLocked(BridgeCaptureStatus.cancelling(reason));
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                cancelSession(sessionId, reason);
            }
        });
    }

    void destroy() {
        boolean idle;
        synchronized (lock) {
            idle = state == State.IDLE;
        }
        cancelActiveCapture("destroy");
        if (idle) sessionClient.close();
        if (ownsExecutor && executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
    }

    private void beginAndStartRecorder() {
        String sessionId = currentSessionId();
        if (sessionId == null) return;

        OperationResult begin = sessionClient.begin(sessionId);
        if (!begin.isSuccess()) {
            failIfCurrent(sessionId, begin.message);
            return;
        }

        boolean started = recorder.start(sessionId, new AudioRecorderCallback() {
            @Override
            public void onPcmFrame(String frameSessionId, byte[] pcm, int sampleRate, int channels, int amplitude) {
                handleFrame(frameSessionId, pcm, sampleRate, channels, amplitude);
            }

            @Override
            public void onRecorderError(String errorSessionId, String message) {
                handleRecorderError(errorSessionId, message);
            }
        });
        if (!started) {
            failIfCurrent(sessionId, "audio record failed");
            return;
        }

        boolean shouldFinish;
        boolean shouldCancel;
        synchronized (lock) {
            if (!sessionId.equals(activeSessionId) || state != State.STARTING) {
                shouldCancel = true;
                shouldFinish = false;
            } else {
                shouldCancel = cancelAfterStart;
                shouldFinish = finishAfterStart;
                if (!shouldCancel && !shouldFinish) {
                    state = State.RECORDING;
                    updateStatusLocked(BridgeCaptureStatus.recording(0));
                }
            }
        }
        if (shouldCancel) {
            cancelSession(sessionId, "cancelled before recording ready");
        } else if (shouldFinish) {
            finishSession(sessionId);
        }
    }

    private void handleFrame(String sessionId, byte[] pcm, int sampleRate, int channels, int amplitude) {
        synchronized (lock) {
            if (state != State.RECORDING || activeSessionId == null || !activeSessionId.equals(sessionId)) {
                return;
            }
            updateStatusLocked(BridgeCaptureStatus.recording(amplitude));
        }
        byte[] frame = pcm == null ? new byte[0] : pcm;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                writeFrame(sessionId, frame, sampleRate, channels);
            }
        });
    }

    private void writeFrame(String sessionId, byte[] pcm, int sampleRate, int channels) {
        synchronized (lock) {
            if (activeSessionId == null || !activeSessionId.equals(sessionId)) return;
            if (state != State.RECORDING && state != State.FINISHING) return;
        }
        OperationResult result = sessionClient.writeFrame(sessionId, pcm, sampleRate, channels);
        if (!result.isSuccess()) {
            handleRecorderError(sessionId, result.message);
        }
    }

    private void handleRecorderError(String sessionId, String message) {
        boolean shouldCancel;
        synchronized (lock) {
            shouldCancel = activeSessionId != null &&
                activeSessionId.equals(sessionId) &&
                (state == State.STARTING || state == State.RECORDING);
            if (shouldCancel) {
                state = State.CANCELLING;
                updateStatusLocked(BridgeCaptureStatus.cancelling(message));
            }
        }
        if (!shouldCancel) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                cancelSession(sessionId, message);
            }
        });
    }

    private void finishSession(String sessionId) {
        recorder.stop();
        OperationResult result = sessionClient.finish(sessionId);
        synchronized (lock) {
            if (sessionId.equals(activeSessionId)) {
                clearActiveLocked();
                if (result.isSuccess()) {
                    updateStatusLocked(BridgeCaptureStatus.ready("ready"));
                } else {
                    updateStatusLocked(BridgeCaptureStatus.failed(result.message));
                }
            }
        }
    }

    private void cancelSession(String sessionId, String reason) {
        recorder.cancel();
        sessionClient.cancel(sessionId);
        synchronized (lock) {
            if (sessionId.equals(activeSessionId)) {
                clearActiveLocked();
                updateStatusLocked(BridgeCaptureStatus.ready(reason == null ? "cancelled" : reason));
            }
        }
    }

    private void failIfCurrent(String sessionId, String message) {
        recorder.cancel();
        sessionClient.cancel(sessionId);
        synchronized (lock) {
            if (sessionId.equals(activeSessionId)) {
                clearActiveLocked();
                updateStatusLocked(BridgeCaptureStatus.failed(message));
            }
        }
    }

    private String currentSessionId() {
        synchronized (lock) {
            return activeSessionId;
        }
    }

    private void clearActiveLocked() {
        state = State.IDLE;
        activeSessionId = null;
        finishAfterStart = false;
        cancelAfterStart = false;
    }

    private void updateStatus(BridgeCaptureStatus status) {
        synchronized (lock) {
            updateStatusLocked(status);
        }
    }

    private void updateStatusLocked(BridgeCaptureStatus status) {
        lastStatus = status;
        if (statusListener != null) statusListener.onCaptureStatusChanged(status);
    }
}
