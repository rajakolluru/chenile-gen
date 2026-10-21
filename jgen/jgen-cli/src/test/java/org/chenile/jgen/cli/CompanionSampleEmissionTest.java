package org.chenile.jgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.blueprint.process.config.ProcessDefinitionConfigurator;
import org.chenile.trigger.cron.JsonCrontabRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompanionSampleEmissionTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();

    private int execute(String... args) {
        var command = new CommandLine(new GenMain());
        command.setErr(new PrintWriter(new StringWriter()));
        return command.execute(args);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> read(Path path) throws Exception { return json.readValue(path.toFile(), Map.class); }

    @Test void processCompanionsAndInputAreWrittenTogetherIntoTheEmitFolder() throws Exception {
        Path folder = temporary.resolve("nested/process-specs");
        Path output = folder.resolve("input-file.json");
        assertEquals(0, execute("-g","chenile-process-management","-e",folder.toString()));
        var inputs = read(output);
        Path definitions = Path.of(inputs.get("processSpec").toString());
        Path schedules = Path.of(inputs.get("cronSpec").toString());
        assertEquals(folder.resolve("input-file-processSpec.json"), definitions);
        assertEquals(folder.resolve("input-file-cronSpec.json"), schedules);
        inputs.putAll(new ConfigProvider().getConfigAsMap(new ConfigProvider().obtainDefaultConfig()));
        inputs.put("application","example");
        var configurator = new ProcessDefinitionConfigurator(inputs);
        try (var stream = Files.newInputStream(definitions)) { configurator.read(stream); }
        var crons = new JsonCrontabRepository();
        try (var stream = Files.newInputStream(schedules)) { crons.read(stream); }
        assertDoesNotThrow(() -> configurator.validateCrons(crons.findAll()));
        assertEquals(2, configurator.processes.processMap.size());
        assertFalse(crons.findAll().get(0).enabled);
        assertTrue(configurator.outputFields("item").stream().anyMatch(field -> "processed".equals(field.get("name"))));
    }

    @Test void customStmEmitterProvidesParseableWorkflowInTheEmitFolder() throws Exception {
        Path folder = temporary.resolve("workflow-specs");
        Path output = folder.resolve("input-file.json");
        assertEquals(0, execute("-g","wfcustom","-e",folder.toString()));
        var input = read(output);
        Path xml = Path.of(input.get("xmlFile").toString());
        assertEquals(folder.resolve("input-file-xmlFile.xml"),xml);
        var provider = new ConfigProvider();
        input.put("service","example");
        var blueprint = Registry.blueprints.get("wfcustom");
        var prepared = new BlueprintInputService().buildInputMap(blueprint, provider.obtainDefaultConfig(), input);
        blueprint.postInputCaptureHook.accept(prepared);
        assertNotNull(prepared.get("workflow"));
        assertNotNull(prepared.get("testcases"));
    }

    @Test void gWithoutERetainsExistingPlaceholderBehavior() throws Exception {
        Path output = temporary.resolve("input.json");
        assertEquals(0, execute("-g","chenile-process-management","-o",output.toString()));
        assertNull(read(output).get("processSpec"));
        assertEquals("process-work", read(output).get("workTopic"));
        assertFalse(Files.exists(temporary.resolve("input-processSpec.json")));
    }

    @Test void eFolderWithBlueprintWithoutFileInputsStillEmitsItsMainInput() throws Exception {
        Path folder = temporary.resolve("service-input");
        Path output = folder.resolve("input-file.json");
        assertEquals(0, execute("-g","chenile-service","-e",folder.toString()));
        assertEquals("chenile-service",read(output).get("blueprint"));
        try (var files = Files.list(folder)) { assertEquals(1,files.count()); }
    }

    @Test void refusesExistingCompanionsBeforeEmittingAnythingElse() throws Exception {
        Path folder = temporary.resolve("specs");
        Files.createDirectories(folder);
        Path output = folder.resolve("input-file.json");
        Path existing = Files.writeString(folder.resolve("input-file-cronSpec.json"),"keep");
        assertNotEquals(0, execute("-g","chenile-process-management","-e",folder.toString()));
        assertEquals("keep",Files.readString(existing));
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(folder.resolve("input-file-processSpec.json")));
    }

    @Test void rejectsMissingOrConflictingOutputPaths() {
        assertNotEquals(0, execute("-g","chenile-process-management","-e"));
        assertNotEquals(0, execute("-g","chenile-process-management","-e",temporary.resolve("one").toString(),
                "-o",temporary.resolve("two.json").toString()));
    }

    @Test void refusesToOverwriteTheMainInputFile() throws Exception {
        Path folder = temporary.resolve("workflow");
        Files.createDirectories(folder);
        Path output = Files.writeString(folder.resolve("input-file.json"),"keep");
        assertNotEquals(0, execute("-g","wfcustom","-e",folder.toString()));
        assertEquals("keep",Files.readString(output));
        assertFalse(Files.exists(folder.resolve("input-file-xmlFile.xml")));
    }
}
