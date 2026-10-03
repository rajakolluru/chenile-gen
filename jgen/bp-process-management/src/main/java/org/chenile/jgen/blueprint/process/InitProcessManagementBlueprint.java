package org.chenile.jgen.blueprint.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;

import javax.lang.model.SourceVersion;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;

/** Validates the complete graph before JGen copies any files. Runtime definitions remain framework JSON. */
public class InitProcessManagementBlueprint implements InitHook {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String,String> TYPES = Map.ofEntries(
        Map.entry("string", "String"), Map.entry("String", "String"),
        Map.entry("integer", "Integer"), Map.entry("int", "Integer"), Map.entry("Integer", "Integer"),
        Map.entry("long", "Long"), Map.entry("Long", "Long"),
        Map.entry("boolean", "Boolean"), Map.entry("Boolean", "Boolean"),
        Map.entry("double", "Double"), Map.entry("Double", "Double"));

    @Override public void init(BlueprintConfig blueprint) {
        blueprint.postInputCaptureHook = this::prepare;
    }

    public void prepare(Map<String,Object> input) {
        try {
            String app = identifier(Objects.toString(input.get("application"), ""));
            require(app.equals(app.toLowerCase(Locale.ROOT)), "application must be lowercase");
            for (String segment : List.of("com", "company", "org")) identifier(input.get(segment).toString());
            require(Set.of("json","database").contains(input.get("definitionSource")), "definitionSource must be json or database");
            boolean registry = Boolean.TRUE.equals(input.get("registerInServiceRegistry"));
            if (registry) {
                var uri = java.net.URI.create(Objects.toString(input.get("serviceRegistryUrl"), ""));
                require(Set.of("http","https").contains(Objects.toString(uri.getScheme(), "")) && uri.getHost() != null,
                    "serviceRegistryUrl must be an HTTP(S) URL");
            }
            String pkg = input.get("com") + "." + input.get("company") + "." + input.get("org") + "." + app;
            JsonNode spec = JSON.readTree(Path.of(input.get("processSpec").toString()).toFile());
            require(spec.isObject(), "processSpec must be an object");
            allowed(spec, Set.of("processes","cronTriggers"));
            require(spec.path("processes").isArray() && !spec.path("processes").isEmpty(), "processes must be a nonempty array");
            Map<String,JsonNode> nodes = new LinkedHashMap<>();
            Set<String> inputNames = new HashSet<>(Set.of("ProcessResult"));
            for (JsonNode p : spec.get("processes")) {
                allowed(p, Set.of("processType","inputType","fields","children","implementation","config","predecessorProcessType","predecessorArgs"));
                String name = identifier(text(p,"processType"));
                require(Character.isUpperCase(name.charAt(0)), "processType must begin with an uppercase letter: " + name);
                require(!Set.of("Process","ProcessChildren","Info").contains(name), "processType conflicts with a framework service: " + name);
                require(nodes.putIfAbsent(name, p) == null, "Duplicate processType: " + name);
                String model=identifier(p.path("inputType").asText(name + "In"));
                require(Character.isUpperCase(model.charAt(0)) && !Set.of("Process","WorkerDto","SubProcessPayload","Object","String","Integer","Long","Double","Boolean","List","Map","FileWork").contains(model), "inputType conflicts with an imported type or must be uppercase: " + model);
                require(inputNames.add(model), "Duplicate inputType");
            }
            Map<String,String> parents = new HashMap<>();
            for (var entry : nodes.entrySet()) {
                Set<String> seen = new HashSet<>();
                for (String child : children(entry.getValue())) {
                    require(nodes.containsKey(child), "Unknown child: " + child);
                    require(seen.add(child), "Duplicate child: " + child);
                    require(parents.putIfAbsent(child, entry.getKey()) == null, "A process type may have only one parent: " + child);
                }
            }
            for (String name : nodes.keySet()) {
                Set<String> seen = new HashSet<>();
                for (String current = name; current != null; current = parents.get(current))
                    require(seen.add(current), "Cycle in process hierarchy: " + name);
                seen.clear();
                for (String current = name; current != null; ) {
                    require(seen.add(current), "Cycle in predecessor chain: " + name);
                    String predecessor = nodes.get(current).path("predecessorProcessType").asText(null);
                    require(predecessor == null || nodes.containsKey(predecessor), "Unknown predecessor: " + predecessor);
                    current = predecessor;
                }
            }
            List<Map<String,Object>> processes = new ArrayList<>();
            Map<String,Object> definitions = new LinkedHashMap<>();
            for (var entry : nodes.entrySet()) {
                String name = entry.getKey(); JsonNode p = entry.getValue();
                List<String> children = children(p);
                String implementation = p.path("implementation").asText("custom");
                require(Set.of("custom","fileSplit","fileRead").contains(implementation), "Unknown implementation: " + implementation);
                require(!implementation.equals("fileRead") || children.isEmpty(), "fileRead must be a leaf");
                require(!implementation.equals("fileSplit") || children.size() == 1, "fileSplit requires exactly one child type");
                List<Map<String,String>> fields = fields(p);
                if (!implementation.equals("custom")) requireFilename(p);
                if (implementation.equals("fileSplit")) requireFilename(nodes.get(children.get(0)));
                String type = p.path("inputType").asText(name + "In");
                String argsChoice = p.path("predecessorArgs").asText("BOTH");
                require(Set.of("INPUT","OUTPUT","BOTH").contains(argsChoice), "Invalid predecessorArgs");
                Map<String,Object> d = new LinkedHashMap<>();
                d.put("processType",name); d.put("leaf",children.isEmpty()); d.put("args",pkg + ".model." + type);
                if (parents.containsKey(name)) d.put("parentProcessType",parents.get(name));
                if (p.has("predecessorProcessType")) d.put("predecessorProcessType",text(p,"predecessorProcessType"));
                d.put("predecessorArgs",argsChoice);
                Map<String,String> config = new LinkedHashMap<>();
                if (p.has("config")) {
                    require(p.get("config").isObject(), "config must be a string-valued object");
                    p.get("config").fields().forEachRemaining(e -> {
                        require(e.getValue().isTextual(), "config values must be strings"); config.put(e.getKey(),e.getValue().asText());
                    });
                }
                if (implementation.equals("fileSplit")) {
                    int bytes = Integer.parseInt(config.getOrDefault("chunkBytes","1048576"));
                    require(bytes > 0 && bytes <= 16777216, "chunkBytes must be 1..16777216");
                }
                d.put("config",config); definitions.put(name,d);
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("name",name); m.put("inputType",type); m.put("fields",fields); m.put("leaf",children.isEmpty());
                m.put("composite",!children.isEmpty()); m.put("serviceId",Character.toLowerCase(name.charAt(0)) + name.substring(1));
                m.put("fileSplit",implementation.equals("fileSplit")); m.put("fileRead",implementation.equals("fileRead"));
                m.put("fileExample",implementation.equals("fileSplit") && fileFamily(nodes, children.get(0)));
                m.put("custom",implementation.equals("custom"));
                m.put("children",children.stream().map(c -> Map.of("name",c,"inputType",nodes.get(c).path("inputType").asText(c + "In"))).toList());
                processes.add(m);
            }
            List<Map<String,Object>> crons = crons(spec, nodes);
            input.put("packageName",pkg); input.put("Application",Character.toUpperCase(app.charAt(0)) + app.substring(1));
            input.put("processes",processes); input.put("databaseDefinitions","database".equals(input.get("definitionSource")));
            input.put("defsJson",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("processMap",definitions)));
            input.put("cronsJson",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(crons));
            input.put("processCount",processes.size()); input.put("hasCrons",!crons.isEmpty());
            input.put("hasFileWorkers",processes.stream().anyMatch(p -> Boolean.TRUE.equals(p.get("fileSplit")) || Boolean.TRUE.equals(p.get("fileRead"))));
            // Rendering XML must not permit unsafe coordinates or arbitrary template/code injection.
            require(input.get("applicationVersion").toString().matches("[A-Za-z0-9_.-]+"), "Invalid applicationVersion");
            Path destination = Path.of(input.get("destFolder").toString()).resolve(app);
            require(!java.nio.file.Files.exists(destination), "Destination already exists: " + destination);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid process-management blueprint input: " + e.getMessage(), e);
        }
    }

    private List<Map<String,Object>> crons(JsonNode spec, Map<String,JsonNode> nodes) {
        if (!spec.has("cronTriggers")) return List.of();
        require(spec.get("cronTriggers").isArray(), "cronTriggers must be an array");
        List<Map<String,Object>> result = new ArrayList<>(); Set<String> names = new HashSet<>();
        for (JsonNode cron : spec.get("cronTriggers")) {
            allowed(cron, Set.of("name","processType","cronExpression","timezone","tenant","enabled","args"));
            String name = text(cron,"name"), type = text(cron,"processType");
            require(name.length() <= 255 && names.add(name), "Duplicate or overlong cron name: " + name);
            require(nodes.containsKey(type), "Unknown cron process type: " + type);
            String tenant = text(cron,"tenant"); require(tenant.matches("[A-Za-z0-9_.-]{1,128}"), "Invalid cron tenant");
            String zone = cron.path("timezone").asText("UTC"); ZoneId.of(zone);
            String expression = text(cron,"cronExpression");
            require(org.quartz.CronExpression.isValidExpression(expression), "Invalid Quartz cronExpression");
            JsonNode args = cron.path("args"); require(args.isObject(), "Cron args must be an object");
            Set<String> fieldNames = new HashSet<>();
            for (var field : fields(nodes.get(type))) {
                String fieldName = field.get("name"); fieldNames.add(fieldName);
                require(args.hasNonNull(fieldName), "Missing cron argument: " + fieldName);
                JsonNode value = args.get(fieldName);
                String javaType = field.get("javaType");
                require(switch (javaType) {
                    case "String" -> value.isTextual(); case "Boolean" -> value.isBoolean();
                    case "Integer" -> value.isIntegralNumber() && value.canConvertToInt();
                    case "Long" -> value.isIntegralNumber() && value.canConvertToLong(); default -> value.isNumber();
                }, "Wrong type for cron argument: " + fieldName);
            }
            args.fieldNames().forEachRemaining(f -> require(fieldNames.contains(f), "Unknown cron argument: " + f));
            require(!cron.has("enabled") || cron.get("enabled").isBoolean(), "enabled must be boolean");
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("name",name); m.put("tenant",tenant); m.put("cronExpression",expression); m.put("timezone",zone);
            m.put("enabled",cron.path("enabled").asBoolean(false));
            m.put("payload",Map.of("processDefName",type,"args",JSON.convertValue(args,Map.class)));
            result.add(m);
        }
        return result;
    }
    private static List<String> children(JsonNode p) {
        if (!p.has("children")) return List.of();
        require(p.get("children").isArray(), "children must be an array");
        List<String> result = new ArrayList<>();
        for (JsonNode child : p.get("children")) { require(child.isTextual(), "children must contain process type names"); result.add(child.asText()); }
        return result;
    }
    private static boolean fileFamily(Map<String,JsonNode> nodes, String name) {
        JsonNode process=nodes.get(name);
        String implementation=process.path("implementation").asText("custom");
        List<String> children=children(process);
        return (implementation.equals("fileRead") && children.isEmpty())
            || (implementation.equals("fileSplit") && children.size()==1 && fileFamily(nodes,children.get(0)));
    }
    private static List<Map<String,String>> fields(JsonNode p) {
        require(p.path("fields").isObject(), "fields must map field names to types");
        List<Map<String,String>> result = new ArrayList<>();
        p.get("fields").fields().forEachRemaining(e -> {
            String name = identifier(e.getKey());
            require(e.getValue().isTextual() && TYPES.containsKey(e.getValue().asText()), "Unsupported field type for " + name);
            result.add(Map.of("name",name,"javaType",TYPES.get(e.getValue().asText())));
        });
        return result;
    }
    private static void requireFilename(JsonNode p) {
        require("String".equals(TYPES.get(p.path("fields").path("filename").asText())), "File workers require filename:string");
    }
    private static void allowed(JsonNode object, Set<String> keys) {
        require(object.isObject(), "Expected an object");
        object.fieldNames().forEachRemaining(k -> require(keys.contains(k), "Unknown specification field: " + k));
    }
    private static String text(JsonNode node, String key) {
        require(node.path(key).isTextual() && !node.get(key).asText().isBlank(), key + " must be a nonblank string");
        return node.get(key).asText();
    }
    private static String identifier(String value) {
        require(value.matches("[A-Za-z][A-Za-z0-9_]*") && SourceVersion.isIdentifier(value) && !SourceVersion.isKeyword(value),
            "Invalid Java identifier: " + value);
        return value;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
