package org.chenile.jgen.portal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.function.Consumer;

public class OperationLog implements AutoCloseable {
    private final OutputStream outputStream;
    private final Consumer<String> broadcaster;

    public OperationLog(Path logFile, Consumer<String> broadcaster) throws IOException {
        Files.createDirectories(logFile.getParent());
        this.outputStream = Files.newOutputStream(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        this.broadcaster = broadcaster;
    }

    public synchronized void line(String level, String message) {
        String entry = Instant.now() + " [" + level + "] " + message;
        try {
            outputStream.write((entry + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        broadcaster.accept(entry);
    }

    public OutputStream asOutputStream(String level) {
        return new LineOutputStream(line -> line(level, line));
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    private static class LineOutputStream extends OutputStream {
        private final Consumer<String> lineConsumer;
        private final StringBuilder buffer = new StringBuilder();

        private LineOutputStream(Consumer<String> lineConsumer) {
            this.lineConsumer = lineConsumer;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                flushBuffer();
                return;
            }
            if (b != '\r') buffer.append((char) b);
        }

        @Override
        public void flush() {
            flushBuffer();
        }

        private void flushBuffer() {
            if (buffer.isEmpty()) return;
            lineConsumer.accept(buffer.toString());
            buffer.setLength(0);
        }
    }
}
