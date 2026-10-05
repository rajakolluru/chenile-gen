package org.chenile.jgen.cli;

import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Every shipped blueprint must reject generic input errors before domain work or file writes. */
class BlueprintValidationIntegrationTest {
    @TempDir Path temporary;
    private static final Map<String,String> PROJECT_FIELDS = Map.ofEntries(
            Map.entry("chenile-service", "service"), Map.entry("chenile-headless-service", "service"),
            Map.entry("wfservice", "service"), Map.entry("wfcustom", "service"),
            Map.entry("chenile-interceptor", "interceptorName"), Map.entry("mybatisQuery", "namespace"),
            Map.entry("batch", "batch"), Map.entry("it", "app"),
            Map.entry("jgen-blueprint", "blueprintName"), Map.entry("minimonolith", "monolith"),
            Map.entry("chenile-ecosystem", "ecosystem"), Map.entry("chenile-process-management", "application"));

    private Map<String,Object> input(String field) {
        var provider = new ConfigProvider();
        var input = new HashMap<>(provider.getConfigAsMap(provider.obtainDefaultConfig()));
        input.put(field, "example");
        input.put("destFolder", temporary.resolve(field).toString());
        return input;
    }

    private void rejects(String fieldToChange, Object value, String expected) {
        assertTrue(Registry.blueprints.keySet().containsAll(PROJECT_FIELDS.keySet()));
        PROJECT_FIELDS.forEach((name, field) -> {
            var input = input(field); input.put(fieldToChange, value);
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new BlueprintExecutor().execute(Registry.blueprints.get(name),
                            new ConfigProvider().obtainDefaultConfig(), input), name);
            assertTrue(error.getMessage().contains(expected), name + ": " + error.getMessage());
            assertFalse(Files.exists(Path.of(input.get("destFolder").toString())), name);
        });
    }

    @Test void everyBlueprintValidatesPackageNamesBeforeDomainWork() {
        rejects("company", "class", "identifier");
    }

    @Test void everyBlueprintValidatesVersionsBeforeDomainWork() {
        rejects("defaultVersion", "1.0</version>", "Invalid defaultVersion");
    }

    @Test void everyBlueprintValidatesSuppliedRegistryUrlsBeforeDomainWork() {
        rejects("serviceRegistryUrl", "ftp://localhost", "HTTP(S)");
    }

    @Test void everyBlueprintProtectsItsActualOutputDirectory() throws Exception {
        for (var entry : PROJECT_FIELDS.entrySet()) {
            var input = input(entry.getValue());
            String prefix = switch (entry.getKey()) {
                case "it" -> "bdd-it-"; case "jgen-blueprint" -> "bp-"; default -> "";
            };
            Path output = Files.createDirectories(Path.of(input.get("destFolder").toString()).resolve(prefix + "example"));
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new BlueprintExecutor().execute(Registry.blueprints.get(entry.getKey()),
                            new ConfigProvider().obtainDefaultConfig(), input), entry.getKey());
            assertTrue(error.getMessage().contains("already exists"), entry.getKey() + ": " + error.getMessage());
            assertTrue(Files.isDirectory(output));
        }
    }
}
