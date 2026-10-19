package org.chenile.jgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import org.chenile.jgen.blueprints.*;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ComposableBlueprintIntegrationTest {
    @TempDir Path temporary;
    private static final Map<String, String[]> TEMPLATES = Map.ofEntries(
        Map.entry("chenile-service", new String[]{"service-template/__service__/pom.xml.mustache", "service-template/__service__/__service__-service/pom.xml.mustache"}),
        Map.entry("chenile-headless-service", new String[]{"headless-service-template/__service__/pom.xml.mustache", "headless-service-template/__service__/__service__-service/pom.xml.mustache"}),
        Map.entry("wfservice", new String[]{"wfservice-template/__service__/pom.xml.mustache", "wfservice-template/__service__/__service__-service/pom.xml.mustache"}),
        Map.entry("wfcustom", new String[]{"wfcustom-template/__service__/pom.xml.mustache", "wfcustom-template/__service__/__service__-service/pom.xml.mustache"}),
        Map.entry("batch", new String[]{"batch-template/__batch__/pom.xml.mustache", "batch-template/__batch__/__batch__-service/pom.xml.mustache"}),
        Map.entry("minimonolith", new String[]{"monolith-template/__monolith__/pom.xml.mustache", "monolith-template/__monolith__/__monolith__-package/pom.xml.mustache"}),
        Map.entry("chenile-process-management", new String[]{"process-management-template/__application__/pom.xml.mustache", "process-management-template/__application__/__application__-server-service/pom.xml.mustache"}),
        Map.entry("mybatisQuery", new String[]{"mybatis-query-template/__namespace__/pom.xml.mustache"}),
        Map.entry("chenile-interceptor", new String[]{"interceptor-template/__interceptorName__/pom.xml.mustache"}),
        Map.entry("it", new String[]{"bdd-it/bdd-it-__app__/pom.xml.mustache"}),
        Map.entry("jgen-blueprint", new String[]{"blueprint-template/bp-__blueprintName__/pom.xml.mustache"}),
        Map.entry("chenile-ecosystem", new String[]{"ecosystem-template/pom.xml.mustache"}));

    private Map<String, Object> values() {
        var provider = new ConfigProvider();
        Map<String, Object> values = new HashMap<>(provider.getConfigAsMap(provider.obtainDefaultConfig()));
        for (String key : List.of("service", "namespace", "batch", "monolith", "application", "interceptorName", "app", "blueprintName", "ecosystem")) {
            values.put(key, "example"); values.put(key + "Version", "1.0.0");
        }
        values.put("serviceVersion", "1.0.0"); return values;
    }
    private Document render(String resource, Map<String, Object> input) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            var writer = new StringWriter();
            new DefaultMustacheFactory().compile(new InputStreamReader(stream), resource).execute(writer, input).flush();
            var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }
    private String xpath(Document document, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, document);
    }
    @Test void everyBlueprintDeclaresCompositionInputsAndRendersTheChosenParentAndDependencyDestination() throws Exception {
        for (var entry : TEMPLATES.entrySet()) {
            var blueprint = Registry.blueprints.get(entry.getKey()); assertNotNull(blueprint, entry.getKey());
            assertTrue(blueprint.inputFields.stream().anyMatch(f -> f.name.equals("super_pom") && f.type == FieldType.SuperPOM));
            assertTrue(blueprint.inputFields.stream().anyMatch(f -> f.name.equals("dependencies") && f.type == FieldType.POM_Dependencies));
            var input = values();
            input.put("super_pom", Map.of("groupId", "com.example.composed", "artifactId", "composition-parent", "version", "9.8.7", "relativePath", "../../pom.xml"));
            input.put("dependencies", List.of(Map.of("groupId", "com.example", "artifactId", "composition-library", "version", "1.2.3")));
            PomInputs.normalizeBlueprintInputs(blueprint, input);
            var parent = render(entry.getValue()[0], input);
            assertEquals("composition-parent", xpath(parent, "string(/*[local-name()='project']/*[local-name()='parent']/*[local-name()='artifactId'])"), entry.getKey());
            assertEquals("../../pom.xml", xpath(parent, "string(/*[local-name()='project']/*[local-name()='parent']/*[local-name()='relativePath'])"));
            var target = render(entry.getValue()[entry.getValue().length - 1], input);
            assertEquals("1", xpath(target, "count(/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='composition-library'])"), entry.getKey());
            if (entry.getValue().length > 1) {
                assertEquals("0", xpath(parent, "count(/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='artifactId']='composition-library'])"));
                assertNotEquals("composition-parent", xpath(target, "string(/*[local-name()='project']/*[local-name()='parent']/*[local-name()='artifactId'])"));
            }
            var defaults = values(); PomInputs.normalizeBlueprintInputs(blueprint, defaults);
            assertNotEquals("composition-parent", xpath(render(entry.getValue()[0], defaults), "string(/*[local-name()='project']/*[local-name()='parent']/*[local-name()='artifactId'])"));
        }
    }

    @Test void cliEmitsRunnableOptionalCompositionInputs() throws Exception {
        var json = new ObjectMapper();
        Path sample = temporary.resolve("sample.json");
        assertEquals(0, new picocli.CommandLine(new GenMain()).execute("-g", "minimonolith", "-o", sample.toString()));
        var emitted = json.readTree(sample.toFile());
        assertTrue(emitted.get("super_pom").isNull()); assertTrue(emitted.get("dependencies").isArray());
    }
    @Test void builtInNamesRejectInvalidJavaInputsWhilePreservingProjectAndRouteNames() {
        Map<String,String> javaNames = Map.of(
                "batch", "batch", "chenile-headless-service", "service",
                "chenile-interceptor", "interceptorName", "it", "app",
                "jgen-blueprint", "blueprintName", "minimonolith", "monolith",
                "mybatisQuery", "namespace", "chenile-service", "service");
        var provider = new ConfigProvider();
        for (var entry : javaNames.entrySet()) {
            var blueprint = Registry.blueprints.get(entry.getKey());
            var field = blueprint.inputFields.stream().filter(f -> f.name.equals(entry.getValue())).findFirst().orElseThrow();
            assertEquals(org.chenile.jgen.blueprints.model.ValidationType.JAVA_IDENTIFIER, field.validation, entry.getKey());
            assertTrue(org.chenile.jgen.util.FieldUtils.isValid(field, "orderService"), entry.getKey());
            for (String invalid : List.of("order-service", "class", "../escape", "1service")) {
                Map<String,Object> input = new HashMap<>(Map.of(field.name, invalid, "destFolder", temporary.toString()));
                var error = assertThrows(IllegalArgumentException.class, () -> new BlueprintExecutor().execute(
                        blueprint, provider.obtainDefaultConfig(), input), entry.getKey() + ": " + invalid);
                assertTrue(error.getMessage().contains(field.name), error.getMessage());
            }
        }
        var ecosystem = Registry.blueprints.get("chenile-ecosystem");
        for (String name : List.of("service", "monolith", "serviceRegistryMonolith", "headlessService", "headlessMonolith", "queryNamespace", "queryMonolith")) {
            var field = ecosystem.inputFields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
            assertFalse(org.chenile.jgen.util.FieldUtils.isValid(field, "broken-name"), name);
            assertTrue(org.chenile.jgen.util.FieldUtils.isValid(field, "orders"), name);
        }
        for (String name : List.of("wfservice", "wfcustom", "chenile-ecosystem")) {
            var field = Registry.blueprints.get(name).inputFields.get(0);
            assertTrue(org.chenile.jgen.util.FieldUtils.isValid(field, "order-service"), name);
        }
        var entity = Registry.blueprints.get("it").inputFields.stream().filter(f -> f.name.equals("entity")).findFirst().orElseThrow();
        assertTrue(org.chenile.jgen.util.FieldUtils.isValid(entity, "order-line"));
        assertFalse(org.chenile.jgen.util.FieldUtils.isValid(entity, "../escape"));
        assertFalse(Files.exists(temporary.resolve("order-service")));
    }

}
