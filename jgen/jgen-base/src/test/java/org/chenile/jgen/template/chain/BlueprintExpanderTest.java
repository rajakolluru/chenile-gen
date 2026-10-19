package org.chenile.jgen.template.chain;

import org.chenile.jgen.blueprints.*;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.ProcessorFactory;
import org.chenile.owiz.config.impl.XmlOrchConfigurator;
import org.chenile.owiz.impl.OrchExecutorImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintExpanderTest {
    @TempDir Path temporary;
    private final List<String> registered = new ArrayList<>();

    @AfterEach void unregister() { registered.forEach(Registry.blueprints::remove); }

    private BlueprintConfig blueprint(String name) throws Exception {
        var blueprint = new BlueprintConfig(); blueprint.name = "aggregation-test-" + name;
        blueprint.templateFolder = Files.createDirectories(temporary.resolve(name + "-templates")).toString();
        List<InputField> fields = new ArrayList<>();
        for (String key : List.of("project", "label", "parentOnly")) {
            var field = new InputField(); field.name = key; field.type = FieldType.STRING;
            if (!key.equals("project")) field.defaultValue = "default";
            fields.add(field);
        }
        var parent = new InputField(); parent.name = "super_pom"; parent.type = FieldType.SuperPOM; fields.add(parent);
        var dependencies = new InputField(); dependencies.name = "libraries"; dependencies.type = FieldType.POM_Dependencies; fields.add(dependencies);
        blueprint.inputFields = fields;
        Registry.registerBlueprint(blueprint); registered.add(blueprint.name);
        return blueprint;
    }

    private Path template(BlueprintConfig blueprint, String path, String content) throws Exception {
        Path file = Path.of(blueprint.templateFolder).resolve(path);
        Files.createDirectories(file.getParent()); Files.writeString(file, content); return file;
    }

    private TemplateContext context(BlueprintConfig blueprint) {
        var context = new TemplateContext(); context.blueprintConfig = blueprint;
        context.config = new ConfigProvider().obtainDefaultConfig(); context.isJar = false;
        context.map = new BlueprintInputService().buildInputMap(blueprint, context.config,
                Map.of("project", "application", "parentOnly", "parent-value", "destFolder", temporary.resolve("output").toString()));
        // Test blueprints do not declare destFolder; the executor also accepts runtime placement.
        context.map.put("destFolder", temporary.resolve("output").toString());
        return context;
    }

    private void execute(TemplateContext context) throws Exception {
        var configurator = new XmlOrchConfigurator<TemplateContext>();
        configurator.setBeanFactoryAdapter(new ProcessorFactory("org.chenile.jgen.template.chain."));
        configurator.setFilename("org/chenile/jgen/template/processors.xml");
        var executor = new OrchExecutorImpl<TemplateContext>(); executor.setOrchConfigurator(configurator);
        executor.execute(context);
    }

    private void noStaging(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith(".jgen-child-")));
        }
    }

    @Test void expandsRepeatedNestedChildrenWithIndependentMapsAndRunsFinalHookAfterExpansion() throws Exception {
        var parent = blueprint("parent"); var nested = blueprint("nested"); var leaf = blueprint("leaf");
        template(parent, "__project__/parent.txt.mustache", "{{parentOnly}}");
        template(parent, "__project__/%{{" + nested.name + " role=server label='server label'}}/.gitkeep", "");
        template(parent, "__project__/%%include=true%%/%{{" + nested.name + " role=worker label='worker label'}}/.gitkeep", "");
        template(parent, "__project__/%%disabled=true%%/%{{missing-blueprint}}/.gitkeep", "");
        template(nested, "__project__/%{{" + leaf.name + " name=listener}}/.gitkeep", "");
        template(leaf, "__project__/result.txt.mustache", "{{company}}/{{label}}/{{parentOnly}}/{{parentArtifactId}}/{{#libraries}}{{artifactId}}{{/libraries}}");
        var calls = new AtomicInteger();
        parent.childBlueprintInputs = (input, type, attributes) -> {
            assertEquals(nested.name, type); calls.incrementAndGet();
            ((Map<String, Object>) input.get("settings")).put("value", "child mutation");
            input.put("parentOnly", "changed");
            return Map.of("project", attributes.get("role"), "label", attributes.get("label"));
        };
        nested.childBlueprintInputs = (input, type, attributes) -> {
            assertEquals(leaf.name, type); calls.incrementAndGet();
            return Map.of("project", input.get("project") + "-" + attributes.get("name"), "label", input.get("label"),
                    "super_pom", Map.of("groupId", "example", "artifactId", "shared", "version", "1.0.0"),
                    "libraries", List.of(Map.of("groupId", "example", "artifactId", "library")));
        };
        leaf.postProcessHook = input -> {
            try { Files.writeString(Path.of(input.get("destFolder").toString(), input.get("project").toString(), "late.txt.mustache"), "{{parentOnly}}"); }
            catch (Exception e) { throw new RuntimeException(e); }
        };
        var context = context(parent); context.map.put("include", true); context.map.put("disabled", false);
        context.map.put("settings", new HashMap<>(Map.of("value", "original")));
        parent.postProcessHook = input -> {
            Path output = Path.of(input.get("destFolder").toString(), "application");
            assertTrue(Files.exists(output.resolve("server/server-listener/result.txt")));
            assertTrue(Files.exists(output.resolve("worker/worker-listener/result.txt")));
        };
        execute(context);
        Path output = temporary.resolve("output/application");
        assertEquals(4, calls.get());
        assertEquals("parent-value", context.map.get("parentOnly"));
        assertEquals("original", ((Map<?, ?>) context.map.get("settings")).get("value"));
        for (String role : List.of("server", "worker")) {
            assertEquals("mycompany/" + role + " label/default/shared/library", Files.readString(output.resolve(role + "/" + role + "-listener/result.txt")));
            assertEquals("{{parentOnly}}", Files.readString(output.resolve(role + "/" + role + "-listener/late.txt.mustache")));
        }
        try (var paths = Files.walk(output)) { assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("%{{"))); }
        noStaging(output);
    }

    @Test void rejectsCyclesAndCleansFailedChildStaging() throws Exception {
        var parent = blueprint("cycle"); var child = blueprint("cycle-child");
        template(parent, "__project__/%{{" + child.name + "}}/.gitkeep", "");
        template(child, "__project__/%{{" + parent.name + "}}/.gitkeep", "");
        parent.childBlueprintInputs = (input, type, attributes) -> Map.of("project", "child");
        child.childBlueprintInputs = (input, type, attributes) -> Map.of("project", "loop");
        var failure = assertThrows(IllegalStateException.class, () -> execute(context(parent)));
        assertTrue(failure.getMessage().contains("Recursive blueprint inclusion"));
        noStaging(temporary.resolve("output"));
    }

    @Test void rejectsMissingBlueprintsCallbacksAndNonemptyMarkers() throws Exception {
        var parent = blueprint("invalid"); var child = blueprint("valid-child");
        Path output = Files.createDirectories(temporary.resolve("output"));
        var context = context(parent); context.destination = output.toFile();
        Path marker = Files.createDirectory(output.resolve("%{{" + child.name + "}}"));
        var expander = new BlueprintExpander();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> expander.execute(context)).getMessage().contains("childBlueprintInputs"));
        parent.childBlueprintInputs = (input, type, attributes) -> Map.of("project", "child");
        Files.writeString(marker.resolve("unexpected.txt"), "preserve me");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> expander.execute(context)).getMessage().contains("must be empty"));
        Files.delete(marker.resolve("unexpected.txt")); Files.delete(marker);
        Files.createDirectory(output.resolve("%{{missing-blueprint}}"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> expander.execute(context)).getMessage().contains("Unknown child blueprint"));
        noStaging(output);
    }

    @Test void validatesChildInputsBeforeChildHooksAndPreservesExistingOutputOnCollision() throws Exception {
        var parent = blueprint("collision"); var child = blueprint("collision-child");
        template(parent, "__project__/same/result.txt", "original");
        template(parent, "__project__/%{{" + child.name + " role=worker}}/.gitkeep", "");
        template(child, "__project__/result.txt", "new");
        var calls = new AtomicInteger(); child.postInputCaptureHook = input -> calls.incrementAndGet();
        parent.childBlueprintInputs = (input, type, attributes) -> Map.of("project", "same", "libraries", "bad shape");
        var context = context(parent);
        assertTrue(assertThrows(IllegalStateException.class, () -> execute(context)).getMessage().contains("libraries"));
        assertEquals(0, calls.get()); noStaging(temporary.resolve("output"));
        parent.childBlueprintInputs = (input, type, attributes) -> Map.of("project", "same", "destFolder", temporary.resolve("escape").toString());
        var failure = assertThrows(IllegalStateException.class, () -> new BlueprintExpander().execute(context));
        assertTrue(failure.getMessage().contains("already exists"));
        assertEquals("original", Files.readString(temporary.resolve("output/application/same/result.txt")));
        assertFalse(Files.exists(temporary.resolve("escape")));
        noStaging(temporary.resolve("output"));
    }
    @Test void repeatsAChildForEachPreparedItemAndChecksTheWholeLoopBeforePublishing() throws Exception {
        var parent = blueprint("loop"); var child = blueprint("loop-child");
        template(child, "__project__/result.txt.mustache", "{{label}}");
        String markerName = "%{{" + child.name + " forEach=plan.processes as=process}}";
        var calls = new AtomicInteger();
        parent.childBlueprintInputs = (input, type, attributes) -> {
            calls.incrementAndGet();
            assertEquals("plan.processes", attributes.get("forEach"));
            return Map.of("project", input.get("process"), "label", input.get("process"));
        };
        for (List<String> items : List.of(List.of("one", "two"), List.<String>of(), List.of("same", "same"))) {
            Path output = Files.createTempDirectory(temporary, "loop-output");
            Path marker = Files.createDirectory(output.resolve(markerName));
            var context = context(parent); context.destination = output.toFile();
            context.map.put("plan", Map.of("processes", items));
            if (items.equals(List.of("same", "same"))) {
                assertTrue(assertThrows(IllegalStateException.class, () -> new BlueprintExpander().execute(context)).getMessage().contains("duplicated"));
                assertFalse(Files.exists(output.resolve("same"))); assertTrue(Files.exists(marker));
            } else {
                new BlueprintExpander().execute(context); assertFalse(Files.exists(marker));
                for (String item : items) assertEquals(item, Files.readString(output.resolve(item + "/result.txt")));
            }
            noStaging(output);
        }
        assertEquals(4, calls.get());
        var context = context(parent); context.destination = Files.createTempDirectory(temporary, "invalid-loop").toFile();
        Files.createDirectory(context.destination.toPath().resolve(markerName));
        assertTrue(assertThrows(IllegalStateException.class, () -> new BlueprintExpander().execute(context)).getMessage().contains("must reference a list"));
    }
}
