package org.chenile.jgen.blueprint.workers;

import static org.chenile.jgen.util.CapUtils.capitalizeFirst;
import static org.chenile.jgen.util.JavaNameUtils.modelName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.chenile.jgen.blueprint.process.config.ProcessDefinitionConfigurator;
import org.chenile.orchestrator.process.config.model.ProcessDef;

/** Derives worker projects, typed contracts and topic subscriptions from process definitions. */
public final class ProcessWorkerSetPlan {
  private ProcessWorkerSetPlan() {}

  public static List<Map<String, Object>> prepare(
      Map<String, Object> input, ProcessDefinitionConfigurator configurator) {
    String application = input.get("application").toString();
    List<Map<String, Object>> processes = new ArrayList<>();
    for (ProcessDef definition : configurator.processes.processMap.values()) {
      Map<String, Object> process = new LinkedHashMap<>();
      String type = definition.processType;
      process.put("name", capitalizeFirst(type));
      process.put("processType", type);
      process.put("inputType", modelName(definition.args));
      process.put("fields", configurator.fields(type));
      process.put("outputType", configurator.outputType(type));
      process.put("outputFields", configurator.outputFields(type));
      process.put("workerModule", application + "-" + type + "-workers");
      process.put("apiArtifact", application + "-" + type + "-api");
      process.put("serviceArtifact", application + "-" + type + "-service");
      process.put("leaf", definition.leaf);
      process.put("composite", !definition.leaf);
      process.put(
          "children",
          configurator.children(definition).stream()
              .map(
                  child ->
                      Map.of(
                          "name", child.processType,
                          "inputType", modelName(child.args),
                          "childApiArtifact", application + "-" + child.processType + "-api"))
              .toList());
      if (!definition.leaf) {
        process.put("childOutputType", configurator.childOutputType(type));
      }
      processes.add(process);
    }
    input.put("processes", processes);
    input.put("Application", capitalizeFirst(application));
    return processes;
  }
}
