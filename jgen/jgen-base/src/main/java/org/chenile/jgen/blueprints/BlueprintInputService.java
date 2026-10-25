package org.chenile.jgen.blueprints;

import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.util.FieldUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlueprintInputService {
    private final ConfigProvider configProvider = new ConfigProvider();

    public Map<String, Object> resolvedDefaults(Config config) {
        return new HashMap<>(configProvider.getConfigAsMap(config));
    }

    /**
     * Resolves and validates submitted inputs, throwing on any error. The checks live in
     * {@link BlueprintPayloadValidator#validateFields}; this adds only the filesystem check for FILE fields.
     * Generic project checks run at the generation boundary ({@code GenericInputValidator}).
     */
    public Map<String, Object> buildInputMap(BlueprintConfig blueprintConfig, Config config, Map<String, Object> submitted) {
        Map<String, Object> result = new HashMap<>(
                BlueprintPayloadValidator.validateFields(blueprintConfig, config, submitted).orThrow());
        if (blueprintConfig.inputFields != null) {
            for (InputField field : blueprintConfig.inputFields) {
                Object value = result.get(field.name);
                if (field.type == FieldType.FILE && value != null && !value.toString().isEmpty()
                        && !new java.io.File(value.toString()).exists()) {
                    throw new IllegalArgumentException("Field " + field.name + " names a file that does not exist: " + value);
                }
            }
        }
        return result;
    }

    public List<Map<String, Object>> describeFields(BlueprintConfig blueprintConfig, Config config) {
        Map<String, Object> defaults = resolvedDefaults(config);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (InputField field : blueprintConfig.inputFields) {
            fields.add(describeField(field, defaults));
        }
        return fields;
    }

    public Object normalizeFieldValue(InputField field, Object value) {
        if (PomInputs.supports(field)) return PomInputs.normalize(field, value, Map.of());
        if (value == null) return null;
        if (field.type == FieldType.BOOLEAN) {
            if (value instanceof Boolean bool) return bool;
            return "y".equalsIgnoreCase(value.toString()) || "true".equalsIgnoreCase(value.toString());
        }
        if (field.type == FieldType.MULTI_SELECT && value instanceof List<?>) {
            return value;
        }
        if (field.type == FieldType.RECORD_ARRAY && value instanceof List<?>) {
            return value;
        }
        return value.toString();
    }

    public boolean isVisible(InputField field, Map<String, Object> values) {
        if (field.visibleWhen == null || field.visibleWhen.isEmpty()) return true;
        for (Map.Entry<String, Object> entry : field.visibleWhen.entrySet()) {
            Object current = values.get(entry.getKey());
            if (current == null || !current.toString().equals(entry.getValue().toString())) return false;
        }
        return true;
    }

    Object resolveSubmittedValue(InputField field, Map<String, Object> values, Map<String, Object> submitted) {
        Object raw = submitted.get(field.name);
        if (raw == null || raw.toString().isBlank()) {
            if (field.type == FieldType.RECORD_ARRAY) return List.of();
            return substitute(values, field.defaultValue);
        }
        if (field.type == FieldType.RECORD_ARRAY) return raw;
        if (field.type == FieldType.MULTI_SELECT && raw instanceof List<?>) return raw;
        return substitute(values, raw.toString());
    }

    private Map<String, Object> describeField(InputField field, Map<String, Object> defaults) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", field.name);
        map.put("description", field.description);
        map.put("helpText", field.helpText);
        map.put("type", field.type);
        map.put("required", FieldUtils.isRequired(field));
        map.put("defaultValue", field.type == FieldType.POM_Dependencies ? List.of() : substitute(defaults, field.defaultValue));
        map.put("validValues", field.validValues);
        map.put("childFields", describeChildFields(PomInputs.childFields(field), defaults));
        map.put("accept", field.accept);
        map.put("maxSizeBytes", field.maxSizeBytes);
        map.put("multiple", Boolean.TRUE.equals(field.multiple));
        map.put("visibleWhen", field.visibleWhen);
        map.put("validation", field.validation);
        return map;
    }

    private List<Map<String, Object>> describeChildFields(List<InputField> childFields, Map<String, Object> defaults) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (InputField child : childFields) {
            fields.add(describeField(child, defaults));
        }
        return fields;
    }

    private String substitute(Map<String, Object> values, String value) {
        if (value == null || value.isEmpty()) return null;
        return new StringSubstitutor(values).replace(value);
    }
}
