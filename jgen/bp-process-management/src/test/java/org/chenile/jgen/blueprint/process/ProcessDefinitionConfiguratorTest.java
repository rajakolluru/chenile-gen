package org.chenile.jgen.blueprint.process;

import org.chenile.jgen.blueprint.process.config.ProcessDefinitionConfigurator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.chenile.jgen.blueprint.process.config.ScheduleSpec;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProcessDefinitionConfiguratorTest {
    private ProcessDefinitionConfigurator configurator() {
        return new ProcessDefinitionConfigurator(Map.of("application","example","com","com","company","example",
                "org","demo","definitionSource","json"));
    }
    private ByteArrayInputStream json(String source) { return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)); }

    @Test void validatesFieldsWithoutAnyBlueprintGeneratorAndKeepsPreviousValidDefinitions() throws Exception {
        var reader=configurator();
        reader.read(json("""
            {"processMap":{"feed":{"leaf":true,"inputFields":{"batchId":"string"}}}}
            """));
        assertEquals("String",reader.fields("feed").get(0).get("javaType"));
        assertThrows(IllegalArgumentException.class,()->reader.read(json("""
            {"processMap":{"bad":{"leaf":true,"inputFields":{"class":"string"}}}}
            """)));
        assertNotNull(reader.findByName("feed"));
        assertThrows(IllegalArgumentException.class,()->reader.read(json("""
            {"processMap":{"feed":{"leaf":true},"Feed":{"leaf":true}}}
            """)));
    }

    @Test void checksBothFieldLocationsAndRejectsConflictingDeclarations() throws Exception {
        var reader=configurator();
        reader.read(json("""
            {"processMap":{"feed":{"leaf":true}},"inputFields":{"feed":{"batchId":"string"}}}
            """));
        assertEquals("batchId",reader.fields("feed").get(0).get("name"));
        assertThrows(IllegalArgumentException.class,()->reader.read(json("""
            {"processMap":{"feed":{"leaf":true,"inputFields":{"batchId":"string"}}},
             "inputFields":{"feed":{"batchId":"string"}}}
            """)));
    }

    @Test void validatesSeparateSchedulePayloadAgainstItsProcessInputFields() throws Exception {
        var reader=configurator(); reader.read(json("""
            {"processMap":{"feed":{"leaf":true,"inputFields":{"batchId":"string"}}}}
            """));
        String source="""
            [{"name":"daily","tenant":"default","cronExpression":"0 0 2 * * ?","eventId":"ProcessCreate",
              "payload":{"processDefName":"feed","args":{"batchId":"a"}}}]
            """;
        assertDoesNotThrow(()->reader.validateSchedules(schedules(source)));
        var failure=assertThrows(IllegalArgumentException.class,
                ()->reader.validateSchedules(schedules(source.replace("\"batchId\":\"a\"","\"batchId\":5"))));
        assertTrue(failure.getMessage().contains("Wrong type"));
        assertThrows(IllegalArgumentException.class,()->reader.validateSchedules(
                schedules(source.replace("\"processDefName\":\"feed\"","\"processDefName\":\"missing\""))));
        assertThrows(IllegalArgumentException.class,()->reader.validateSchedules(
                schedules(source.replace("0 0 2 * * ?","invalid"))));
        assertThrows(IllegalArgumentException.class,()->reader.validateSchedules(
                schedules(source.replace("\"cronExpression\"","\"timezone\":\"Mars/Base\",\"cronExpression\""))));
        String twice=source.substring(0,source.lastIndexOf(']'))+","+source.trim().substring(1);
        assertTrue(assertThrows(IllegalArgumentException.class,()->reader.validateSchedules(schedules(twice)))
                .getMessage().contains("Duplicate schedule"));
    }

    private List<ScheduleSpec> schedules(String json) throws Exception {
        return List.of(new ObjectMapper().readValue(json, ScheduleSpec[].class));
    }
}
