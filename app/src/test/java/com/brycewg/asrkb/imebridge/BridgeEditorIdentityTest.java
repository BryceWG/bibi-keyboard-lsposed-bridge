package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeEditorIdentityTest {
    @Test
    public void sameEditorRestartKeepsGenerationAndSession() {
        BridgeEditorIdentity editor = editor("app.one", 7, 1, 2);

        assertFalse(BridgeEditorIdentity.shouldAdvance(true, editor, editor("app.one", 7, 1, 2)));
    }

    @Test
    public void changedOrUnknownEditorAdvancesConservatively() {
        BridgeEditorIdentity editor = editor("app.one", 7, 1, 2);

        assertTrue(BridgeEditorIdentity.shouldAdvance(false, editor, editor));
        assertTrue(BridgeEditorIdentity.shouldAdvance(true, editor, editor("app.one", 8, 1, 2)));
        assertTrue(BridgeEditorIdentity.shouldAdvance(true, editor, editor("app.two", 7, 1, 2)));
        assertTrue(BridgeEditorIdentity.shouldAdvance(true, editor, null));
        assertTrue(BridgeEditorIdentity.shouldAdvance(true, editor, editor(null, 7, 1, 2)));
    }

    private BridgeEditorIdentity editor(String packageName, int fieldId, int inputType, int imeOptions) {
        return new BridgeEditorIdentity(packageName, fieldId, inputType, imeOptions);
    }
}
