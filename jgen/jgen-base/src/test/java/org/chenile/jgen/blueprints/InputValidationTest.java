package org.chenile.jgen.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.model.*;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.template.chain.GenericInputValidator;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InputValidationTest {
    @TempDir Path output;
    private final ObjectMapper json = new ObjectMapper();

    @Test void rulesRejectUnsafeValuesAndAcceptValidNames() {
        String[][] examples = {
            {"JAVA_IDENTIFIER", "firstName", "class", "first-name", "../escape", "1name"},
            {"JAVA_TYPE", "FirstName", "firstName", "Class-name"},
            {"JAVA_PACKAGE", "com.example.workers", "com.class", "com.example.", ".example", "com..example"},
            {"PROJECT_NAME", "my-service", "../escape", "bad/name"},
            {"VERSION", "2.1.32-SNAPSHOT", "bad version", "../1"},
            {"HTTP_URL", "https://example.com:8000", "file:///tmp", "http://", "bad"},
            {"KAFKA_TOPIC", "process-work.v1", ".", "..", "bad topic", "a".repeat(250)}
        };
        for (String[] example : examples) {
            var rule = ValidationType.valueOf(example[0]);
            assertTrue(InputValidation.isValid(rule, example[1]), example[0]);
            for (int i = 2; i < example.length; i++) assertFalse(InputValidation.isValid(rule, example[i]), example[0] + ": " + example[i]);
        }
    }

    @Test void metadataIsOptionalAndRejectsUnknownRules() throws Exception {
        InputField field = json.readValue("{\"name\":\"service\",\"type\":\"STRING\",\"validation\":\"JAVA_IDENTIFIER\"}", InputField.class);
        assertTrue(FieldUtils.isValid(field, "orders"));
        assertFalse(FieldUtils.isValid(field, "class"));
        field.validation = null;
        assertTrue(FieldUtils.isValid(field, "legacy-name"));
        assertThrows(Exception.class, () -> json.readValue("{\"validation\":\"TYPO\"}", InputField.class));
    }

    @Test void validatesVisibleNestedFieldsAndSelectedValuesAcrossInputPaths() throws Exception {
        BlueprintConfig blueprint = json.readValue("""
            {"inputFields":[
              {"name":"enabled","type":"BOOLEAN","defaultValue":"n"},
              {"name":"url","type":"STRING","validation":"HTTP_URL","visibleWhen":{"enabled":"true"}},
              {"name":"fields","type":"RECORD_ARRAY","required":false,"childFields":[
                {"name":"name","type":"STRING","validation":"JAVA_IDENTIFIER"}]},
              {"name":"names","type":"MULTI_SELECT","required":false,"validation":"JAVA_IDENTIFIER"}
            ]}
            """, BlueprintConfig.class);
        var service = new BlueprintInputService();
        var config = new ConfigProvider().obtainDefaultConfig();
        Map<String,Object> values = new HashMap<>(Map.of("url", "ignored", "fields", List.of(Map.of("name", "valid"))));
        assertDoesNotThrow(() -> service.buildInputMap(blueprint, config, values));
        values.put("enabled", true);
        assertThrows(IllegalArgumentException.class, () -> service.buildInputMap(blueprint, config, values));
        values.put("url", "https://example.com");
        values.put("fields", List.of(Map.of("name", "class")));
        var error = assertThrows(IllegalArgumentException.class, () -> InputValidation.validateBlueprint(blueprint, values));
        assertTrue(error.getMessage().contains("fields[0].name"));
        values.put("fields", List.of(Map.of("name", "valid")));
        values.put("names", List.of("first", "class"));
        assertThrows(IllegalArgumentException.class, () -> service.buildInputMap(blueprint, config, values));
        var names = blueprint.inputFields.get(3);
        assertTrue(FieldUtils.isValid(names, "first,second"));
        assertFalse(FieldUtils.isValid(names, "first,class"));
        values.put("names", "first,second");
        assertDoesNotThrow(() -> InputValidation.validateBlueprint(blueprint, values));
        values.put("names", List.of("first", "second"));
        assertDoesNotThrow(() -> service.buildInputMap(blueprint, config, values));
        assertEquals(ValidationType.HTTP_URL, service.describeFields(blueprint, config).get(1).get("validation"));
    }

    @Test void executionBoundaryRejectsInvalidComposedInputsBeforeHooksOrWrites() throws Exception {
        var blueprint = json.readValue("""
            {"name":"example","inputFields":[
              {"name":"project","type":"STRING"},
              {"name":"service","type":"STRING","validation":"JAVA_IDENTIFIER"}]}
            """, BlueprintConfig.class);
        var config = new ConfigProvider();
        var context = new TemplateContext(); context.blueprintConfig = blueprint;
        context.map = new HashMap<>(config.getConfigAsMap(config.obtainDefaultConfig()));
        context.map.putAll(Map.of("project", "example", "destFolder", output.toString(), "service", "class"));
        var error = assertThrows(IllegalArgumentException.class, () -> new GenericInputValidator().execute(context));
        assertTrue(error.getMessage().contains("service"));
        assertFalse(Files.exists(output.resolve("example")));
        context.map.remove("service");
        assertThrows(IllegalArgumentException.class, () -> new GenericInputValidator().execute(context));
        context.map.put("service", "orders");
        assertDoesNotThrow(() -> new GenericInputValidator().execute(context));
    }
}
