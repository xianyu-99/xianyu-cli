package com.yucli.runtime.headless;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class JsonlEventWriter implements Closeable, Flushable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Writer writer;

    public JsonlEventWriter(OutputStream outputStream) {
        this(new OutputStreamWriter(Objects.requireNonNull(outputStream, "outputStream"), StandardCharsets.UTF_8));
    }

    public JsonlEventWriter(Writer writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public synchronized void writeResult(HeadlessRunResult result) throws IOException {
        writer.write(toJson(result));
        writer.write(System.lineSeparator());
        writer.flush();
    }

    public static String toJson(HeadlessRunResult result) throws IOException {
        return MAPPER.writeValueAsString(Objects.requireNonNull(result, "result"));
    }

    @Override
    public synchronized void flush() throws IOException {
        writer.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
