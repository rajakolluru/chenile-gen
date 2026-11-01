package org.chenile.jgen.template.chain;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotReplacerTest {
    @TempDir Path temporary;

    @Test void turnsDotMarkersIntoDotsInFileAndFolderNames() throws Exception {
        Path templates = temporary.resolve("templates");
        write(templates.resolve("__project__/_DoT_gitignore"), "target/\n");
        write(templates.resolve("__project__/_DoT_github/workflows/ci.yml"), "name: ci\n");
        write(templates.resolve("__project__/__project___DoT_properties.mustache"), "name={{project}}\n");
        write(templates.resolve("__project__/README.md"), "plain\n");

        BlueprintConfig blueprint = new BlueprintConfig();
        blueprint.name = "dot-test";
        blueprint.templateFolder = templates.toString();
        InputField project = new InputField();
        project.name = "project"; project.type = FieldType.STRING;
        blueprint.inputFields = List.of(project);

        Config config = new ConfigProvider().obtainDefaultConfig();
        Path out = temporary.resolve("out");
        Map<String, Object> map = new HashMap<>(new BlueprintInputService().buildInputMap(blueprint, config, Map.of("project", "demo")));
        map.put("destFolder", out.toString());
        new BlueprintExecutor().execute(blueprint, config, map);

        Path generated = out.resolve("demo");
        assertEquals("target/\n", Files.readString(generated.resolve(".gitignore")));
        assertTrue(Files.isRegularFile(generated.resolve(".github/workflows/ci.yml")));
        assertEquals("name=demo\n", Files.readString(generated.resolve("demo.properties")));
        assertTrue(Files.isRegularFile(generated.resolve("README.md")));
        try (var paths = Files.walk(generated)) {
            assertTrue(paths.noneMatch(p -> p.getFileName().toString().contains(DotReplacer.DOT)));
        }
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
