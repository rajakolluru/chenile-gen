package org.chenile.jgen.blueprints;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.util.BlueprintValidationUtils;
import org.chenile.jgen.util.FieldUtils;
import org.chenile.jgen.util.InputValidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single validator for blueprint input payloads. Every input path uses it: the CLI's {@code -f} file, the
 * portal, composed child blueprints (through {@link BlueprintInputService#buildInputMap}) and external callers
 * such as ChenAIle.
 *
 * <p>It resolves a payload exactly as generation will (config defaults, {@code ${...}} substitution,
 * {@code visibleWhen}, boolean and Maven-coordinate normalization) and checks:</p>
 * <ul>
 *   <li>the JSON syntax, and that the payload is a JSON object (for the {@link #validate(BlueprintConfig, Config, String)} form);</li>
 *   <li>required fields, counting only visible ones;</li>
 *   <li>each field's type and shape (booleans, numbers, dates, dropdown and multi-select values, record arrays,
 *       Maven parent and dependency objects);</li>
 *   <li>declarative {@code validation} rules, including nested record fields;</li>
 *   <li>the generic inputs every blueprint shares: package segments, project name, versions, registry URL
 *       (skipped by {@link #validateFields}).</li>
 * </ul>
 * <p>All problems are reported, not just the first. Keys the blueprint does not declare are warnings, not
 * errors: they are ignored by generation (config values come from the config file, not the payload), and input
 * files carry a {@code blueprint} key, which is never warned about.</p>
 *
 * <p>It never touches the filesystem. Checks that do, such as "the destination folder must not exist" and
 * "a FILE field's path must exist", stay at the generation boundary.</p>
 */
public final class BlueprintPayloadValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Set<String> CONTROL_KEYS = Set.of("blueprint");
    private static final List<String> LEGACY_PARENT_KEYS =
            List.of("parentGroupId", "parentArtifactId", "parentVersion", "parentRelativePath");

    private BlueprintPayloadValidator() { }

    /** Validates a JSON payload against a blueprint, using the default JGen config. */
    public static PayloadValidation validate(BlueprintConfig blueprint, String json) {
        return validate(blueprint, null, json);
    }

    /** Validates a JSON payload against a blueprint and config ({@code null} means the default config). */
    public static PayloadValidation validate(BlueprintConfig blueprint, Config config, String json) {
        if (json == null || json.isBlank()) {
            return failed(new PayloadIssue("$", PayloadIssue.INVALID_JSON, "Input is empty; expected a JSON object"));
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return failed(new PayloadIssue("$", PayloadIssue.INVALID_JSON, "Input is not valid JSON: " + describe(e)));
        }
        if (node == null || !node.isObject()) {
            String kind = node == null ? "nothing" : node.getNodeType().name().toLowerCase();
            return failed(new PayloadIssue("$", PayloadIssue.NOT_AN_OBJECT,
                    "Input must be a JSON object of field values, not " + kind));
        }
        Map<String, Object> payload = MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() { });
        return validate(blueprint, config, payload);
    }

    /** Validates an already-parsed payload against a blueprint and config ({@code null} means the default config). */
    public static PayloadValidation validate(BlueprintConfig blueprint, Config config, Map<String, Object> payload) {
        return validate(blueprint, config, payload, true);
    }

    /**
     * Field checks only, without the generic project checks. Used by {@link BlueprintInputService#buildInputMap},
     * which resolves inputs (including composed child inputs) before the generation boundary runs the generic and
     * filesystem checks on the final map.
     */
    public static PayloadValidation validateFields(BlueprintConfig blueprint, Config config, Map<String, Object> payload) {
        return validate(blueprint, config, payload, false);
    }

    private static PayloadValidation validate(BlueprintConfig blueprint, Config config, Map<String, Object> payload,
                                              boolean includeGeneric) {
        BlueprintInputService service = new BlueprintInputService();
        Config effective = config == null ? new ConfigProvider().obtainDefaultConfig() : config;
        Map<String, Object> defaults = service.resolvedDefaults(effective);
        Map<String, Object> result = new HashMap<>(defaults);
        Map<String, Object> source = payload == null ? Map.of() : payload;
        List<InputField> fields = blueprint.inputFields == null ? List.of() : blueprint.inputFields;
        List<PayloadIssue> errors = new ArrayList<>();
        List<PayloadIssue> warnings = new ArrayList<>();

        for (InputField field : fields) {
            if (!service.isVisible(field, result)) {
                if (source.containsKey(field.name)) {
                    warnings.add(new PayloadIssue(field.name, PayloadIssue.NOT_VISIBLE,
                            "Field " + field.name + " is ignored because its condition " + field.visibleWhen
                                    + " is not met"));
                }
                continue;
            }
            Object value;
            try {
                if (PomInputs.supports(field)) {
                    value = PomInputs.normalize(field, PomInputs.suppliedValue(field, source), result);
                } else {
                    Object resolved = service.resolveSubmittedValue(field, result, source);
                    if (field.type == FieldType.BOOLEAN && resolved != null && !(resolved instanceof Boolean)
                            && !FieldUtils.isValid(field, resolved.toString(), false)) {
                        // Normalization would silently turn any other text into false.
                        errors.add(invalid(field.name, "Field " + field.name + " " + expectation(field)
                                + "; got \"" + resolved + "\""));
                        continue;
                    }
                    value = service.normalizeFieldValue(field, resolved);
                }
            } catch (IllegalArgumentException e) {
                boolean missing = PomInputs.supports(field) && PomInputs.suppliedValue(field, source) == null;
                errors.add(new PayloadIssue(field.name, missing ? PayloadIssue.REQUIRED : PayloadIssue.INVALID_VALUE,
                        e.getMessage()));
                continue;
            }
            int before = errors.size();
            checkValue(field, value, errors);
            if (errors.size() == before && value != null) result.put(field.name, value);
        }
        if (errors.isEmpty()) {
            try {
                PomInputs.normalizeBlueprintInputs(blueprint, result);
            } catch (IllegalArgumentException e) {
                errors.add(new PayloadIssue("$", PayloadIssue.INVALID_VALUE, e.getMessage()));
            }
        }
        if (includeGeneric) {
            Set<String> reported = new HashSet<>();
            errors.forEach(issue -> reported.add(issue.path()));
            List<PayloadIssue> generic = new ArrayList<>();
            BlueprintValidationUtils.collectGenericPayload(result, blueprint, generic);
            generic.stream().filter(issue -> !reported.contains(issue.path())).forEach(errors::add);
        }

        warnUnknownKeys(fields, source, warnings);
        return new PayloadValidation(errors.isEmpty() ? Collections.unmodifiableMap(result) : null, errors, warnings);
    }

    /** Type, shape and rule checks for one resolved field value; appends one issue per problem. */
    static void checkValue(InputField field, Object value, List<PayloadIssue> errors) {
        int before = errors.size();
        InputValidation.collectField(field, value, field.name, errors);
        if (errors.size() > before) return;
        if (PomInputs.supports(field)) {
            try { PomInputs.normalize(field, value, Map.of()); }
            catch (IllegalArgumentException e) { errors.add(invalid(field.name, e.getMessage())); }
            return;
        }
        if (field.type == FieldType.RECORD_ARRAY) {
            checkRecordArray(field, value, errors);
            return;
        }
        if (field.type == FieldType.MULTI_SELECT && value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!FieldUtils.isValid(field, item == null ? null : item.toString(), false)) {
                    errors.add(invalid(field.name + "[" + i + "]",
                            "Field " + field.name + "[" + i + "] " + expectation(field)));
                }
            }
            return;
        }
        String text = value == null ? null : value.toString();
        if (FieldUtils.isValid(field, text, false)) return;
        if (text == null || text.isEmpty()) {
            errors.add(new PayloadIssue(field.name, PayloadIssue.REQUIRED, "Field " + field.name + " is required"));
        } else {
            errors.add(invalid(field.name, "Field " + field.name + " " + expectation(field) + "; got \"" + text + "\""));
        }
    }

    private static void checkRecordArray(InputField field, Object value, List<PayloadIssue> errors) {
        if (value == null) return;
        if (!(value instanceof List<?> records)) {
            errors.add(invalid(field.name, "Field " + field.name + " must be a list of records."));
            return;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            if (!(records.get(i) instanceof Map<?, ?> rawMap)) {
                errors.add(invalid(field.name + "[" + i + "]",
                        "Field " + field.name + "[" + i + "] must be a record object."));
                continue;
            }
            Map<String, Object> converted = new HashMap<>();
            rawMap.forEach((k, v) -> converted.put(k.toString(), v));
            normalized.add(converted);
        }
        if (normalized.size() != records.size()) return;
        if (field.childFields == null || field.childFields.isEmpty()) {
            errors.add(invalid(field.name, "Field " + field.name + " declares no child fields."));
            return;
        }
        for (int i = 0; i < normalized.size(); i++) {
            if (!FieldUtils.isValidRecordArray(field, List.of(normalized.get(i)), false)) {
                errors.add(invalid(field.name + "[" + i + "]", "Field " + field.name + "[" + i
                        + "] has invalid record values; the first field (" + field.childFields.get(0).name
                        + ") must be set whenever any other field is, and every value must match its type"));
            }
        }
    }

    private static String expectation(InputField field) {
        if (field.validation != null) return "must satisfy " + field.validation;
        if (field.type == null) return "has an invalid value";
        return switch (field.type) {
            case BOOLEAN -> "must be true or false (or y/n)";
            case NUMBER -> "must be an integer";
            case DATE -> "must be an ISO date (yyyy-MM-dd)";
            case DROPDOWN -> field.validValues == null || field.validValues.isEmpty()
                    ? "has an invalid value" : "must be one of " + field.validValues;
            case MULTI_SELECT -> field.validValues == null || field.validValues.isEmpty()
                    ? "has an invalid value" : "values must be among " + field.validValues;
            default -> "has an invalid value";
        };
    }

    private static void warnUnknownKeys(List<InputField> fields, Map<String, Object> source, List<PayloadIssue> warnings) {
        Set<String> known = new HashSet<>(CONTROL_KEYS);
        for (InputField field : fields) {
            known.add(field.name);
            if (field.type == FieldType.SuperPOM && "super_pom".equals(field.name)) known.addAll(LEGACY_PARENT_KEYS);
        }
        for (String key : source.keySet()) {
            if (!known.contains(key)) {
                warnings.add(new PayloadIssue(key, PayloadIssue.UNKNOWN_FIELD,
                        "Field " + key + " is not an input of this blueprint and is ignored"));
            }
        }
    }

    private static String describe(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        String message = e.getOriginalMessage();
        return location == null ? message
                : message + " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
    }

    private static PayloadIssue invalid(String path, String message) {
        return new PayloadIssue(path, PayloadIssue.INVALID_VALUE, message);
    }

    private static PayloadValidation failed(PayloadIssue issue) {
        return new PayloadValidation(null, List.of(issue), List.of());
    }
}
