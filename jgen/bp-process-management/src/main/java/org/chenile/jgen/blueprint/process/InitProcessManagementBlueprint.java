package org.chenile.jgen.blueprint.process;

import static org.chenile.jgen.util.CapUtils.capitalizeFirst;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.chenile.jgen.blueprint.process.config.ProcessDefinitionConfigurator;
import org.chenile.jgen.blueprint.process.config.ScheduleSpec;
import org.chenile.jgen.blueprint.workers.ProcessWorkerSetPlan;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.blueprints.SampleInputFile;

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
        "scheduleSpec",
        SampleInputFile.resource(".json", getClass(), "/process-management-samples/schedules.json"));
  }

  public void prepare(Map<String, Object> input) {
    try {
      var configurator = new ProcessDefinitionConfigurator(input);
      try (var stream = Files.newInputStream(Path.of(input.get("processSpec").toString()))) {
        configurator.read(stream);
      }
      List<ScheduleSpec> schedules = List.of();
      Object scheduleSpec = input.get("scheduleSpec");
      if (scheduleSpec != null && !scheduleSpec.toString().isBlank()) {
        try (var stream = Files.newInputStream(Path.of(scheduleSpec.toString()))) {
          schedules = List.of(JSON.readValue(stream, ScheduleSpec[].class));
        }
      }
      configurator.validateSchedules(schedules);
      List<Map<String, Object>> processes = ProcessWorkerSetPlan.prepare(input, configurator);
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
          "schedulesJson", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(schedules));
      var root =
          configurator.processes.processMap.values().stream()
              .filter(d -> d.parentProcessType == null || d.parentProcessType.isBlank())
              .findFirst()
              .orElseThrow();
      input.put("testProcessType", root.processType);
      input.put("testWorkTopic", root.config.get("queue"));
      input.put("processCount", processes.size());
      input.put("hasSchedules", !schedules.isEmpty());
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Invalid process-management blueprint input: " + e.getMessage(), e);
    }
  }
}
