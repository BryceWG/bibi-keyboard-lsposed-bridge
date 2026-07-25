package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;

public class BridgeEditorPolicyTest {
    @Test
    public void personalizedLearningRejectsSensitiveAndOptOutEditors() {
        assertFalse(BridgeEditorPolicy.allowsPersonalizedLearning(
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            0
        ));
        assertFalse(BridgeEditorPolicy.allowsPersonalizedLearning(
            InputType.TYPE_CLASS_TEXT,
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        ));
        assertTrue(BridgeEditorPolicy.allowsPersonalizedLearning(
            InputType.TYPE_CLASS_TEXT,
            0
        ));
    }
}
