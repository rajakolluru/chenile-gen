package org.chenile.jgen.blueprints;

import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.util.FieldUtils;
import java.util.*;

/** Shared structured Maven input contract. Each blueprint owns where its templates emit dependencies. */
public final class PomInputs {
    private PomInputs() { }

    public static boolean supports(InputField field) {
        return field.type == FieldType.SuperPOM || field.type == FieldType.POM_Dependencies;
    }

    /** Old parent coordinates remain accepted by input-file, portal, and programmatic callers. */
    public static Object suppliedValue(InputField field, Map<String, Object> supplied) {
        Object value = supplied.get(field.name);
        if (value != null) return value;
        if (field.type == FieldType.SuperPOM && "super_pom".equals(field.name)
                && supplied.get("parentArtifactId") != null) {
            Map<String, Object> parent = new LinkedHashMap<>();
            for (String coordinate : List.of("groupId", "artifactId", "version", "relativePath")) {
                String legacy = "parent" + Character.toUpperCase(coordinate.charAt(0)) + coordinate.substring(1);
                if (supplied.containsKey(legacy)) parent.put(coordinate, supplied.get(legacy));
            }
            return parent;
        }
        return null;
    }

    public static Object normalize(InputField field, Object value, Map<String, Object> variables) {
        if (field.type == FieldType.SuperPOM) {
            if (value == null || value instanceof Map<?, ?> map && map.isEmpty()) {
                if (FieldUtils.isRequired(field)) throw invalid(field, "is required");
                return null;
            }
            if (!(value instanceof Map<?, ?> raw)) throw invalid(field, "must be a parent coordinate object");
            Map<String, Object> parent = new LinkedHashMap<>();
            for (String key : List.of("groupId", "artifactId", "version"))
                parent.put(key, coordinate(field, raw, key, null, variables, true));
            Object relative = raw.containsKey("relativePath") ? raw.get("relativePath") : "../pom.xml";
            if (!(relative instanceof String path)) throw invalid(field, "relativePath must be a string");
            parent.put("relativePath", substitute(path, variables)); // Empty means Maven repository lookup only.
            return parent;
        }
        if (field.type != FieldType.POM_Dependencies) throw invalid(field, "is not a Maven input type");
        if (value == null) value = List.of();
        if (!(value instanceof List<?> entries)) throw invalid(field, "must be an array of dependency objects");
        if (entries.isEmpty() && FieldUtils.isRequired(field)) throw invalid(field, "requires at least one dependency");
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> raw)) throw invalid(field, "must contain only dependency objects");
            Map<String, Object> dependency = new LinkedHashMap<>();
            String group = coordinate(field, raw, "groupId", "dependencyGroup", variables, true);
            String artifact = coordinate(field, raw, "artifactId", "dependencyName", variables, true);
            String version = coordinate(field, raw, "version", "dependencyVersion", variables, false);
            dependency.put("groupId", group); dependency.put("artifactId", artifact);
            // Preserve legacy template consumers as well as legacy input files.
            dependency.put("dependencyGroup", group); dependency.put("dependencyName", artifact);
            if (version != null) { dependency.put("version", version); dependency.put("dependencyVersion", version); }
            dependencies.add(dependency);
        }
        return dependencies;
    }

    /** Also runs at the execution boundary, so composers cannot bypass validation and normalization. */
    public static void normalizeBlueprintInputs(BlueprintConfig blueprint, Map<String, Object> input) {
        if (blueprint.inputFields == null) return;
        BlueprintInputService inputService = new BlueprintInputService();
        for (InputField field : blueprint.inputFields) {
            if (!supports(field) || !inputService.isVisible(field, input)) continue;
            Object value = normalize(field, suppliedValue(field, input), input);
            if (value == null) input.remove(field.name);
            else input.put(field.name, value);
            if (field.type == FieldType.SuperPOM && "super_pom".equals(field.name) && value instanceof Map<?, ?> parent) {
                for (String key : List.of("groupId", "artifactId", "version", "relativePath"))
                    input.put("parent" + Character.toUpperCase(key.charAt(0)) + key.substring(1), parent.get(key));
            }
        }
    }

    public static List<InputField> childFields(InputField field) {
        if (!supports(field)) return field.childFields == null ? List.of() : field.childFields;
        List<InputField> fields = new ArrayList<>();
        for (String name : List.of("groupId", "artifactId", "version")) {
            InputField child = new InputField(); child.name = name; child.description = name;
            child.type = FieldType.STRING;
            child.required = !"version".equals(name) || field.type == FieldType.SuperPOM;
            fields.add(child);
        }
        if (field.type == FieldType.SuperPOM) {
            InputField path = new InputField(); path.name = "relativePath"; path.description = "relativePath";
            path.type = FieldType.STRING; path.required = false;
            fields.add(path);
        }
        return fields;
    }

    private static String coordinate(InputField field, Map<?, ?> raw, String key, String legacy,
            Map<String, Object> variables, boolean required) {
        Object value = raw.get(key);
        if (value == null && legacy != null) value = raw.get(legacy);
        if (value == null || value instanceof String text && text.isBlank()) {
            if (required) throw invalid(field, key + " is required");
            return null;
        }
        if (!(value instanceof String text)) throw invalid(field, key + " must be a string");
        String resolved = substitute(text, variables).trim();
        if (resolved.isBlank() || resolved.chars().anyMatch(Character::isWhitespace))
            throw invalid(field, "invalid " + key);
        return resolved;
    }
    private static String substitute(String value, Map<String, Object> variables) {
        return new StringSubstitutor(variables).replace(value);
    }
    private static IllegalArgumentException invalid(InputField field, String message) {
        return new IllegalArgumentException("Field " + field.name + " " + message);
    }
}
