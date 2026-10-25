package org.chenile.jgen.util;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.PayloadIssue;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.blueprints.model.ValidationType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared declarative validation for every input path, including composed blueprints. */
public final class InputValidation {
    private InputValidation() { }

    public static boolean isValid(ValidationType rule, String value) {
        if (rule == null) return true;
        try { validate(rule, value); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    public static void validate(ValidationType rule, String value) {
        switch (rule) {
            case JAVA_IDENTIFIER -> JavaNameUtils.identifier(value);
            case JAVA_TYPE -> JavaNameUtils.upperIdentifier(value);
            case JAVA_PACKAGE -> {
                for (String segment : value.split("\\.", -1)) JavaNameUtils.identifier(segment);
            }
            case PROJECT_NAME -> ValidationUtils.require(value.matches("[A-Za-z][A-Za-z0-9_-]*"), "Invalid project name");
            case VERSION -> ValidationUtils.require(value.matches("[A-Za-z0-9_.-]+"), "Invalid version");
            case HTTP_URL -> BlueprintValidationUtils.validateHttpUrl(value, "Value");
            case KAFKA_TOPIC -> ValidationUtils.require(value.matches("[A-Za-z0-9._-]{1,249}")
                    && !value.equals(".") && !value.equals(".."), "Invalid Kafka topic");
        }
    }

    /** Fail-fast form: throws on the first problem. Kept for the execution boundary and older callers. */
    public static void validateBlueprint(BlueprintConfig blueprint, Map<String,Object> input) {
        List<PayloadIssue> issues = new ArrayList<>();
        collectBlueprint(blueprint, input, issues);
        throwFirst(issues);
    }

    /** Collect-all form: appends one issue per invalid field (or list element) to {@code issues}. */
    public static void collectBlueprint(BlueprintConfig blueprint, Map<String,Object> input, List<PayloadIssue> issues) {
        if (blueprint.inputFields != null) collectFields(blueprint.inputFields, input, "", issues);
    }

    private static void collectFields(List<InputField> fields, Map<?,?> values, String prefix, List<PayloadIssue> issues) {
        var service = new BlueprintInputService();
        Map<String,Object> visibility = new HashMap<>();
        values.forEach((k,v) -> visibility.put(k.toString(), v));
        for (InputField field : fields) {
            if (!service.isVisible(field, visibility)) continue;
            collectField(field, values.get(field.name), prefix + field.name, issues);
        }
    }

    private static boolean hasValidation(InputField field) {
        return field.validation != null || field.childFields != null
                && field.childFields.stream().anyMatch(InputValidation::hasValidation);
    }

    /** Fail-fast form of {@link #collectField}. */
    public static void validateField(InputField field, Object value, String path) {
        List<PayloadIssue> issues = new ArrayList<>();
        collectField(field, value, path, issues);
        throwFirst(issues);
    }

    /**
     * Checks a field's declarative {@code validation} rule, and those of its child fields, appending one issue per
     * failing value. Fields with no rules anywhere in their tree are not checked here.
     */
    public static void collectField(InputField field, Object value, String path, List<PayloadIssue> issues) {
        if (!hasValidation(field)) return;
        if (field.validation != null) {
            if (field.type == FieldType.MULTI_SELECT && value instanceof String text && !text.isEmpty())
                value = Arrays.stream(text.split(",", -1)).map(String::trim).toList();
            if (value instanceof List<?> list) {
                if (list.isEmpty() && FieldUtils.isRequired(field))
                    issues.add(new PayloadIssue(path, PayloadIssue.REQUIRED,
                            "Field " + path + " is required (" + field.validation + ")"));
                for (int i = 0; i < list.size(); i++) collectValue(field, list.get(i), path + "[" + i + "]", issues);
            } else collectValue(field, value, path, issues);
        }
        if (field.childFields == null || value == null) return;
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?,?> record)) {
                    issues.add(new PayloadIssue(path + "[" + i + "]", PayloadIssue.INVALID_VALUE,
                            "Field " + path + "[" + i + "] must be a record"));
                    continue;
                }
                collectFields(field.childFields, record, path + "[" + i + "].", issues);
            }
        } else if (value instanceof Map<?,?> record) {
            collectFields(field.childFields, record, path + ".", issues);
        } else issues.add(new PayloadIssue(path, PayloadIssue.INVALID_VALUE, "Field " + path + " must contain records"));
    }

    private static void collectValue(InputField field, Object value, String path, List<PayloadIssue> issues) {
        if (value == null || value instanceof String text && text.isEmpty()) {
            if (FieldUtils.isRequired(field))
                issues.add(new PayloadIssue(path, PayloadIssue.REQUIRED,
                        "Field " + path + " is required (" + field.validation + ")"));
            return;
        }
        if (!(value instanceof String text) || !isValid(field.validation, text))
            issues.add(new PayloadIssue(path, PayloadIssue.INVALID_VALUE,
                    "Field " + path + " must satisfy " + field.validation));
    }

    private static void throwFirst(List<PayloadIssue> issues) {
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.get(0).message());
    }
}
