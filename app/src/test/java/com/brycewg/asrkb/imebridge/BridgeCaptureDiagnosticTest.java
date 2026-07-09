package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeCaptureDiagnosticTest {
    @Test
    public void summaryContainsOnlySafeDiagnosticFields() {
        String summary = BridgeCaptureDiagnostic.summary(
            "com.example.ime",
            BridgeCaptureStatus.ready("attached viewText=secret candidate=word"),
            54,
            "bottom_safe_area",
            "attached viewText=secret candidate=word"
        );

        assertTrue(summary.contains("targetPackage=com.example.ime"));
        assertTrue(summary.contains("supportResult=ready"));
        assertTrue(summary.contains("triggerStripSize=standard"));
        assertTrue(summary.contains("triggerStripPosition=bottom_safe_area"));
        assertTrue(summary.contains("failureReason=none"));
        assertFalse(summary.contains("viewText"));
        assertFalse(summary.contains("candidate"));
        assertFalse(summary.contains("secret"));
        assertFalse(summary.contains("word"));
    }

    @Test
    public void failureReasonsAreClassifiedWithoutRawText() {
        String summary = BridgeCaptureDiagnostic.summary(
            "com.example.ime",
            BridgeCaptureStatus.unsupported("attach failed: TextView password=123 token=abc"),
            0,
            "root:TextView",
            "attach failed: TextView password=123 token=abc"
        );

        assertTrue(summary.contains("supportResult=unsupported"));
        assertTrue(summary.contains("triggerStripSize=none"));
        assertTrue(summary.contains("triggerStripPosition=other"));
        assertTrue(summary.contains("failureReason=attach_failed"));
        assertFalse(summary.contains("TextView"));
        assertFalse(summary.contains("password=123"));
        assertFalse(summary.contains("token=abc"));
    }
}
