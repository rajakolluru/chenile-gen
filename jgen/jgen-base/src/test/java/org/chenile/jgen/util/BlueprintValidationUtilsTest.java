package org.chenile.jgen.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.chenile.jgen.util.BlueprintValidationUtils.validateGenericInputs;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintValidationUtilsTest {
    @TempDir Path temporary;

    private Map<String,Object> input() {
        return new HashMap<>(Map.of("com", "com", "company", "example", "org", "demo",
                "service", "order-item", "serviceVersion", "0.0.1-SNAPSHOT",
                "destFolder", temporary.toString()));
    }

    @Test void acceptsExistingNamingConventionsAndDoesNotCreateFiles() {
        var input = input();
        assertDoesNotThrow(() -> validateGenericInputs(input, "service"));
        assertFalse(Files.exists(temporary.resolve("order-item")));
    }

    @Test void rejectsUnsafeProjectNamesAndPackageSegments() {
        for (String name : new String[]{"", "../escape", "/absolute", "a/b", "a\\b", "a<b", "a b"}) {
            var input = input(); input.put("service", name);
            assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input, "service"));
        }
        var input = input(); input.put("company", "class");
        assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input, "service"));
        input.remove("com");
        assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input, "service"));
    }

    @Test void validatesAllProvidedVersionFields() {
        for (String field : new String[]{"serviceVersion", "chenileVersion", "chenileBddVersion", "parentVersion"}) {
            var input = input(); input.put(field, "1.0</version>");
            var error = assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input, "service"));
            assertEquals("Invalid " + field, error.getMessage());
        }
    }

    @Test void rejectsExistingOutputsWithoutChangingThem() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("order-item"));
        Path marker = Files.writeString(project.resolve("keep.txt"), "unchanged");
        assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input(), "service"));
        assertEquals("unchanged", Files.readString(marker));
    }

    @Test void checksActualPrefixedOutputDirectory() throws Exception {
        Files.createDirectory(temporary.resolve("bp-order-item"));
        assertThrows(IllegalArgumentException.class,
                () -> validateGenericInputs(input(), "service", "bp-", null));
        assertDoesNotThrow(() -> validateGenericInputs(input(), "service", "bdd-it-", null));
    }

    @Test void rejectsFileParentsAndAcceptsNotYetCreatedParents() throws Exception {
        var input = input();
        Path file = Files.writeString(temporary.resolve("not-a-directory"), "keep");
        input.put("destFolder", file.resolve("nested").toString());
        assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input, "service"));
        input.put("destFolder", temporary.resolve("new/nested").toString());
        assertDoesNotThrow(() -> validateGenericInputs(input, "service"));
        assertFalse(Files.exists(temporary.resolve("new")));
    }

    @Test void validatesRegistryClientUrlsOnlyWhenRequiredOrSupplied() {
        var input = input(); input.put("registerInServiceRegistry", true);
        // Headless service registration is resolved by its host, not a blueprint URL.
        assertDoesNotThrow(() -> validateGenericInputs(input, "service"));
        assertThrows(IllegalArgumentException.class,
                () -> validateGenericInputs(input, "service", "", "registerInServiceRegistry"));
        for (String url : new String[]{"ftp://example.com", "http://", "http://bad host"}) {
            input.put("serviceRegistryUrl", url);
            assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input, "service"));
        }
        input.put("serviceRegistryUrl", "https://registry.example.com/api");
        assertDoesNotThrow(() -> validateGenericInputs(input, "service", "", "registerInServiceRegistry"));
    }

    @Test void rejectsBrokenSymlinkOutputs() throws Exception {
        Files.createSymbolicLink(temporary.resolve("order-item"), temporary.resolve("missing"));
        assertThrows(IllegalArgumentException.class, () -> validateGenericInputs(input(), "service"));
    }
}
