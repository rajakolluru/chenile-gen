package org.chenile.jgen.template.chain;

import org.apache.commons.io.FileUtils;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.BlueprintMarker;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Expands child blueprints after the enclosing blueprint has finished rendering its own templates. */
public class BlueprintExpander implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) throws Exception {
        List<Invocation> invocations = new ArrayList<>();
        try (var paths = Files.walk(context.destination.toPath())) {
            for (Path path : paths.filter(p -> BlueprintMarker.isMarker(p.getFileName().toString())).sorted().toList()) {
                BlueprintMarker marker = BlueprintMarker.parse(path.getFileName().toString());
                requirePlaceholder(path);
                if (context.blueprintConfig.childBlueprintInputs == null)
                    throw new IllegalArgumentException("Blueprint " + context.blueprintConfig.name
                            + " must register childBlueprintInputs to expand " + path);
                BlueprintConfig child = Registry.blueprints.get(marker.blueprintName());
                if (child == null) throw new IllegalArgumentException("Unknown child blueprint " + marker.blueprintName() + " at " + path);
                invocations.add(new Invocation(path, marker, child));
            }
        }
        // Finish traversal before generating children; their outputs belong to their own pipeline.
        for (Invocation invocation : invocations) expand(context, invocation);
    }

    private void expand(TemplateContext parent, Invocation invocation) throws Exception {
        Path markerPath = invocation.path();
        Path destination = markerPath.getParent();
        List<Path> stages = new ArrayList<>();
        try {
            List<Map<String, Object>> scopes = invocationScopes(parent.map, invocation.marker());
            List<Path> generated = new ArrayList<>();
            java.util.Set<Path> targets = new java.util.HashSet<>();
            for (Map<String, Object> scope : scopes) {
                Map<String, Object> computed = parent.blueprintConfig.childBlueprintInputs.computeChildInputs(
                        scope, invocation.marker().blueprintName(), invocation.marker().attributes());
                if (computed == null) throw new IllegalArgumentException("childBlueprintInputs returned null");
                Path staging = Files.createTempDirectory(destination, ".jgen-child-");
                stages.add(staging);
                Map<String, Object> supplied = copyInputs(computed);
                supplied.put("destFolder", staging.toString());
                Map<String, Object> inputs = new BlueprintInputService().buildInputMap(invocation.blueprint(), parent.config, supplied);
                inputs.put("destFolder", staging.toString());
                new BlueprintExecutor().executeChild(invocation.blueprint(), inputs, parent);
                List<Path> outputs;
                try (var entries = Files.list(staging)) { outputs = entries.sorted().toList(); }
                if (outputs.isEmpty()) throw new IllegalArgumentException("Child blueprint generated no output");
                for (Path source : outputs) {
                    Path target = destination.resolve(source.getFileName());
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || !targets.add(target))
                        throw new IllegalArgumentException("Child output already exists or is duplicated: " + target);
                    generated.add(source);
                }
            }
            // Validate all loop iterations before publishing any of this marker's output.
            for (Path source : generated) Files.move(source, destination.resolve(source.getFileName()));
            FileUtils.deleteDirectory(markerPath.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot expand " + markerPath + " in blueprint "
                    + parent.blueprintConfig.name + ": " + e.getMessage(), e);
        } finally {
            for (Path staging : stages) FileUtils.deleteDirectory(staging.toFile());
        }
    }

    private List<Map<String, Object>> invocationScopes(Map<String, Object> input, BlueprintMarker marker) {
        String collection = marker.attributes().get("forEach");
        String alias = marker.attributes().get("as");
        if (collection == null) {
            if (alias != null) throw new IllegalArgumentException("as requires forEach");
            return List.of(copyInputs(input));
        }
        if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("forEach requires a valid as attribute");
        if (!collection.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
            throw new IllegalArgumentException("Invalid forEach collection: " + collection);
        Object value = input;
        for (String segment : collection.split("\\."))
            value = value instanceof Map<?, ?> map ? map.get(segment) : null;
        if (!(value instanceof List<?> items))
            throw new IllegalArgumentException("forEach must reference a list: " + collection);
        List<Map<String, Object>> scopes = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> scope = copyInputs(input);
            scope.put(alias, copyValue(item)); scopes.add(scope);
        }
        return scopes;
    }

    private void requirePlaceholder(Path marker) throws Exception {
        if (!Files.isDirectory(marker, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Blueprint marker must be a directory: " + marker);
        try (var contents = Files.list(marker)) {
            for (Path entry : contents.toList()) {
                if (!entry.getFileName().toString().equals(".gitkeep")
                        || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || Files.size(entry) != 0)
                    throw new IllegalArgumentException("Blueprint marker must be empty (an empty .gitkeep is allowed): " + marker);
            }
        }
    }

    private static Map<String, Object> copyInputs(Map<String, Object> input) {
        Map<String, Object> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(key, copyValue(value)));
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, copyValue(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(copyValue(item)));
            return copy;
        }
        return value;
    }

    private record Invocation(Path path, BlueprintMarker marker, BlueprintConfig blueprint) { }
}
