package org.chenile.jgen.blueprint.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.blueprints.SampleInputFile;
import org.chenile.jgen.blueprint.process.config.ProcessDefinitionConfigurator;
import org.chenile.trigger.cron.JsonCrontabRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.chenile.jgen.util.CapUtils.capitalizeFirst;
import static org.chenile.jgen.util.JavaNameUtils.modelName;

/** Adapts validated definitions and schedules to the template model; contains no validation rules. */
public class InitProcessManagementBlueprint implements InitHook {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public void init(BlueprintConfig blueprint) {
        blueprint.postInputCaptureHook = this::prepare;
        blueprint.sampleInputFiles.put("processSpec", SampleInputFile.resource(".json", getClass(), "/process-management-samples/defs.json"));
        blueprint.sampleInputFiles.put("cronSpec", SampleInputFile.resource(".json", getClass(), "/process-management-samples/crons.json"));
    }

    public void prepare(Map<String,Object> input) {
        try {
            var configurator = new ProcessDefinitionConfigurator(input);
            try (var stream = Files.newInputStream(Path.of(input.get("processSpec").toString()))) {
                configurator.read(stream);
            }
            var crons = new JsonCrontabRepository();
            Object cronSpec = input.get("cronSpec");
            if (cronSpec != null && !cronSpec.toString().isBlank()) {
                try (var stream = Files.newInputStream(Path.of(cronSpec.toString()))) { crons.read(stream); }
            }
            configurator.validateCrons(crons.findAll());
            List<Map<String,Object>> processes = new ArrayList<>();
            for (var def : configurator.processes.processMap.values()) processes.add(Map.of(
                "name", capitalizeFirst(def.processType), "processType", def.processType,
                "inputType", modelName(def.args), "fields", configurator.fields(def.processType),
                "leaf", def.leaf, "composite", !def.leaf,
                "serviceId", Character.toLowerCase(def.processType.charAt(0)) + def.processType.substring(1),
                "children", configurator.children(def).stream().map(c -> Map.of(
                        "name", c.processType, "inputType", modelName(c.args))).toList()));
            input.put("packageName", configurator.packageName);
            input.put("Application", capitalizeFirst(input.get("application").toString()));
            input.put("processes", processes);
            input.put("databaseDefinitions", "database".equals(input.get("definitionSource")));
            input.put("defsJson", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("processMap", configurator.processes.processMap)));
            input.put("cronsJson", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(crons.findAll()));
            input.put("processCount", processes.size()); input.put("hasCrons", !crons.findAll().isEmpty());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid process-management blueprint input: " + e.getMessage(), e);
        }
    }
}
