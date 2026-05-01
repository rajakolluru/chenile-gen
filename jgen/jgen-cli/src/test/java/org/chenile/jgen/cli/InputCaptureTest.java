package org.chenile.jgen.cli;

import org.junit.jupiter.api.Test;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputCaptureTest {

    @Test
    void defaultValueCanReferenceEarlierCapturedField() {
        Map<String, Object> resolutionMap = new HashMap<>();
        resolutionMap.put("monolith", "agent");

        InputField field = new InputField();
        field.name = "mcpServerName";
        field.type = FieldType.STRING;
        field.description = "MCP server name";
        field.defaultValue = "${monolith}";

        String value = (String) InputCapture.captureField(field, resolutionMap, null, Map.of());

        assertEquals("agent", value);
    }

    @Test
    void inputMapValueCanReferenceEarlierCapturedField() {
        Map<String, Object> resolutionMap = new HashMap<>();
        resolutionMap.put("monolith", "agent");

        InputField field = new InputField();
        field.name = "mcpInstructions";
        field.type = FieldType.STRING;
        field.description = "MCP instructions";
        field.defaultValue = "ignored";

        String value = (String) InputCapture.captureField(
                field,
                resolutionMap,
                null,
                Map.of("mcpInstructions", "This server exposes MCP tools from the ${monolith} mini monolith"));

        assertEquals("This server exposes MCP tools from the agent mini monolith", value);
    }

    @Test
    void recordArrayInputMapSupportsDependencies() {
        Map<String, Object> resolutionMap = new HashMap<>();
        InputField field = dependencyField();

        List<Map<String,Object>> value = (List<Map<String, Object>>) InputCapture.captureField(
                field,
                resolutionMap,
                null,
                Map.of("dependencies", List.of(Map.of(
                        "dependencyName", "service-registry-delegate",
                        "dependencyGroup", "org.chenile",
                        "dependencyVersion", "2.1.20"
                ))));

        assertEquals(1, value.size());
        assertEquals("service-registry-delegate", value.get(0).get("dependencyName"));
        assertEquals("org.chenile", value.get(0).get("dependencyGroup"));
        assertEquals("2.1.20", value.get(0).get("dependencyVersion"));
    }

    @Test
    void recordArrayRejectsGroupOrVersionWithoutName() {
        Map<String, Object> resolutionMap = new HashMap<>();
        InputField field = dependencyField();

        assertThrows(RuntimeException.class, () -> InputCapture.captureField(
                field,
                resolutionMap,
                null,
                Map.of("dependencies", List.of(Map.of(
                        "dependencyGroup", "org.chenile",
                        "dependencyVersion", "2.1.20"
                )))));
    }

    private InputField dependencyField() {
        InputField field = new InputField();
        field.name = "dependencies";
        field.type = FieldType.RECORD_ARRAY;
        field.description = "Additional Maven dependencies";

        InputField name = new InputField();
        name.name = "dependencyName";
        name.type = FieldType.STRING;
        name.description = "Dependency artifactId";

        InputField group = new InputField();
        group.name = "dependencyGroup";
        group.type = FieldType.STRING;
        group.description = "Dependency groupId";

        InputField version = new InputField();
        version.name = "dependencyVersion";
        version.type = FieldType.STRING;
        version.description = "Dependency version";

        field.childFields = List.of(name, group, version);
        return field;
    }
}
