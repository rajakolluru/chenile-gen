package org.chenile.jgen.remote;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code ~/.jgen/settings.json} (which sources are trusted) and {@code ~/.jgen/blueprints.lock.json} (the SHA-256
 * first seen for each fetched blueprint version).
 */
public class JgenSettings {
    /** Trusted out of the box: the official Chenile organisations. */
    public static final List<String> DEFAULT_TRUSTED = List.of("github:ajapros/*", "github:rajakolluru/*");

    static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** Patterns: github:owner/* or github:owner/repo. */
    public List<String> trustedSources = new ArrayList<>(DEFAULT_TRUSTED);

    public static Path home() {
        String override = System.getenv("JGEN_HOME_DIR");
        return override != null && !override.isBlank() ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".jgen");
    }

    public static JgenSettings load(Path home) throws IOException {
        Path file = home.resolve("settings.json");
        return Files.isRegularFile(file) ? MAPPER.readValue(file.toFile(), JgenSettings.class) : new JgenSettings();
    }

    public void save(Path home) throws IOException {
        Files.createDirectories(home);
        MAPPER.writeValue(home.resolve("settings.json").toFile(), this);
    }

    public boolean trusts(BlueprintReference reference) {
        String repo = "github:" + reference.repository();
        String owner = "github:" + reference.owner() + "/*";
        return trustedSources.stream().anyMatch(p -> p.equalsIgnoreCase(repo) || p.equalsIgnoreCase(owner));
    }

    public void trust(BlueprintReference reference) {
        String repo = "github:" + reference.repository();
        if (!trusts(reference)) trustedSources.add(repo);
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> loadLock(Path home) throws IOException {
        Path file = home.resolve("blueprints.lock.json");
        return Files.isRegularFile(file) ? new TreeMap<>(MAPPER.readValue(file.toFile(), Map.class)) : new TreeMap<>();
    }

    static void saveLock(Path home, Map<String, String> lock) throws IOException {
        Files.createDirectories(home);
        MAPPER.writeValue(home.resolve("blueprints.lock.json").toFile(), new TreeMap<>(lock));
    }
}
