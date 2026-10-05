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

    @Test void processCompanionsUseOutputStemAndValidateWithTheirRuntimeReaders() throws Exception {
        Path output = temporary.resolve("nested/orders.input.json");
        assertEquals(0, execute("-g","chenile-process-management","-o",output.toString(),"-e"));
        var inputs = read(output);
        Path definitions = Path.of(inputs.get("processSpec").toString());
        Path schedules = Path.of(inputs.get("cronSpec").toString());
        assertEquals(output.resolveSibling("orders.input-processSpec.json"), definitions);
        assertEquals(output.resolveSibling("orders.input-cronSpec.json"), schedules);
        inputs.putAll(new ConfigProvider().getConfigAsMap(new ConfigProvider().obtainDefaultConfig()));
        inputs.put("application","example");
        var configurator = new ProcessDefinitionConfigurator(inputs);
        try (var stream = Files.newInputStream(definitions)) { configurator.read(stream); }
        var crons = new JsonCrontabRepository();
        try (var stream = Files.newInputStream(schedules)) { crons.read(stream); }
        assertDoesNotThrow(() -> configurator.validateCrons(crons.findAll()));
        assertEquals(2, configurator.processes.processMap.size());
        assertFalse(crons.findAll().get(0).enabled);
    }

    @Test void customStmEmitterProvidesParseableWorkflowAndAcceptsEOutputPath() throws Exception {
        Path output = temporary.resolve("workflow.json");
        assertEquals(0, execute("-g","wfcustom","-e",output.toString()));
        var input = read(output);
        Path xml = Path.of(input.get("xmlFile").toString());
        assertEquals(output.resolveSibling("workflow-xmlFile.xml"),xml);
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
        assertFalse(Files.exists(temporary.resolve("input-processSpec.json")));
    }

    @Test void eWithBlueprintWithoutFileInputsStillEmitsItsMainInput() throws Exception {
        Path output = temporary.resolve("service.json");
        assertEquals(0, execute("-g","chenile-service","-o",output.toString(),"-e"));
        assertEquals("chenile-service",read(output).get("blueprint"));
        try (var files = Files.list(temporary)) { assertEquals(1,files.count()); }
    }

    @Test void refusesExistingCompanionsBeforeEmittingAnythingElse() throws Exception {
        Path output = temporary.resolve("input.json");
        Path existing = Files.writeString(temporary.resolve("input-cronSpec.json"),"keep");
        assertNotEquals(0, execute("-g","chenile-process-management","-o",output.toString(),"-e"));
        assertEquals("keep",Files.readString(existing));
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(temporary.resolve("input-processSpec.json")));
    }

    @Test void rejectsMissingOrConflictingOutputPaths() {
        assertNotEquals(0, execute("-g","chenile-process-management","-e"));
        assertNotEquals(0, execute("-g","chenile-process-management","-e",temporary.resolve("one.json").toString(),
                "-o",temporary.resolve("two.json").toString()));
    }

    @Test void refusesToOverwriteTheMainInputFile() throws Exception {
        Path output = Files.writeString(temporary.resolve("input.json"),"keep");
        assertNotEquals(0, execute("-g","wfcustom","-o",output.toString(),"-e"));
        assertEquals("keep",Files.readString(output));
        assertFalse(Files.exists(temporary.resolve("input-xmlFile.xml")));
    }
}
