package org.chenile.jgen.blueprint.process;

import static org.chenile.jgen.util.CapUtils.capitalizeFirst;
import static org.chenile.jgen.util.JavaNameUtils.modelName;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.chenile.jgen.blueprint.process.config.ProcessDefinitionConfigurator;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.blueprints.SampleInputFile;
import org.chenile.trigger.cron.JsonCrontabRepository;

/**
 * Adapts validated definitions and schedules to the template model; contains no validation rules.
 */
public class InitProcessManagementBlueprint implements InitHook {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public void init(BlueprintConfig blueprint) {
    blueprint.postInputCaptureHook = this::prepare;
    blueprint.childBlueprintInputs = new ProcessDeploymentPlan()::childInputs;
    blueprint.sampleInputFiles.put(
        "processSpec",
        SampleInputFile.resource(".json", getClass(), "/process-management-samples/defs.json"));
    blueprint.sampleInputFiles.put(
        "cronSpec",
        SampleInputFile.resource(".json", getClass(), "/process-management-samples/crons.json"));
  }

  public void prepare(Map<String, Object> input) {
    try {
      var configurator = new ProcessDefinitionConfigurator(input);
      try (var stream = Files.newInputStream(Path.of(input.get("processSpec").toString()))) {
        configurator.read(stream);
      }
      var crons = new JsonCrontabRepository();
      Object cronSpec = input.get("cronSpec");
      if (cronSpec != null && !cronSpec.toString().isBlank()) {
        try (var stream = Files.newInputStream(Path.of(cronSpec.toString()))) {
          crons.read(stream);
        }
      }
      configurator.validateCrons(crons.findAll());
      List<Map<String, Object>> processes = new ArrayList<>();
      for (var def : configurator.processes.processMap.values()) {
        Map<String, Object> process = new LinkedHashMap<>();
        process.put("name", capitalizeFirst(def.processType));
        process.put("processType", def.processType);
        process.put("inputType", modelName(def.args));
        process.put("fields", configurator.fields(def.processType));
        process.put("outputType", configurator.outputType(def.processType));
        process.put("outputFields", configurator.outputFields(def.processType));
        process.put("apiArtifact", input.get("application") + "-" + def.processType + "-api");
        process.put("serviceArtifact", input.get("application") + "-" + def.processType + "-service");
        process.put("leaf", def.leaf);
        process.put("composite", !def.leaf);
        process.put(
            "serviceId",
            Character.toLowerCase(def.processType.charAt(0)) + def.processType.substring(1));
        process.put(
            "children",
            configurator.children(def).stream()
                .map(
                    c ->
                        Map.of(
                            "name", c.processType,
                            "inputType", modelName(c.args),
                            "childApiArtifact",
                                input.get("application") + "-" + c.processType + "-api"))
                .toList());
        if (Boolean.TRUE.equals(process.get("composite"))) {
          String processType = process.get("processType").toString();
          process.put("childOutputType", configurator.childOutputType(processType));
        }
        processes.add(process);
      }
      ProcessDeploymentPlan.prepare(input, processes, configurator.processes.processMap);
      input.put("packageName", configurator.packageName);
      input.put("Application", capitalizeFirst(input.get("application").toString()));
      input.put("processes", processes);
      input.put("databaseDefinitions", "database".equals(input.get("definitionSource")));
      input.put(
          "defsJson",
          JSON.writerWithDefaultPrettyPrinter()
              .writeValueAsString(Map.of("processMap", configurator.processes.processMap)));
      input.put(
          "cronsJson", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(crons.findAll()));
      var root =
          configurator.processes.processMap.values().stream()
              .filter(d -> d.parentProcessType == null || d.parentProcessType.isBlank())
              .findFirst()
              .orElseThrow();
      input.put("testProcessType", root.processType);
      input.put("testWorkTopic", root.config.get("queue"));
      input.put(
          "testServiceId",
          Character.toLowerCase(root.processType.charAt(0)) + root.processType.substring(1));
      input.put("processCount", processes.size());
      input.put("hasCrons", !crons.findAll().isEmpty());
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Invalid process-management blueprint input: " + e.getMessage(), e);
    }
  }
}
