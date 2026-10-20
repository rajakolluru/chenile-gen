package org.chenile.jgen.blueprint.process.config;

import static org.chenile.jgen.util.CapUtils.capitalizeFirst;
import static org.chenile.jgen.util.JavaNameUtils.*;
import static org.chenile.jgen.util.ValidationUtils.require;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.model.Processes;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.model.ProcessDto;
import org.chenile.trigger.cron.Crontab;

/** Native process definitions with optional input fields and generation-specific validation. */
public class ProcessDefinitionConfigurator extends ProcessConfigurator {
  private static final Map<String, String> TYPES =
      Map.ofEntries(
          Map.entry("string", "String"),
          Map.entry("String", "String"),
          Map.entry("integer", "Integer"),
          Map.entry("int", "Integer"),
          Map.entry("Integer", "Integer"),
          Map.entry("long", "Long"),
          Map.entry("Long", "Long"),
          Map.entry("boolean", "Boolean"),
          Map.entry("Boolean", "Boolean"),
          Map.entry("double", "Double"),
          Map.entry("Double", "Double"));
  public final String packageName;

  public static class Definition extends ProcessDef {
    public Map<String, String> inputFields;
    public Map<String, String> outputFields;
  }

  public static class Definitions extends Processes {
    public Map<String, Map<String, String>> inputFields = new LinkedHashMap<>();
    public Map<String, Map<String, String>> outputFields = new LinkedHashMap<>();

    @JsonDeserialize(contentAs = Definition.class)
    public void setProcessMap(Map<String, ProcessDef> definitions) {
      processMap = definitions;
    }
  }

  public ProcessDefinitionConfigurator(Map<String, Object> input) {
    String app = identifier(Objects.toString(input.get("application"), ""));
    require(app.equals(app.toLowerCase(Locale.ROOT)), "application must be lowercase");
    require(
        Set.of("json", "database").contains(input.get("definitionSource")),
        "Invalid definitionSource");
    packageName =
        input.get("com") + "." + input.get("company") + "." + input.get("org") + "." + app;
  }

  @Override
  public void read(InputStream stream) throws IOException {
    read(stream, Definitions.class);
  }

  @Override
  protected void validate(Processes document) {
    var spec = (Definitions) document;
    require(spec.processMap != null && !spec.processMap.isEmpty(), "processMap must be nonempty");
    require(spec.inputFields != null, "inputFields cannot be null");
    require(spec.outputFields != null, "outputFields cannot be null");
    spec.processMap = new TreeMap<>(spec.processMap);
    require(
        spec.processMap.keySet().containsAll(spec.inputFields.keySet()),
        "Unknown inputFields process type");
    require(
        spec.processMap.keySet().containsAll(spec.outputFields.keySet()),
        "Unknown outputFields process type");
    Set<String> models =
        new HashSet<>(
            Set.of(
                "Process",
                "WorkerDto",
                "SubProcessPayload",
                "Object",
                "String",
                "Integer",
                "Long",
                "Double",
                "Boolean",
                "List",
                "Map"));
    Set<String> classes = new HashSet<>(), services = new HashSet<>();
    validateLinks(spec.processMap, d -> d.parentProcessType, "parent hierarchy");
    validateLinks(spec.processMap, d -> d.predecessorProcessType, "predecessor chain");
    for (var def : spec.processMap.values()) {
      identifier(def.processType);
      String name = capitalizeFirst(def.processType);
      require(!Set.of("Process", "ProcessChildren", "Info").contains(name), "Reserved processType");
      require(classes.add(name), "Duplicate generated process class: " + name);
      require(
          services.add(Character.toLowerCase(name.charAt(0)) + name.substring(1)),
          "Duplicate service ID");
      if (def instanceof Definition extended && extended.inputFields != null) {
        require(
            !spec.inputFields.containsKey(def.processType),
            "Duplicate inputFields declaration: " + def.processType);
        spec.inputFields.put(def.processType, extended.inputFields);
      }
      if (def instanceof Definition extended && extended.outputFields != null) {
        require(
            !spec.outputFields.containsKey(def.processType),
            "Duplicate outputFields declaration: " + def.processType);
        spec.outputFields.put(def.processType, extended.outputFields);
      }
      String type = def.args == null ? name + "In" : modelName(def.args);
      upperIdentifier(type);
      require(models.add(type), "Duplicate or reserved input type: " + type);
      if (def.leaf)
        require(
            models.add(name + "Output"),
            "Duplicate or reserved output type: " + name + "Output");
      def.args = packageName + ".model." + type;
      require(def.config != null, "config cannot be null");
      require(
          def.leaf == children(spec, def).isEmpty(),
          "leaf must match the hierarchy: " + def.processType);
      fields(spec, def.processType);
      require(
          def.leaf || spec.outputFields.getOrDefault(def.processType, Map.of()).isEmpty(),
          "outputFields may only be declared for leaf processes: " + def.processType);
      outputFields(spec, def.processType);
    }
    for (var def : spec.processMap.values()) {
      List<ProcessDef> children = children(spec, def);
      if (!children.isEmpty()) {
        String output = outputType(spec, children.get(0));
        require(
            children.stream().allMatch(child -> output.equals(outputType(spec, child))),
            "All immediate children must have the same output type for typed aggregation: "
                + def.processType);
      }
    }
  }

