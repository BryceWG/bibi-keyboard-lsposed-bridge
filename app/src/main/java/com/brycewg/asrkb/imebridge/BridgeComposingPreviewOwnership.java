/*
 * Tracks composing preview text owned by the IME bridge.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge;

import android.view.inputmethod.InputConnection;

final class BridgeComposingPreviewOwnership {
    private static final int ANCHOR_MAX = 128;
    private static final int VERIFY_MAX = 10_000;

    private long editorGeneration;
    private String anchorBeforeCursor;
    private String ownedText;

    boolean setPreview(
        InputConnection inputConnection,
        long editorGeneration,
        CharSequence text,
        int cursorPosition
    ) {
        String previewText = text.toString();
        boolean sameEditor = hasOwnership() && this.editorGeneration == editorGeneration;
        String anchor = sameEditor
            ? anchorBeforeCursor
            : captureAnchor(inputConnection, previewText.length());
        boolean success;
        try {
            success = inputConnection.setComposingText(text, cursorPosition);
        } catch (Throwable ignored) {
            resetUnlessStillOwned(inputConnection, sameEditor);
            return false;
        }
        if (!success) {
            resetUnlessStillOwned(inputConnection, sameEditor);
            return false;
        }

        String replacedExpected = verified(anchor, previewText);
        String appendedText = sameEditor ? ownedText + previewText : null;
        String appendedExpected = verified(anchor, appendedText);
        int verifyLength = Math.max(lengthOf(replacedExpected), lengthOf(appendedExpected));
        String actual = previewText.length() > 0 && verifyLength > 0
            ? textBeforeCursor(inputConnection, verifyLength)
            : null;
        String actualOwnedText = null;
        if (replacedExpected != null && replacedExpected.equals(actual)) {
            actualOwnedText = previewText;
        } else if (appendedExpected != null && appendedExpected.equals(actual)) {
            actualOwnedText = appendedText;
        }

        if (anchor != null && actualOwnedText != null) {
            this.editorGeneration = editorGeneration;
            anchorBeforeCursor = anchor;
            ownedText = actualOwnedText;
        } else {
            reset();
        }
        return success;
    }

    boolean replaceAndFinish(
        InputConnection inputConnection,
        long editorGeneration,
        CharSequence text,
        int cursorPosition
    ) {
        Snapshot owned = takeVerified(inputConnection, editorGeneration);
        reset();
        if (owned == null) return setAndFinish(inputConnection, text, cursorPosition);

        boolean removed;
        try {
            removed = inputConnection.finishComposingText() &&
                inputConnection.deleteSurroundingText(owned.text.length(), 0);
        } catch (Throwable ignored) {
            removed = false;
        }
        if (!removed) return setAndFinish(inputConnection, text, cursorPosition);

        boolean written;
        try {
            written = inputConnection.setComposingText(text, cursorPosition);
        } catch (Throwable ignored) {
            written = false;
        }
        if (!written) {
            restore(inputConnection, owned);
            return false;
        }
        try {
            return inputConnection.finishComposingText();
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean clear(InputConnection inputConnection, long editorGeneration) {
        Snapshot owned = takeVerified(inputConnection, editorGeneration);
        reset();
        if (owned == null) return setAndFinish(inputConnection, "", 1);
        try {
            return inputConnection.finishComposingText() &&
                inputConnection.deleteSurroundingText(owned.text.length(), 0);
        } catch (Throwable ignored) {
            return false;
        }
    }

    void reset() {
        editorGeneration = 0L;
        anchorBeforeCursor = null;
        ownedText = null;
    }

    private Snapshot takeVerified(InputConnection inputConnection, long editorGeneration) {
        if (!hasOwnership() || this.editorGeneration != editorGeneration) return null;
        String expected = anchorBeforeCursor + ownedText;
        String actual = textBeforeCursor(inputConnection, expected.length());
        if (!expected.equals(actual)) return null;
        return new Snapshot(editorGeneration, anchorBeforeCursor, ownedText);
    }

    private String captureAnchor(InputConnection inputConnection, int previewLength) {
        int maxLength = Math.min(ANCHOR_MAX, VERIFY_MAX - previewLength);
        if (maxLength <= 0) return null;
        return textBeforeCursor(inputConnection, maxLength);
    }

    private String textBeforeCursor(InputConnection inputConnection, int length) {
        try {
            CharSequence value = inputConnection.getTextBeforeCursor(length, 0);
            return value == null ? null : value.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String verified(String anchor, String text) {
        if (anchor == null || text == null || anchor.length() + text.length() > VERIFY_MAX) {
            return null;
        }
        return anchor + text;
    }

    private boolean setAndFinish(
        InputConnection inputConnection,
        CharSequence text,
        int cursorPosition
    ) {
        try {
            return inputConnection.setComposingText(text, cursorPosition) &&
                inputConnection.finishComposingText();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void restore(InputConnection inputConnection, Snapshot owned) {
        try {
            if (!inputConnection.setComposingText(owned.text, 1)) return;
            String expected = owned.anchor + owned.text;
            if (expected.equals(textBeforeCursor(inputConnection, expected.length()))) {
                editorGeneration = owned.editorGeneration;
                anchorBeforeCursor = owned.anchor;
                ownedText = owned.text;
            }
        } catch (Throwable ignored) {
            reset();
        }
    }

    private boolean hasOwnership() {
        return anchorBeforeCursor != null && ownedText != null;
    }

    private void resetUnlessStillOwned(InputConnection inputConnection, boolean sameEditor) {
        if (!sameEditor) {
            reset();
            return;
        }
        String expected = anchorBeforeCursor + ownedText;
        if (!expected.equals(textBeforeCursor(inputConnection, expected.length()))) {
            reset();
        }
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static final class Snapshot {
        final long editorGeneration;
        final String anchor;
        final String text;

        Snapshot(long editorGeneration, String anchor, String text) {
            this.editorGeneration = editorGeneration;
            this.anchor = anchor;
            this.text = text;
        }
    }
}
