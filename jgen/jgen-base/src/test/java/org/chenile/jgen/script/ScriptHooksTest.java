package org.chenile.jgen.script;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScriptHooksTest {
    @TempDir Path temporary;
    private String name;

    @AfterEach void forget() {
        if (name != null) Registry.blueprints.remove(name);
    }

    /** A template-only blueprint folder with the given scripts (hook name to script source). */
    private BlueprintConfig blueprint(Map<String, String> scripts) throws Exception {
        name = "script-demo-" + System.nanoTime();
        Path root = temporary.resolve(name);
        Files.createDirectories(root.resolve("META-INF"));
        Files.createDirectories(root.resolve(name + "-template"));
        StringBuilder declared = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> script : scripts.entrySet()) {
            String path = "scripts/" + script.getKey() + ".js";
            Files.createDirectories(root.resolve("scripts"));
            Files.writeString(root.resolve(path), script.getValue());
            declared.append(i++ == 0 ? "" : ",").append('"').append(script.getKey()).append("\":\"").append(path).append('"');
        }
        Files.writeString(root.resolve("META-INF/blueprint.json"), """
                {"name":"%s","description":"scripts","templateFolder":"%s-template",
                 "inputFields":[{"name":"project","type":"STRING"},{"name":"entity","type":"STRING"}],
                 "scripts":{%s}}
                """.formatted(name, name, declared));
        List<BlueprintConfig> loaded = Registry.loadTemplateOnly(root);
        return loaded.get(0);
    }

    private Map<String, Object> inputs() {
        Map<String, Object> map = new HashMap<>();
        map.put("project", "orders");
        map.put("entity", "purchase order");
        map.put("destFolder", temporary.resolve("out").toString());
        return map;
    }

    @Test void postInputCaptureAddsValuesForTheTemplates() throws Exception {
        BlueprintConfig bp = blueprint(Map.of("postInputCapture", """
                function postInputCapture(inputs, jgen) {
                  return { Entity: jgen.pascalCase(inputs.entity), entityVar: jgen.camelCase(inputs.entity) };
                }"""));
        Map<String, Object> map = inputs();
        bp.postInputCaptureHook.accept(map);
        assertEquals("PurchaseOrder", map.get("Entity"));
        assertEquals("purchaseOrder", map.get("entityVar"));
    }

    @Test void postProcessWorksOnTheGeneratedProject() throws Exception {
        BlueprintConfig bp = blueprint(Map.of("postProcess", """
                function postProcess(inputs, project, jgen) {
                  project.write("docs/README.md", "# " + inputs.project);
                  project.move("docs/README.md", "README.md");
                }"""));
        Map<String, Object> map = inputs();
        bp.postProcessHook.accept(map);
        assertEquals("# orders", Files.readString(temporary.resolve("out/orders/README.md")));
        assertFalse(Files.exists(temporary.resolve("out/orders/docs/README.md")));
    }

    @Test void childBlueprintInputsAreComputedByTheScript() throws Exception {
        BlueprintConfig bp = blueprint(Map.of("childBlueprintInputs", """
                function childBlueprintInputs(inputs, child, attributes, jgen) {
                  return { project: inputs.project + "-" + attributes.suffix, kind: child };
                }"""));
        Map<String, Object> child = bp.childBlueprintInputs.computeChildInputs(inputs(), "wfservice", Map.of("suffix", "wf"));
        assertEquals("orders-wf", child.get("project"));
        assertEquals("wfservice", child.get("kind"));
    }

    @Test void scriptsCannotLeaveTheProjectFolder() throws Exception {
        BlueprintConfig bp = blueprint(Map.of("postProcess", """
                function postProcess(inputs, project) { project.write("../../escaped.txt", "x"); }"""));
        assertThrows(IllegalStateException.class, () -> bp.postProcessHook.accept(inputs()));
        assertFalse(Files.exists(temporary.resolve("escaped.txt")));
    }

    @Test void scriptsCannotReachJava() throws Exception {
        BlueprintConfig bp = blueprint(Map.of("postProcess", """
                function postProcess(inputs, project) { Java.type("java.lang.Runtime").getRuntime().exec("true"); }"""));
        assertThrows(IllegalStateException.class, () -> bp.postProcessHook.accept(inputs()));
    }

    @Test void scriptsCannotCallUnexportedMethods() throws Exception {
        BlueprintConfig bp = blueprint(Map.of("postProcess", """
                function postProcess(inputs, project) { project.getClass().forName("java.lang.System"); }"""));
        assertThrows(IllegalStateException.class, () -> bp.postProcessHook.accept(inputs()));
    }

    @Test void runawayLoopsAreStopped() throws Exception {
        BlueprintConfig bp = blueprint(Map.of("postInputCapture", """
                function postInputCapture(inputs) { while (true) {} }"""));
        var error = assertThrows(IllegalStateException.class, () -> bp.postInputCaptureHook.accept(inputs()));
        assertTrue(error.getMessage().contains("statements"), error.getMessage());
    }

    @Test void syntaxErrorsFailTheBlueprintAtLoad() {
        var error = assertThrows(IllegalArgumentException.class, () -> blueprint(Map.of("postProcess", """
                function postProcess(inputs { }""")));
        assertTrue(error.getMessage().contains("does not load"), error.getMessage());
    }

    @Test void theHookFunctionMustExist() {
        var error = assertThrows(IllegalArgumentException.class, () -> blueprint(Map.of("postProcess", """
                function somethingElse() { }""")));
        assertTrue(error.getMessage().contains("must define function postProcess"), error.getMessage());
    }

    @Test void unknownHooksAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> blueprint(Map.of("onSave", "function onSave() {}")));
    }
}
