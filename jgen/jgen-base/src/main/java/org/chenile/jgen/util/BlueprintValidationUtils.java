package org.chenile.jgen.util;

import org.chenile.jgen.blueprints.BlueprintConfig;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.chenile.jgen.util.JavaNameUtils.identifier;
import static org.chenile.jgen.util.ValidationUtils.require;

/** Common checks run by the processing chain before blueprint hooks or file generation. */
public final class BlueprintValidationUtils {
    private BlueprintValidationUtils() {}

    public static void validateGenericInputs(Map<String,Object> input, BlueprintConfig blueprint) {
        String projectField = blueprint.projectNameField;
        if (projectField == null || projectField.isBlank()) {
            require(blueprint.inputFields != null && !blueprint.inputFields.isEmpty(),
                    "Blueprint must declare projectNameField or inputFields");
            projectField = blueprint.inputFields.get(0).name;
        }
        validateGenericInputs(input, projectField, blueprint.projectDirectoryPrefix, blueprint.registryClientFlag);
    }

    public static void validateGenericInputs(Map<String,Object> input, String projectField) {
        validateGenericInputs(input, projectField, "", null);
    }

    /**
     * The prefix is the template's project-directory prefix. A registry flag is supplied only
     * when this blueprint configures a registry client URL (registration alone need not do so).
     * Domain-specific names, fields, formats and relationships remain in the blueprint.
     */
    public static void validateGenericInputs(Map<String,Object> input, String projectField,
                                             String directoryPrefix, String registryClientFlag) {
        require(input != null, "Blueprint input cannot be null");
        for (String segment : List.of("com", "company", "org")) identifier(text(input, segment));
        String project = text(input, projectField);
        // Existing workflow blueprints sanitize hyphenated names into Java package/variable names.
        require(project.matches("[A-Za-z][A-Za-z0-9_-]*"), "Invalid project name: " + projectField);
        for (var entry : input.entrySet()) {
            if (entry.getKey().endsWith("Version")) {
                require(entry.getValue() instanceof String version && version.matches("[A-Za-z0-9_.-]+"),
                        "Invalid " + entry.getKey());
            }
        }
        boolean registryRequired = registryClientFlag != null && Boolean.TRUE.equals(input.get(registryClientFlag));
        Object registryUrl = input.get("serviceRegistryUrl");
        if (registryRequired || registryUrl != null && !registryUrl.toString().isBlank()) {
            validateHttpUrl(registryUrl, "serviceRegistryUrl");
        }
        Path base = Path.of(text(input, "destFolder")).toAbsolutePath().normalize();
        Path destination = base.resolve(directoryPrefix + project).normalize();
        require(destination.startsWith(base) && !destination.equals(base), "Destination escapes destFolder");
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS), "Destination already exists: " + destination);
        for (Path ancestor = base; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.exists(ancestor)) {
                require(Files.isDirectory(ancestor), "Destination parent is not a directory: " + ancestor);
                break;
            }
        }
    }

    public static void validateHttpUrl(Object value, String field) {
        String message = field + " must be an HTTP(S) URL";
        URI uri;
        try { uri = URI.create(Objects.toString(value, "")); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException(message, e); }
        require(List.of("http", "https").contains(Objects.toString(uri.getScheme(), ""))
                && uri.getHost() != null, message);
    }

    private static String text(Map<String,Object> input, String field) {
        Object value = input.get(field);
        require(value instanceof String && !value.toString().isBlank(), "Missing or invalid " + field);
        return value.toString();
    }
}
