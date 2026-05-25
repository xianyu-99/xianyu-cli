package com.yucli.mcp.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TokenStoreTest {

    @TempDir
    Path tempDir;

    private TokenStore store;

    @BeforeEach
    void setUp() {
        store = new TokenStore(tempDir.toFile());
    }

    private static long futureEpoch() {
        return Instant.now().getEpochSecond() + 3600;
    }

    private static long pastEpoch() {
        return Instant.now().getEpochSecond() - 10;
    }

    @Test
    void saveAndGetToken() {
        TokenStore.TokenEntry entry = new TokenStore.TokenEntry("access123", "refresh456", futureEpoch());
        store.saveToken("server1", entry);

        TokenStore.TokenEntry got = store.getToken("server1");
        assertNotNull(got);
        assertEquals("access123", got.accessToken());
        assertEquals("refresh456", got.refreshToken());
    }

    @Test
    void hasValidTokenReturnsTrueForFreshToken() {
        TokenStore.TokenEntry entry = new TokenStore.TokenEntry("access", "refresh", futureEpoch());
        store.saveToken("s1", entry);
        assertTrue(store.hasValidToken("s1"));
    }

    @Test
    void hasValidTokenReturnsFalseForExpiredToken() {
        TokenStore.TokenEntry entry = new TokenStore.TokenEntry("access", "refresh", pastEpoch());
        store.saveToken("s1", entry);
        assertFalse(store.hasValidToken("s1"));
    }

    @Test
    void hasValidTokenReturnsFalseForMissingToken() {
        assertFalse(store.hasValidToken("nonexistent"));
    }

    @Test
    void hasValidTokenReturnsFalseForNullAccessToken() {
        TokenStore.TokenEntry entry = new TokenStore.TokenEntry(null, "refresh", futureEpoch());
        store.saveToken("s1", entry);
        assertFalse(store.hasValidToken("s1"));
    }

    @Test
    void removeToken() {
        TokenStore.TokenEntry entry = new TokenStore.TokenEntry("access", "refresh", futureEpoch());
        store.saveToken("s1", entry);
        store.removeToken("s1");
        assertNull(store.getToken("s1"));
    }

    @Test
    void persistsAcrossInstances() {
        TokenStore.TokenEntry entry = new TokenStore.TokenEntry("access", "refresh", futureEpoch());
        store.saveToken("s1", entry);

        TokenStore reloaded = new TokenStore(tempDir.toFile());
        TokenStore.TokenEntry got = reloaded.getToken("s1");
        assertNotNull(got);
        assertEquals("access", got.accessToken());
        assertEquals("refresh", got.refreshToken());
    }

    @Test
    void serializationPreservesExpiresAt() {
        long expires = Instant.parse("2026-12-31T23:59:59Z").getEpochSecond();
        TokenStore.TokenEntry entry = new TokenStore.TokenEntry("access", "refresh", expires);
        store.saveToken("s1", entry);

        TokenStore reloaded = new TokenStore(tempDir.toFile());
        TokenStore.TokenEntry got = reloaded.getToken("s1");
        assertNotNull(got);
        assertEquals(expires, got.expiresAtEpochSeconds());
    }

    @Test
    void multipleServersStoredIndependently() {
        store.saveToken("s1", new TokenStore.TokenEntry("a1", "r1", futureEpoch()));
        store.saveToken("s2", new TokenStore.TokenEntry("a2", "r2", futureEpoch()));

        assertEquals("a1", store.getToken("s1").accessToken());
        assertEquals("a2", store.getToken("s2").accessToken());
    }
}
