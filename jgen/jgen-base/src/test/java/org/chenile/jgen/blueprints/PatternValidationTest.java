package org.chenile.jgen.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.util.InputValidation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PatternValidationTest {
    private final ObjectMapper json = new ObjectMapper();

    private BlueprintConfig blueprint(String field) throws Exception {
        return json.readValue("""
            {"name":"pattern-test","inputFields":[
              {"name":"project","type":"STRING"},
              %s,
              {"name":"repo","type":"STRING","required":false,"validation":"GITHUB_REPOSITORY"}]}
            """.formatted(field), BlueprintConfig.class);
    }

    private static final String TICKET = """
        {"name":"ticket","type":"STRING","validation":"PATTERN","pattern":"[A-Z]{2,5}-[0-9]+",
         "patternMessage":"must look like ABC-123"}""";

    @Test void patternMatchesTheWholeValueAndReportsItsOwnMessage() throws Exception {
        BlueprintConfig blueprint = blueprint(TICKET);
        InputValidation.checkDeclarations(blueprint);

        assertTrue(BlueprintPayloadValidator.validate(blueprint,
                "{\"project\":\"demo\",\"ticket\":\"JGEN-42\",\"repo\":\"acme/jgen-x\"}").valid());

        var result = BlueprintPayloadValidator.validate(blueprint,
                "{\"project\":\"demo\",\"ticket\":\"see JGEN-42\",\"repo\":\"acme\"}");
        assertEquals(List.of("ticket", "repo"), result.errors().stream().map(PayloadIssue::path).toList());
        assertEquals("Field ticket must look like ABC-123", result.errors().get(0).message());
        assertTrue(result.errors().get(1).message().contains("owner/repo"));
    }

    @Test void patternWorksForEachMultiSelectValue() throws Exception {
        BlueprintConfig blueprint = blueprint("""
            {"name":"tickets","type":"MULTI_SELECT","required":false,"validation":"PATTERN",
             "pattern":"[A-Z]+-[0-9]+","patternMessage":"must look like ABC-123"}""");
        var result = BlueprintPayloadValidator.validate(blueprint, null,
                Map.of("project", "demo", "tickets", List.of("AB-1", "nope")));
        assertEquals(List.of("tickets[1]"), result.errors().stream().map(PayloadIssue::path).toList());
    }

    @Test void badDeclarationsAreCaughtWhenTheBlueprintLoads() throws Exception {
        var noPattern = assertThrows(IllegalArgumentException.class, () -> InputValidation.checkDeclarations(blueprint(
                "{\"name\":\"t\",\"type\":\"STRING\",\"validation\":\"PATTERN\",\"patternMessage\":\"x\"}")));
        assertTrue(noPattern.getMessage().contains("no pattern"));
        var noMessage = assertThrows(IllegalArgumentException.class, () -> InputValidation.checkDeclarations(blueprint(
                "{\"name\":\"t\",\"type\":\"STRING\",\"validation\":\"PATTERN\",\"pattern\":\"x\"}")));
        assertTrue(noMessage.getMessage().contains("no patternMessage"));
        var badRegex = assertThrows(IllegalArgumentException.class, () -> InputValidation.checkDeclarations(blueprint(
                "{\"name\":\"t\",\"type\":\"STRING\",\"validation\":\"PATTERN\",\"pattern\":\"[\",\"patternMessage\":\"x\"}")));
        assertTrue(badRegex.getMessage().contains("invalid pattern"));
        var stray = assertThrows(IllegalArgumentException.class, () -> InputValidation.checkDeclarations(blueprint(
                "{\"name\":\"t\",\"type\":\"STRING\",\"validation\":\"VERSION\",\"pattern\":\"x\"}")));
        assertTrue(stray.getMessage().contains("not PATTERN"));
    }
}
