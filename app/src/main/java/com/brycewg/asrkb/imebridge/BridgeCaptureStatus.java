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

    final State state;
    final String message;
    final int amplitude;

    private BridgeCaptureStatus(State state, String message, int amplitude) {
        this.state = state;
        this.message = message == null ? "" : message;
        this.amplitude = Math.max(0, amplitude);
    }

    static BridgeCaptureStatus unsupported(String message) {
        return new BridgeCaptureStatus(State.UNSUPPORTED, message, 0);
    }

    static BridgeCaptureStatus ready(String message) {
        return new BridgeCaptureStatus(State.READY, message, 0);
    }

    static BridgeCaptureStatus starting(String message) {
        return new BridgeCaptureStatus(State.STARTING, message, 0);
    }

    static BridgeCaptureStatus recording(int amplitude) {
        return new BridgeCaptureStatus(State.RECORDING, "recording", amplitude);
    }

    static BridgeCaptureStatus finishing() {
        return new BridgeCaptureStatus(State.FINISHING, "finishing", 0);
    }

    static BridgeCaptureStatus cancelling(String message) {
        return new BridgeCaptureStatus(State.CANCELLING, message, 0);
    }

    static BridgeCaptureStatus failed(String message) {
        return new BridgeCaptureStatus(State.FAILED, message, 0);
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