  public List<ProcessDef> children(ProcessDef parent) {
    return children((Definitions) processes, parent);
  }

  private static List<ProcessDef> children(Definitions spec, ProcessDef parent) {
    return spec.processMap.values().stream()
        .filter(d -> parent.processType.equals(d.parentProcessType))
        .toList();
  }

  private static void validateLinks(
      Map<String, ProcessDef> defs, Function<ProcessDef, String> link, String label) {
    for (String name : defs.keySet()) {
      Set<String> seen = new HashSet<>();
      for (String current = name;
          current != null && !current.isBlank();
          current = link.apply(defs.get(current))) {
        require(defs.containsKey(current), "Unknown process in " + label + ": " + current);
        require(seen.add(current), "Cycle in " + label + ": " + name);
      }
    }
  }

  public List<Map<String, String>> fields(String type) {
    return fields((Definitions) processes, type);
  }

  public List<Map<String, String>> outputFields(String type) {
    return outputFields((Definitions) processes, type);
  }

  public String outputType(String type) {
    return outputType((Definitions) processes, ((Definitions) processes).processMap.get(type));
  }

  public String childOutputType(String type) {
    var spec = (Definitions) processes;
    var process = spec.processMap.get(type);
    return outputType(spec, children(spec, process).get(0));
  }

  private String outputType(Definitions spec, ProcessDef def) {
    List<ProcessDef> children = children(spec, def);
    if (children.isEmpty()) return capitalizeFirst(def.processType) + "Output";
    return "AggregatedResult<" + outputType(spec, children.get(0)) + ">";
  }

  private static List<Map<String, String>> outputFields(Definitions spec, String type) {
    var fields = spec.outputFields.getOrDefault(type, Map.of());
    require(fields != null, "outputFields cannot be null: " + type);
    return fields.entrySet().stream()
        .map(
            e -> {
              require(TYPES.containsKey(e.getValue()), "Unsupported field type: " + e.getValue());
              return Map.of("name", identifier(e.getKey()), "javaType", TYPES.get(e.getValue()));
            })
        .toList();
  }

  private static List<Map<String, String>> fields(Definitions spec, String type) {
    var fields = spec.inputFields.getOrDefault(type, Map.of());
    require(fields != null, "inputFields cannot be null: " + type);
    return fields.entrySet().stream()
        .map(
            e -> {
              require(TYPES.containsKey(e.getValue()), "Unsupported field type: " + e.getValue());
              return Map.of("name", identifier(e.getKey()), "javaType", TYPES.get(e.getValue()));
            })
        .toList();
  }

  /** Check process ingress against the already validated process definitions and DTO fields. */
  public void validateCrons(List<Crontab> crons) throws IOException {
    var json = new ObjectMapper();
    var spec = (Definitions) processes;
    for (var cron : crons) {
      require(
          "ProcessCreate".equals(cron.eventName), "Only ProcessCreate cron ingress is supported");
      require(cron.tenant != null, "Cron tenant is required");
      var payload = json.readValue(cron.eventPayloadJson, ProcessDto.class);
      require(
          payload != null && spec.processMap.containsKey(payload.processDefName),
          "Unknown cron process type");
      require(payload.args instanceof Map<?, ?>, "Cron args must be an object");
      var args = (Map<?, ?>) payload.args;
      require(
          args.keySet()
              .equals(spec.inputFields.getOrDefault(payload.processDefName, Map.of()).keySet()),
          "Cron args must match inputFields");
      for (var field : fields(payload.processDefName)) {
        Object value = args.get(field.get("name"));
        boolean valid =
            switch (field.get("javaType")) {
              case "String" -> value instanceof String;
              case "Boolean" -> value instanceof Boolean;
              case "Integer" -> value instanceof Integer;
              case "Long" -> value instanceof Integer || value instanceof Long;
              default -> value instanceof Number;
            };
        require(valid, "Wrong type for cron argument: " + field.get("name"));
      }
    }
  }
}
