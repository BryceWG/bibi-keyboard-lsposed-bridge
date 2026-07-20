package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeLocalePrefsTest {
    @Test
    public void normalizeMapsAliasesToSupportedTags() {
        assertEquals(BridgeLocalePrefs.TAG_SYSTEM, BridgeLocalePrefs.normalize(null));
        assertEquals(BridgeLocalePrefs.TAG_SYSTEM, BridgeLocalePrefs.normalize(""));
        assertEquals(BridgeLocalePrefs.TAG_SYSTEM, BridgeLocalePrefs.normalize("auto"));
        assertEquals(BridgeLocalePrefs.TAG_EN, BridgeLocalePrefs.normalize("en-US"));
        assertEquals(BridgeLocalePrefs.TAG_ZH_CN, BridgeLocalePrefs.normalize("zh-Hans-CN"));
        assertEquals(BridgeLocalePrefs.TAG_ZH_CN, BridgeLocalePrefs.normalize("zh"));
        assertEquals(BridgeLocalePrefs.TAG_ZH_TW, BridgeLocalePrefs.normalize("zh-Hant-TW"));
        assertEquals(BridgeLocalePrefs.TAG_SYSTEM, BridgeLocalePrefs.normalize("ja"));
    }

    @Test
    public void systemFlagMatchesNormalizedSystemTag() {
        assertTrue(BridgeLocalePrefs.isSystem("system"));
        assertTrue(BridgeLocalePrefs.isSystem("auto"));
        assertFalse(BridgeLocalePrefs.isSystem("en"));
        assertFalse(BridgeLocalePrefs.isSystem("zh-CN"));
    }
}
