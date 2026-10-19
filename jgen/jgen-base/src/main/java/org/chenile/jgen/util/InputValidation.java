package org.chenile.jgen.util;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.blueprints.model.ValidationType;
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

    public static void validateBlueprint(BlueprintConfig blueprint, Map<String,Object> input) {
        if (blueprint.inputFields != null) validateFields(blueprint.inputFields, input, "");
    }

    private static void validateFields(List<InputField> fields, Map<?,?> values, String prefix) {
        var service = new BlueprintInputService();
        Map<String,Object> visibility = new java.util.HashMap<>();
        values.forEach((k,v) -> visibility.put(k.toString(), v));
        for (InputField field : fields) {
            if (!service.isVisible(field, visibility)) continue;
            validateField(field, values.get(field.name), prefix + field.name);
        }
    }

    private static boolean hasValidation(InputField field) {
        return field.validation != null || field.childFields != null
                && field.childFields.stream().anyMatch(InputValidation::hasValidation);
    }

    public static void validateField(InputField field, Object value, String path) {
        if (!hasValidation(field)) return;
        if (field.validation != null) {
            if (field.type == org.chenile.jgen.blueprints.model.FieldType.MULTI_SELECT && value instanceof String text && !text.isEmpty())
                value = java.util.Arrays.stream(text.split(",", -1)).map(String::trim).toList();
            if (value instanceof List<?> list) {
                if (list.isEmpty() && FieldUtils.isRequired(field))
                    throw new IllegalArgumentException("Field " + path + " is required (" + field.validation + ")");
                for (int i = 0; i < list.size(); i++) validateValue(field, list.get(i), path + "[" + i + "]");
            } else validateValue(field, value, path);
        }
        if (field.childFields == null || value == null) return;
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?,?> record))
                    throw new IllegalArgumentException("Field " + path + "[" + i + "] must be a record");
                validateFields(field.childFields, record, path + "[" + i + "].");
            }
        } else if (value instanceof Map<?,?> record) {
            validateFields(field.childFields, record, path + ".");
        } else throw new IllegalArgumentException("Field " + path + " must contain records");
    }

    private static void validateValue(InputField field, Object value, String path) {
        if (value == null || value instanceof String text && text.isEmpty()) {
            if (FieldUtils.isRequired(field)) throw new IllegalArgumentException("Field " + path + " is required (" + field.validation + ")");
            return;
        }
        if (!(value instanceof String text) || !isValid(field.validation, text))
            throw new IllegalArgumentException("Field " + path + " must satisfy " + field.validation);
    }
}
