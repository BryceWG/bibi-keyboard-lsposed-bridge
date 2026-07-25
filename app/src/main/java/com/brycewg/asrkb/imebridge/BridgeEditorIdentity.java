package com.brycewg.asrkb.imebridge;

final class BridgeEditorIdentity {
    final String packageName;
    final int fieldId;
    final int inputType;
    final int imeOptions;

    BridgeEditorIdentity(String packageName, int fieldId, int inputType, int imeOptions) {
        this.packageName = packageName;
        this.fieldId = fieldId;
        this.inputType = inputType;
        this.imeOptions = imeOptions;
    }

    static boolean shouldAdvance(
        boolean restarting,
        BridgeEditorIdentity previous,
        BridgeEditorIdentity next
    ) {
        if (!restarting) return true;
        if (previous == null || next == null || !hasValue(previous.packageName) ||
            !hasValue(next.packageName)) {
            return true;
        }
        return previous.fieldId != next.fieldId ||
            previous.inputType != next.inputType ||
            previous.imeOptions != next.imeOptions ||
            !previous.packageName.equals(next.packageName);
    }

    private static boolean hasValue(String value) {
        return value != null && value.length() > 0;
    }
}
