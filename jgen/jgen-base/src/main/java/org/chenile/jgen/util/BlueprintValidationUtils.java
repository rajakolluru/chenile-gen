package org.chenile.jgen.util;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.PayloadIssue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.chenile.jgen.util.JavaNameUtils.identifier;
import static org.chenile.jgen.util.ValidationUtils.require;

/** Common checks run by the processing chain before blueprint hooks or file generation. */
public final class BlueprintValidationUtils {
    private BlueprintValidationUtils() {}

    public static void validateGenericInputs(Map<String,Object> input, BlueprintConfig blueprint) {
        validateGenericInputs(input, projectField(blueprint), blueprint.projectDirectoryPrefix, blueprint.registryClientFlag);
    }

    public static void validateGenericInputs(Map<String,Object> input, String projectField) {
        validateGenericInputs(input, projectField, "", null);
    }

    /**
     * The prefix is the template's project-directory prefix. A registry flag is supplied only
     * when this blueprint configures a registry client URL (registration alone need not do so).
     * Domain-specific names, fields, formats and relationships remain in the blueprint.
     * Payload checks come first (see {@link #collectGenericPayload}); the destination checks that need the
     * filesystem run only when the payload is clean.
     */
    public static void validateGenericInputs(Map<String,Object> input, String projectField,
                                             String directoryPrefix, String registryClientFlag) {
        require(input != null, "Blueprint input cannot be null");
        List<PayloadIssue> issues = new ArrayList<>();
        collectGenericPayload(input, projectField, registryClientFlag, issues);
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.get(0).message());
        String project = text(input, projectField);
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

    /** Collect-all, filesystem-free form of the generic checks, for payload validation. */
    public static void collectGenericPayload(Map<String,Object> input, BlueprintConfig blueprint, List<PayloadIssue> issues) {
        String projectField;
        try { projectField = projectField(blueprint); }
        catch (IllegalArgumentException e) { issues.add(issue("$", e.getMessage())); return; }
        collectGenericPayload(input, projectField, blueprint.registryClientFlag, issues);
    }

    /**
     * Package segments ({@code com}, {@code company}, {@code org}) must be Java identifiers, the project name must
     * be a safe directory name, every {@code *Version} must look like a version, and a registry URL, when
     * required or given, must be HTTP(S). Appends one issue per failing check.
     */
    public static void collectGenericPayload(Map<String,Object> input, String projectField, String registryClientFlag,
                                             List<PayloadIssue> issues) {
        for (String segment : List.of("com", "company", "org")) {
            check(issues, segment, () -> identifier(text(input, segment)));
        }
        check(issues, projectField, () -> {
            String project = text(input, projectField);
            // Existing workflow blueprints sanitize hyphenated names into Java package/variable names.
            require(project.matches("[A-Za-z][A-Za-z0-9_-]*"), "Invalid project name: " + projectField);
        });
        for (var entry : input.entrySet()) {
            if (entry.getKey().endsWith("Version")) {
                String pattern = entry.getKey().equals("parentVersion") ? "[A-Za-z0-9_.$}{-]+" : "[A-Za-z0-9_.-]+";
                check(issues, entry.getKey(), () -> require(entry.getValue() instanceof String version
                        && version.matches(pattern), "Invalid " + entry.getKey()));
            }
        }
        boolean registryRequired = registryClientFlag != null && Boolean.TRUE.equals(input.get(registryClientFlag));
        Object registryUrl = input.get("serviceRegistryUrl");
        if (registryRequired || registryUrl != null && !registryUrl.toString().isBlank()) {
            check(issues, "serviceRegistryUrl", () -> validateHttpUrl(registryUrl, "serviceRegistryUrl"));
        }
    }

    private static String projectField(BlueprintConfig blueprint) {
        String projectField = blueprint.projectNameField;
        if (projectField == null || projectField.isBlank()) {
            require(blueprint.inputFields != null && !blueprint.inputFields.isEmpty(),
                    "Blueprint must declare projectNameField or inputFields");
            projectField = blueprint.inputFields.get(0).name;
        }
        return projectField;
    }

    private static void check(List<PayloadIssue> issues, String path, Runnable rule) {
        try { rule.run(); }
        catch (IllegalArgumentException e) { issues.add(issue(path, e.getMessage())); }
    }

    private static PayloadIssue issue(String path, String message) {
        return new PayloadIssue(path, PayloadIssue.INVALID_INPUT, message);
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
