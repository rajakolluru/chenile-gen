package org.chenile.jgen.blueprint.workers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.chenile.jgen.blueprint.process.config.ProcessDefinitionConfigurator;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;

/** Expands the complete prepared process set into its per-type worker projects. */
public class InitProcessWorkerSetBlueprint implements InitHook {
  @Override
  public void init(BlueprintConfig blueprint) {
    blueprint.postInputCaptureHook = this::prepare;
    blueprint.childBlueprintInputs =
        (current, childBlueprint, attributes) -> {
          if (!childBlueprint.equals("chenile-process-workers")) {
            throw new IllegalArgumentException("Unknown worker child blueprint: " + childBlueprint);
          }
          @SuppressWarnings("unchecked")
          Map<String, Object> process = (Map<String, Object>) current.get("process");
          Map<String, Object> child = new LinkedHashMap<>(process);
          child.put("application", current.get("application"));
          child.put("applicationVersion", current.get("applicationVersion"));
          child.put("packageName", current.get("packageName"));
          child.put("super_pom", current.get("super_pom"));
          child.put("dependencies", java.util.List.of());
          return child;
        };
  }

  private void prepare(Map<String, Object> input) {
    Object processSpec = input.get("processSpec");
    if (processSpec == null || processSpec.toString().isBlank()) {
      return;
    }
    try {
      var configurator = new ProcessDefinitionConfigurator(input);
      try (var stream = Files.newInputStream(Path.of(processSpec.toString()))) {
        configurator.read(stream);
      }
      input.put("packageName", configurator.packageName);
      ProcessWorkerSetPlan.prepare(input, configurator);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid process worker specification: " + e.getMessage(), e);
    }
  }
}
