package com.yucli.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabInfoTest {

    @Test
    void shouldCreateRecordWithAllFields() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("ABC123", "Google", "https://google.com", true);

        assertEquals("ABC123", tab.targetId());
        assertEquals("Google", tab.title());
        assertEquals("https://google.com", tab.url());
        assertTrue(tab.attached());
    }

    @Test
    void shouldCreateRecordWithNotAttached() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("DEF456", "GitHub", "https://github.com", false);

        assertEquals("DEF456", tab.targetId());
        assertEquals("GitHub", tab.title());
        assertEquals("https://github.com", tab.url());
        assertFalse(tab.attached());
    }

    @Test
    void shouldCreateRecordWithNullFields() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo(null, null, null, false);

        assertNull(tab.targetId());
        assertNull(tab.title());
        assertNull(tab.url());
        assertFalse(tab.attached());
    }

    @Test
    void shouldCreateRecordWithEmptyStrings() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("", "", "", true);

        assertEquals("", tab.targetId());
        assertEquals("", tab.title());
        assertEquals("", tab.url());
        assertTrue(tab.attached());
    }

    @Test
    void shouldBeEqualWhenAllFieldsMatch() {
        CdpSession.TabInfo tab1 = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);
        CdpSession.TabInfo tab2 = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);

        assertEquals(tab1, tab2);
        assertEquals(tab1.hashCode(), tab2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenFieldsDiffer() {
        CdpSession.TabInfo tab1 = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);
        CdpSession.TabInfo tab2 = new CdpSession.TabInfo("ID2", "Title", "https://example.com", true);

        assertNotEquals(tab1, tab2);
    }

    @Test
    void shouldNotBeEqualWhenAttachedDiffers() {
        CdpSession.TabInfo tab1 = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);
        CdpSession.TabInfo tab2 = new CdpSession.TabInfo("ID1", "Title", "https://example.com", false);

        assertNotEquals(tab1, tab2);
    }

    @Test
    void shouldBeEqualToItself() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);
        assertEquals(tab, tab);
    }

    @Test
    void shouldNotBeEqualToNull() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);
        assertNotEquals(null, tab);
    }

    @Test
    void shouldNotBeEqualToDifferentType() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);
        assertNotEquals("not a tab", tab);
    }

    @Test
    void shouldHaveConsistentHashCode() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("ID1", "Title", "https://example.com", true);
        int hash1 = tab.hashCode();
        int hash2 = tab.hashCode();
        assertEquals(hash1, hash2);
    }

    @Test
    void shouldHaveMeaningfulToString() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo("ABC", "My Page", "https://example.com", true);
        String str = tab.toString();

        assertTrue(str.contains("ABC"), "toString should contain targetId");
        assertTrue(str.contains("My Page"), "toString should contain title");
        assertTrue(str.contains("https://example.com"), "toString should contain url");
        assertTrue(str.contains("true"), "toString should contain attached value");
    }

    @Test
    void shouldHandleSpecialCharactersInFields() {
        CdpSession.TabInfo tab = new CdpSession.TabInfo(
                "id-with-special!@#",
                "Title with unicode: 中文",
                "https://example.com/path?q=hello&lang=zh",
                false
        );

        assertEquals("id-with-special!@#", tab.targetId());
        assertEquals("Title with unicode: 中文", tab.title());
        assertEquals("https://example.com/path?q=hello&lang=zh", tab.url());
        assertFalse(tab.attached());
    }
}
