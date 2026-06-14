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

    public Map<String, Object> buildInputMap(BlueprintConfig blueprintConfig, Config config, Map<String, Object> submitted) {
        Map<String, Object> result = resolvedDefaults(config);
        Map<String, Object> source = submitted == null ? Map.of() : submitted;
        for (InputField field : blueprintConfig.inputFields) {
            if (!isVisible(field, result)) continue;
            Object value = normalizeFieldValue(field, resolveSubmittedValue(field, result, source));
            validateValue(field, value);
            if (value != null) result.put(field.name, value);
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

    private Object resolveSubmittedValue(InputField field, Map<String, Object> values, Map<String, Object> submitted) {
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
        map.put("defaultValue", substitute(defaults, field.defaultValue));
        map.put("validValues", field.validValues);
        map.put("childFields", field.childFields == null ? List.of() : describeChildFields(field.childFields, defaults));
        map.put("accept", field.accept);
        map.put("maxSizeBytes", field.maxSizeBytes);
        map.put("multiple", Boolean.TRUE.equals(field.multiple));
        map.put("visibleWhen", field.visibleWhen);
        return map;
    }

    private List<Map<String, Object>> describeChildFields(List<InputField> childFields, Map<String, Object> defaults) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (InputField child : childFields) {
            fields.add(describeField(child, defaults));
        }
        return fields;
    }

    private void validateValue(InputField field, Object value) {
        if (field.type == FieldType.RECORD_ARRAY) {
            if (value == null) return;
            if (!(value instanceof List<?> records)) {
                throw new IllegalArgumentException("Field " + field.name + " must be a list of records.");
            }
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object record : records) {
                if (!(record instanceof Map<?, ?> rawMap)) {
                    throw new IllegalArgumentException("Field " + field.name + " must contain only record objects.");
                }
                Map<String, Object> converted = new HashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    converted.put(entry.getKey().toString(), entry.getValue());
                }
                normalized.add(converted);
            }
            if (!FieldUtils.isValidRecordArray(field, normalized)) {
                throw new IllegalArgumentException("Field " + field.name + " has invalid record values.");
            }
            return;
        }
        if (field.type == FieldType.MULTI_SELECT && value instanceof List<?> list) {
            for (Object item : list) {
                if (!FieldUtils.isValid(field, item == null ? null : item.toString())) {
                    throw new IllegalArgumentException("Field " + field.name + " has an invalid value.");
                }
            }
            return;
        }
        if (!FieldUtils.isValid(field, value == null ? null : value.toString())) {
            throw new IllegalArgumentException("Field " + field.name + " has an invalid value.");
        }
    }

    private String substitute(Map<String, Object> values, String value) {
        if (value == null || value.isEmpty()) return null;
        return new StringSubstitutor(values).replace(value);
    }
}
