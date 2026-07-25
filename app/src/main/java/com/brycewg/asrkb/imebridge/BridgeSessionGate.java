package com.brycewg.asrkb.imebridge;

final class BridgeSessionGate {
    private BridgeSessionGate() {
    }

    static boolean accepts(String activeSessionId, String requestSessionId) {
        boolean hasActiveSession = hasValue(activeSessionId);
        boolean hasRequestSession = hasValue(requestSessionId);
        if (hasRequestSession) {
            return hasActiveSession && activeSessionId.equals(requestSessionId);
        }
        return !hasActiveSession;
    }

    static boolean accepts(
        String activeSessionId,
        String requestSessionId,
        long sessionEditorGeneration,
        long currentEditorGeneration
    ) {
        if (!accepts(activeSessionId, requestSessionId)) return false;
        return !hasValue(requestSessionId) ||
            (sessionEditorGeneration > 0 && sessionEditorGeneration == currentEditorGeneration);
    }

    private static boolean hasValue(String value) {
        return value != null && value.length() > 0;
    }
}
