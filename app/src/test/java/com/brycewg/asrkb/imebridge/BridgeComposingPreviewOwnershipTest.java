package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import org.junit.Test;

public class BridgeComposingPreviewOwnershipTest {
    @Test
    public void committedPartialsAreReplacedByOneFinalResult() {
        CommittingInputConnection editor = new CommittingInputConnection("prefix:");
        BridgeComposingPreviewOwnership ownership = new BridgeComposingPreviewOwnership();

        assertTrue(ownership.setPreview(editor.connection, 1L, "partial-a", 1));
        assertTrue(ownership.setPreview(editor.connection, 1L, "partial-b", 1));
        assertTrue(ownership.replaceAndFinish(editor.connection, 1L, "final", 1));

        assertEquals("prefix:final", editor.text());
    }

    @Test
    public void changedContextIsNotDeleted() {
        CommittingInputConnection editor = new CommittingInputConnection("prefix:");
        BridgeComposingPreviewOwnership ownership = new BridgeComposingPreviewOwnership();

        assertTrue(ownership.setPreview(editor.connection, 1L, "partial", 1));
        editor.appendUserText(" user");
        assertTrue(ownership.replaceAndFinish(editor.connection, 1L, "final", 1));

        assertEquals("prefix:partial userfinal", editor.text());
    }

    @Test
    public void failedFinalWriteRestoresOwnedPreview() {
        CommittingInputConnection editor = new CommittingInputConnection("prefix:");
        BridgeComposingPreviewOwnership ownership = new BridgeComposingPreviewOwnership();

        assertTrue(ownership.setPreview(editor.connection, 1L, "partial", 1));
        editor.rejectNextComposingWrite = true;
        assertFalse(ownership.replaceAndFinish(editor.connection, 1L, "final", 1));

        assertEquals("prefix:partial", editor.text());
    }

    @Test
    public void failedPartialWriteKeepsPreviousOwnership() {
        CommittingInputConnection editor = new CommittingInputConnection("prefix:");
        BridgeComposingPreviewOwnership ownership = new BridgeComposingPreviewOwnership();

        assertTrue(ownership.setPreview(editor.connection, 1L, "partial", 1));
        editor.rejectNextComposingWrite = true;
        assertFalse(ownership.setPreview(editor.connection, 1L, "ignored", 1));
        assertTrue(ownership.replaceAndFinish(editor.connection, 1L, "final", 1));

        assertEquals("prefix:final", editor.text());
    }

    @Test
    public void cancelRemovesCommittedPreview() {
        CommittingInputConnection editor = new CommittingInputConnection("prefix:");
        BridgeComposingPreviewOwnership ownership = new BridgeComposingPreviewOwnership();

        assertTrue(ownership.setPreview(editor.connection, 1L, "partial", 1));
        assertTrue(ownership.clear(editor.connection, 1L));

        assertEquals("prefix:", editor.text());
    }

    @Test
    public void anchorReadIsBounded() {
        CommittingInputConnection editor = new CommittingInputConnection("p".repeat(1_000));
        BridgeComposingPreviewOwnership ownership = new BridgeComposingPreviewOwnership();

        assertTrue(ownership.setPreview(editor.connection, 1L, "a".repeat(512), 1));

        assertTrue(editor.beforeCursorRequests.get(0) <= 128);
    }

    private static final class CommittingInputConnection {
        private final StringBuilder content;
        final InputConnection connection;
        boolean rejectNextComposingWrite;
        final java.util.List<Integer> beforeCursorRequests = new java.util.ArrayList<>();

        CommittingInputConnection(String initialText) {
            content = new StringBuilder(initialText);
            connection = (InputConnection) Proxy.newProxyInstance(
                InputConnection.class.getClassLoader(),
                new Class<?>[] {InputConnection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setComposingText":
                        case "commitText":
                            if (rejectNextComposingWrite) {
                                rejectNextComposingWrite = false;
                                return false;
                            }
                            content.append(args[0]);
                            return true;
                        case "getTextBeforeCursor":
                            int length = (Integer) args[0];
                            beforeCursorRequests.add(length);
                            return content.substring(Math.max(0, content.length() - length));
                        case "deleteSurroundingText":
                            int beforeLength = (Integer) args[0];
                            content.delete(content.length() - beforeLength, content.length());
                            return true;
                        case "finishComposingText":
                            return true;
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "CommittingInputConnection";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                }
            );
        }

        String text() {
            return content.toString();
        }

        void appendUserText(String text) {
            content.append(text);
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            if (type == double.class) return 0d;
            return null;
        }
    }
}
