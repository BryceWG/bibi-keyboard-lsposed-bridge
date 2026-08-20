package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BridgeImeSwitcherTest {
    @Test
    public void evaluateRejectsBlankTargets() {
        assertEquals(BridgeImeSwitcher.RESULT_EMPTY_TARGET, BridgeImeSwitcher.evaluate(null, "a"));
        assertEquals(BridgeImeSwitcher.RESULT_EMPTY_TARGET, BridgeImeSwitcher.evaluate("  ", "a"));
        assertEquals(
            R.string.bridge_toast_ime_not_selected,
            BridgeImeSwitcher.warningMessageResFor(BridgeImeSwitcher.RESULT_EMPTY_TARGET)
        );
    }

    @Test
    public void evaluateSkipsSwitchWhenAlreadyCurrent() {
        assertEquals(
            BridgeImeSwitcher.RESULT_ALREADY_CURRENT,
            BridgeImeSwitcher.evaluate("com.example/.Ime", "com.example/.Ime")
        );
        assertEquals(0, BridgeImeSwitcher.warningMessageResFor(BridgeImeSwitcher.RESULT_ALREADY_CURRENT));
    }

    @Test
    public void evaluateAcceptsADifferentEnabledTarget() {
        assertEquals(
            BridgeImeSwitcher.RESULT_OK,
            BridgeImeSwitcher.evaluate(
                "  com.brycewg.asrkb/.ime.AsrKeyboardService  ",
                "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
            )
        );
        assertEquals(0, BridgeImeSwitcher.warningMessageResFor(BridgeImeSwitcher.RESULT_OK));
        assertEquals(
            R.string.bridge_toast_ime_switch_failed,
            BridgeImeSwitcher.warningMessageResFor(BridgeImeSwitcher.RESULT_FAILED)
        );
    }
}
