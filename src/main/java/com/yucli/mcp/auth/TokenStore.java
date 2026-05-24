package com.yucli.mcp.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TokenStore {
    private final File storageFile;
    private final ObjectMapper mapper;
    private final Map<String, TokenEntry> tokens;

    public TokenStore() {
        this(resolveStorageDir());
    }

    public TokenStore(File storageDir) {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.tokens = new ConcurrentHashMap<>();
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.storageFile = new File(storageDir, "mcp-tokens.json");
        loadFromDisk();
    }

    public TokenEntry getToken(String serverName) {
        return tokens.get(serverName);
    }

    public void saveToken(String serverName, TokenEntry token) {
        tokens.put(serverName, token);
        saveToDisk();
    }

    public void removeToken(String serverName) {
        tokens.remove(serverName);
        saveToDisk();
    }

    public boolean hasValidToken(String serverName) {
        TokenEntry entry = tokens.get(serverName);
        if (entry == null || entry.accessToken() == null || entry.accessToken().isBlank()) {
            return false;
        }
        if (entry.expiresAtEpochSeconds() <= 0) {
            return true;
        }
        return Instant.now().getEpochSecond() < entry.expiresAtEpochSeconds() - 60;
    }

    private void saveToDisk() {
        try {
            mapper.writeValue(storageFile, tokens);
        } catch (IOException e) {
            System.err.println("OAuth token 持久化失败: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!storageFile.exists()) return;
        try {
            Map<String, TokenEntry> loaded = mapper.readValue(storageFile,
                    new TypeReference<Map<String, TokenEntry>>() {});
            if (loaded != null) {
                tokens.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("加载 OAuth token 失败: " + e.getMessage());
        }
    }

    private static File resolveStorageDir() {
        File home = new File(System.getProperty("user.home"));
        return new File(home, ".YuCLI");
    }

    public record TokenEntry(
            String accessToken,
            String refreshToken,
            long expiresAtEpochSeconds
    ) {}
}
