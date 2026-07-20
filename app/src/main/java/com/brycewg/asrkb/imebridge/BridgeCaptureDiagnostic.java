/*
 * Privacy-safe diagnostics for the IME-side PCM capture strip.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

final class BridgeCaptureDiagnostic {
    private BridgeCaptureDiagnostic() {
    }

    static String summary(
        String targetPackage,
        BridgeCaptureStatus status,
        int stripHeightPx,
        String positionClass,
        String failureReason
    ) {
        String supportResult = "unsupported";
        if (status != null) {
            if (status.state == BridgeCaptureStatus.State.READY) {
                supportResult = "ready";
            } else if (status.state == BridgeCaptureStatus.State.FAILED) {
                supportResult = "failed";
            }
        }
        return "targetPackage=" + safePackage(targetPackage) +
            "; supportResult=" + supportResult +
            "; triggerStripSize=" + sizeClass(stripHeightPx) +
            "; triggerStripPosition=" + safePositionClass(positionClass) +
            "; failureReason=" + reasonCode(failureReason);
    }

    static String reasonCode(String reason) {
        if (reason == null || reason.length() == 0) return "none";
        String normalized = reason.toLowerCase();
        if (normalized.contains("not attached")) return "not_attached";
        if (normalized.contains("attached") || normalized.contains("ready")) return "none";
        if (normalized.contains("permission")) return "microphone_permission";
        if (normalized.contains("audio record")) return "audio_record_failed";
        if (normalized.contains("input connection")) return "no_input_connection";
        if (normalized.contains("sensitive")) return "sensitive_field";
        if (normalized.contains("window not ready")) return "window_not_ready";
        if (normalized.contains("window root")) return "unsupported_window_root";
        if (normalized.contains("attach failed")) return "attach_failed";
        if (normalized.contains("hidden")) return "window_hidden";
        if (normalized.contains("destroy")) return "destroy";
        if (normalized.contains("cancel")) return "cancelled";
        return "other";
    }

    private static String sizeClass(int stripHeightPx) {
        if (stripHeightPx <= 0) return "none";
        if (stripHeightPx < 24) return "small";
        if (stripHeightPx <= 96) return "standard";
        return "large";
    }

    private static String safePositionClass(String positionClass) {
        if ("bottom_safe_area".equals(positionClass)) return positionClass;
        if ("none".equals(positionClass)) return positionClass;
        return "other";
    }

    private static String safePackage(String targetPackage) {
        if (targetPackage == null || targetPackage.length() == 0) return "unknown";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < targetPackage.length(); i++) {
            char c = targetPackage.charAt(i);
            if ((c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                c == '.' ||
                c == '_' ||
                c == '-') {
                out.append(c);
            }
        }
        return out.length() == 0 ? "unknown" : out.toString();
    }
}
