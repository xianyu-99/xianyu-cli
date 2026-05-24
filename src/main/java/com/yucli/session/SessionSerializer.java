package com.yucli.session;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SessionSerializer {
    private static final String STORAGE_DIR_NAME = "sessions";
    private final ObjectMapper mapper;
    private final File storageDir;

    public SessionSerializer() {
        this(resolveStorageDir());
    }

    public SessionSerializer(File storageDir) {
        this.storageDir = storageDir;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }

    public void save(Session session) {
        File file = new File(storageDir, session.getSessionId() + ".json");
        try {
            mapper.writeValue(file, session);
        } catch (IOException e) {
            System.err.println("保存会话失败: " + e.getMessage());
        }
    }

    public Session load(String sessionId) {
        File file = new File(storageDir, sessionId + ".json");
        if (!file.exists()) {
            return null;
        }
        try {
            return mapper.readValue(file, Session.class);
        } catch (IOException e) {
            System.err.println("加载会话失败: " + e.getMessage());
            return null;
        }
    }

    public List<Session> listAll() {
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(files)
                .map(file -> {
                    try {
                        return mapper.readValue(file, Session.class);
                    } catch (IOException e) {
                        return null;
                    }
                })
                .filter(s -> s != null)
                .sorted((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    public boolean delete(String sessionId) {
        File file = new File(storageDir, sessionId + ".json");
        return file.exists() && file.delete();
    }

    public File getStorageDir() {
        return storageDir;
    }

    private static File resolveStorageDir() {
        return new File(new File(System.getProperty("user.home"), ".YuCLI"), STORAGE_DIR_NAME);
    }
}
