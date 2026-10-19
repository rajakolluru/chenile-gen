package org.chenile.jgen.template.chain;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.ProcessorFactory;
import org.chenile.owiz.config.impl.XmlOrchConfigurator;
import org.chenile.owiz.impl.OrchExecutorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GenericInputValidatorTest {
    @TempDir Path temporary;

    private BlueprintConfig blueprint() {
        var blueprint = new BlueprintConfig();
        blueprint.name = "custom";
        var project = new InputField(); project.name = "project";
        blueprint.inputFields = List.of(project);
        return blueprint;
    }

    private Map<String,Object> input() {
        return new HashMap<>(Map.of("project", "example", "destFolder", temporary.resolve("output").toString()));
    }

    @Test void invalidInputsStopTheChainBeforeHooksAndFileWrites() {
        var blueprint = blueprint();
        var called = new AtomicBoolean();
        blueprint.postInputCaptureHook = map -> called.set(true);
        var input = input(); input.put("company", "class");
        var failure = assertThrows(IllegalArgumentException.class, () -> new BlueprintExecutor().execute(
                blueprint, new ConfigProvider().obtainDefaultConfig(), input));
        assertTrue(failure.getMessage().contains("identifier"));
        assertFalse(called.get());
        assertFalse(Files.exists(temporary.resolve("output")));
        var dependencies = new InputField(); dependencies.name = "runtimeDependencies";
        dependencies.type = FieldType.POM_Dependencies;
        blueprint.inputFields = List.of(blueprint.inputFields.get(0), dependencies);
        var malformed = input(); malformed.put("runtimeDependencies", "not-an-array");
        failure = assertThrows(IllegalArgumentException.class, () -> new BlueprintExecutor().execute(
                blueprint, new ConfigProvider().obtainDefaultConfig(), malformed));
        assertTrue(failure.getMessage().contains("runtimeDependencies"));
        assertFalse(called.get());
        assertFalse(Files.exists(temporary.resolve("output")));
    }

    @Test void validatesBlueprintsWithoutHooks() {
        var input = input(); input.put("project", "../escape");
        assertThrows(IllegalArgumentException.class, () -> new BlueprintExecutor().execute(
                blueprint(), new ConfigProvider().obtainDefaultConfig(), input));
        assertFalse(Files.exists(temporary.resolve("output")));
    }

    @Test void metadataOverridesProjectFieldPrefixAndRegistryRequirement() throws Exception {
        var blueprint = blueprint();
        blueprint.projectNameField = "customName";
        blueprint.projectDirectoryPrefix = "prefix-";
        blueprint.registryClientFlag = "registryClient";
        var context = new TemplateContext(); context.blueprintConfig = blueprint;
        context.map = new HashMap<>(new ConfigProvider().getConfigAsMap(new ConfigProvider().obtainDefaultConfig()));
        context.map.putAll(input()); context.map.put("customName", "different");
        context.map.put("registryClient", true);
        var validator = new GenericInputValidator();
        assertThrows(IllegalArgumentException.class, () -> validator.execute(context));
        context.map.put("serviceRegistryUrl", "https://registry.example.com");
        assertDoesNotThrow(() -> validator.execute(context));
        Files.createDirectories(temporary.resolve("output/prefix-different"));
        var failure = assertThrows(IllegalArgumentException.class, () -> validator.execute(context));
        assertTrue(failure.getMessage().contains("already exists"));
    }

    @Test void rejectsMissingProjectMetadata() {
        var context = new TemplateContext(); context.blueprintConfig = new BlueprintConfig();
        context.map = input();
        assertThrows(IllegalArgumentException.class, () -> new GenericInputValidator().execute(context));
    }

    @Test void validChainDefaultsConfigThenValidatesThenRunsHookAndGeneratesFiles() throws Exception {
        var blueprint = blueprint();
        Path templates = Files.createDirectories(temporary.resolve("templates/__project__"));
        Files.writeString(templates.resolve("result.txt.mustache"), "{{company}}/{{message}}");
        blueprint.templateFolder = templates.getParent().toString();
        var parent = new InputField(); parent.name = "super_pom"; parent.type = FieldType.SuperPOM;
        var dependencies = new InputField(); dependencies.name = "runtimeDependencies";
        dependencies.type = FieldType.POM_Dependencies;
        blueprint.inputFields = List.of(blueprint.inputFields.get(0), parent, dependencies);
        blueprint.postInputCaptureHook = map -> {
            assertEquals("mycompany", map.get("company"));
            assertEquals("shared-parent", map.get("parentArtifactId"));
            assertEquals("${revision}", map.get("parentVersion"));
            assertEquals("", map.get("parentRelativePath"));
            assertEquals("library", ((Map<?, ?>) ((List<?>) map.get("runtimeDependencies")).get(0)).get("artifactId"));
            assertFalse(Files.exists(temporary.resolve("output")));
            map.put("message", "hook-ran");
        };
        var context = new TemplateContext(); context.blueprintConfig = blueprint;
        context.config = new ConfigProvider().obtainDefaultConfig(); context.map = input(); context.isJar = false;
        context.map.put("super_pom", Map.of("groupId", "example", "artifactId", "shared-parent", "version", "${revision}", "relativePath", ""));
        context.map.put("runtimeDependencies", List.of(Map.of("groupId", "example", "artifactId", "library", "version", "1.0.0")));
        var config = new XmlOrchConfigurator<TemplateContext>();
        config.setBeanFactoryAdapter(new ProcessorFactory("org.chenile.jgen.template.chain."));
        config.setFilename("org/chenile/jgen/template/processors.xml");
        var executor = new OrchExecutorImpl<TemplateContext>(); executor.setOrchConfigurator(config);
        executor.execute(context);
        assertEquals("mycompany/hook-ran", Files.readString(temporary.resolve("output/example/result.txt")));
    }
}
