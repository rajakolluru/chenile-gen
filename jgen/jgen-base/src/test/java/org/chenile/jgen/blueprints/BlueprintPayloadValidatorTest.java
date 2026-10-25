package org.chenile.jgen.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintPayloadValidatorTest {
    private final ObjectMapper json = new ObjectMapper();

    private BlueprintConfig blueprint() throws Exception {
        return json.readValue("""
            {"name":"demo","projectNameField":"service","inputFields":[
              {"name":"service","type":"STRING","validation":"JAVA_IDENTIFIER"},
              {"name":"mode","type":"DROPDOWN","validValues":["basic","advanced"],"defaultValue":"basic"},
              {"name":"count","type":"NUMBER","defaultValue":"1"},
              {"name":"secure","type":"BOOLEAN","defaultValue":"n"},
              {"name":"realm","type":"STRING","visibleWhen":{"secure":"true"}},
              {"name":"fields","type":"RECORD_ARRAY","required":false,"childFields":[
                {"name":"name","type":"STRING","validation":"JAVA_IDENTIFIER"},
                {"name":"type","type":"STRING","required":false}]}
            ]}
            """, BlueprintConfig.class);
    }

    private static List<String> paths(List<PayloadIssue> issues) {
        return issues.stream().map(PayloadIssue::path).toList();
    }

    @Test void reportsSyntaxErrorsWithLocation() throws Exception {
        var result = BlueprintPayloadValidator.validate(blueprint(), "{\"service\": \"orders\",\n \"mode\": }");
        assertFalse(result.valid());
        assertEquals(PayloadIssue.INVALID_JSON, result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("line 2"), result.errors().get(0).message());
        assertNull(result.inputs());
    }

    @Test void rejectsDuplicateKeysEmptyInputAndNonObjects() throws Exception {
        assertEquals(PayloadIssue.INVALID_JSON, BlueprintPayloadValidator.validate(blueprint(),
                "{\"service\":\"a\",\"service\":\"b\"}").errors().get(0).code());
        assertEquals(PayloadIssue.INVALID_JSON, BlueprintPayloadValidator.validate(blueprint(), "  ").errors().get(0).code());
        assertEquals(PayloadIssue.NOT_AN_OBJECT, BlueprintPayloadValidator.validate(blueprint(), "[1,2]").errors().get(0).code());
    }

    @Test void collectsEveryFieldErrorAtOnce() throws Exception {
        var result = BlueprintPayloadValidator.validate(blueprint(), """
            {"service":"class","mode":"expert","count":"many","secure":"maybe"}
            """);
        assertEquals(List.of("service", "mode", "count", "secure"), paths(result.errors()));
        assertTrue(result.errors().get(1).message().contains("[basic, advanced]"));
        assertTrue(result.errors().get(2).message().contains("integer"));
    }

    @Test void requiredCountsOnlyVisibleFields() throws Exception {
        var hidden = BlueprintPayloadValidator.validate(blueprint(), "{\"service\":\"orders\"}");
        assertTrue(hidden.valid(), hidden.errors().toString());
        var shown = BlueprintPayloadValidator.validate(blueprint(), "{\"service\":\"orders\",\"secure\":\"true\"}");
        assertEquals(List.of("realm"), paths(shown.errors()));
        assertEquals(PayloadIssue.REQUIRED, shown.errors().get(0).code());
        var ignored = BlueprintPayloadValidator.validate(blueprint(), "{\"service\":\"orders\",\"realm\":\"x\"}");
        assertTrue(ignored.valid());
        assertEquals(PayloadIssue.NOT_VISIBLE, ignored.warnings().get(0).code());
    }

    @Test void collectsNestedRecordErrorsPerElement() throws Exception {
        var result = BlueprintPayloadValidator.validate(blueprint(), """
            {"service":"orders","fields":[{"name":"class"},{"name":"ok"},{"name":"1bad"},"oops"]}
            """);
        assertEquals(List.of("fields[0].name", "fields[2].name", "fields[3]"), paths(result.errors()));
    }

    @Test void undeclaredKeysAreWarningsExceptTheBlueprintKey() throws Exception {
        var result = BlueprintPayloadValidator.validate(blueprint(), """
            {"blueprint":"demo","company":"acme","service":"orders","colour":"blue"}
            """);
        assertTrue(result.valid());
        assertEquals("mycompany", result.inputs().get("company"), "config values come from the config, not the payload");
        assertEquals(List.of("colour", "company"), paths(result.warnings()).stream().sorted().toList());
        assertEquals(PayloadIssue.UNKNOWN_FIELD, result.warnings().get(0).code());
    }

    @Test void genericChecksRunForPayloadsButNotForFieldOnlyResolution() throws Exception {
        var config = new org.chenile.jgen.config.ConfigProvider().obtainDefaultConfig();
        config.chenileVersion = "1.0</version>";
        var result = BlueprintPayloadValidator.validate(blueprint(), config, Map.of("service", "orders"));
        assertEquals(List.of("chenileVersion"), paths(result.errors()));
        assertEquals(PayloadIssue.INVALID_INPUT, result.errors().get(0).code());
        assertTrue(BlueprintPayloadValidator.validateFields(blueprint(), config, Map.of("service", "orders")).valid());
    }

    @Test void resolvesDefaultsAndNormalizesAValidPayload() throws Exception {
        var result = BlueprintPayloadValidator.validate(blueprint(), "{\"service\":\"orders\",\"secure\":\"y\",\"realm\":\"r\"}");
        assertTrue(result.valid(), result.errors().toString());
        assertEquals("basic", result.inputs().get("mode"));
        assertEquals(Boolean.TRUE, result.inputs().get("secure"));
        assertEquals("r", result.inputs().get("realm"));
        assertEquals("com", result.inputs().get("com"));
    }

    @Test void orThrowListsAllErrors() throws Exception {
        var result = BlueprintPayloadValidator.validate(blueprint(), "{\"service\":\"class\",\"count\":\"x\"}");
        var error = assertThrows(IllegalArgumentException.class, result::orThrow);
        assertTrue(error.getMessage().startsWith("2 input errors"), error.getMessage());
        assertTrue(error.getMessage().contains("service") && error.getMessage().contains("count"));
    }
}
