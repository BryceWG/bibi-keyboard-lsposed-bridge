package com.brycewg.asrkb.imebridge;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

final class BridgeEditorPolicy {
    private BridgeEditorPolicy() {
    }

    static boolean isSensitive(int inputType) {
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    static boolean allowsPersonalizedLearning(int inputType, int imeOptions) {
        return inputType != InputType.TYPE_NULL &&
            !isSensitive(inputType) &&
            (imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
    }
}
