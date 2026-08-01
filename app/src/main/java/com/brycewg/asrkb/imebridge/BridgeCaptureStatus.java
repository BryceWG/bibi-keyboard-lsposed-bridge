/*
 * Small status model for the IME-side capture trigger.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class BridgeCaptureStatus {
    enum State {
        UNSUPPORTED,
        READY,
        STARTING,
        RECORDING,
        FINISHING,
        CANCELLING,
        FAILED
    }

    static final int FAILURE_REASON_NONE = 0;
    static final int FAILURE_REASON_NO_INPUT_CONNECTION = 1;
    static final int FAILURE_REASON_SENSITIVE_FIELD = 2;
    static final int FAILURE_REASON_AUDIO_RECORD = 3;
    static final int FAILURE_REASON_RECORDING = 4;
    static final int FAILURE_REASON_FINISH = 5;

    final State state;
    final String message;
    final int amplitude;
    final int failureCode;

    private BridgeCaptureStatus(State state, String message, int amplitude, int failureCode) {
        this.state = state;
        this.message = message == null ? "" : message;
        this.amplitude = Math.max(0, amplitude);
        this.failureCode = failureCode;
    }

    static BridgeCaptureStatus unsupported(String message) {
        return new BridgeCaptureStatus(State.UNSUPPORTED, message, 0, FAILURE_REASON_NONE);
    }

    static BridgeCaptureStatus ready(String message) {
        return new BridgeCaptureStatus(State.READY, message, 0, FAILURE_REASON_NONE);
    }

    static BridgeCaptureStatus starting(String message) {
        return new BridgeCaptureStatus(State.STARTING, message, 0, FAILURE_REASON_NONE);
    }

    static BridgeCaptureStatus recording(int amplitude) {
        return new BridgeCaptureStatus(State.RECORDING, "recording", amplitude, FAILURE_REASON_NONE);
    }

    static BridgeCaptureStatus finishing() {
        return new BridgeCaptureStatus(State.FINISHING, "finishing", 0, FAILURE_REASON_NONE);
    }

    static BridgeCaptureStatus cancelling(String message) {
        return new BridgeCaptureStatus(State.CANCELLING, message, 0, FAILURE_REASON_NONE);
    }

    static BridgeCaptureStatus failed(String message) {
        return failed(message, FAILURE_REASON_NONE);
    }

    static BridgeCaptureStatus failed(String message, int failureCode) {
        return new BridgeCaptureStatus(State.FAILED, message, 0, failureCode);
    }

    boolean supportsPcmRecording() {
        return state != State.UNSUPPORTED;
    }

    boolean isActiveCapture() {
        return state == State.STARTING ||
            state == State.RECORDING ||
            state == State.FINISHING ||
            state == State.CANCELLING;
    }
}
