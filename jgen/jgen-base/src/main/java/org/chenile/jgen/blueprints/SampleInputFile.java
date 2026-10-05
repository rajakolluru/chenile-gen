package org.chenile.jgen.blueprints;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Blueprint-owned sample content; the framework supplies its destination filename. */
public record SampleInputFile(String extension, Emitter emitter) {
    @FunctionalInterface
    public interface Emitter {
        void emit(Path destination) throws IOException;
    }

    public static SampleInputFile resource(String extension, Class<?> owner, String resource) {
        return new SampleInputFile(extension, destination -> {
            try (var stream = owner.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing sample resource: " + resource);
                Files.copy(stream, destination);
            }
        });
    }
}
