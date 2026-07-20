package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HookInstallGateTest {
    @Test
    public void webViewPackageLoadDoesNotInstallImeHooksTwice() {
        HookInstallGate gate = new HookInstallGate();

        assertTrue(gate.tryInstall("com.tencent.wetype"));
        assertFalse(gate.tryInstall("com.google.android.webview"));

        assertEquals("com.tencent.wetype", gate.getInstalledPackage());
    }

    @Test
    public void servicePackageOverridesLoadPackageAndCanonicalPackageIsFallback() {
        HookInstallGate gate = new HookInstallGate();
        assertTrue(gate.tryInstall("com.google.android.webview"));

        assertEquals(
            "com.tencent.wetype",
            gate.resolveHostPackage("com.tencent.wetype")
        );
        assertEquals(
            "com.google.android.webview",
            gate.resolveHostPackage("")
        );
    }
}
