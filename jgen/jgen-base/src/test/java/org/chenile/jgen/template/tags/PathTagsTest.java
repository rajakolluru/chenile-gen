package org.chenile.jgen.template.tags;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.provenance.GenerationRecord;
import org.chenile.jgen.provenance.ProvenanceRecorder;
import org.chenile.jgen.template.model.TemplateContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathTagsTest {
    @TempDir Path temporary;

    /** An example extension: {@code _UP_name} becomes {@code NAME}. */
    public static class UpperTag implements PathTag {
        @Override public String name() { return "upper"; }
        @Override public int order() { return 250; }   // after placeholders, before dots
        @Override public boolean matches(String name) { return name.startsWith("_UP_"); }
        @Override public void process(File file, TemplateContext context) {
            String rest = file.getName().substring(4);
            assertTrue(file.renameTo(new File(file.getParentFile(), rest.toUpperCase())));
        }
    }

    @AfterEach void forget() {
        PathTags.reset();
    }

    @Test void builtInsRunInTheirDocumentedOrder() {
        assertEquals(List.of("filelist", "placeholder", "dot", "conditional"),
                PathTags.all().stream().map(PathTag::name).toList());
    }

    @Test void anExtensionTagRunsAtItsOrderAndIsRecorded() throws Exception {
        PathTags.register(new UpperTag());
        assertEquals(List.of("filelist", "placeholder", "upper", "dot", "conditional"),
                PathTags.all().stream().map(PathTag::name).toList());
        Path templates = temporary.resolve("templates");
        // placeholder runs first (_UP_x__project__.txt -> _UP_xdemo.txt), then upper (-> XDEMO.TXT)
        write(templates.resolve("__project__/_UP_x__project__.txt"), "x\n");
        write(templates.resolve("__project__/_DoT_gitignore"), "target/\n");
        write(templates.resolve("__project__/%%withExtras=true%%/extra.txt"), "extra\n");

        Path generated = generate(templates, Map.of("project", "demo", "withExtras", "true"));
        assertTrue(Files.isRegularFile(generated.resolve("XDEMO.TXT")), "upper ran after placeholder");
        assertTrue(Files.isRegularFile(generated.resolve(".gitignore")));
        assertTrue(Files.isRegularFile(generated.resolve("extra.txt")));

        GenerationRecord record = ProvenanceRecorder.read(generated);
        assertEquals(List.of("tag:upper"), record.extensions);
    }

    @Test void extensionsAreFoundWithServiceLoader() throws Exception {
        Path jar = temporary.resolve("ext");
        write(jar.resolve("META-INF/services/" + PathTag.class.getName()), UpperTag.class.getName() + "\n");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, previous)) {
            thread.setContextClassLoader(loader);
            PathTags.reset();
            assertTrue(PathTags.all().stream().anyMatch(tag -> tag.name().equals("upper")));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test void tagNamesMustBeUnique() {
        PathTags.register(new UpperTag());
        PathTags.register(new UpperTag() { });
        var error = assertThrows(IllegalStateException.class, PathTags::all);
        assertTrue(error.getMessage().contains("upper"), error.getMessage());
    }

    @Test void extensionsCannotTakeABuiltInName() {
        PathTags.register(new UpperTag() {
            @Override public String name() { return "dot"; }
        });
        assertThrows(IllegalStateException.class, PathTags::all);
    }

    @Test void regenerationNeedsTheTagsTheProjectWasGeneratedWith() throws Exception {
        PathTags.register(new UpperTag());
        Path templates = temporary.resolve("templates");
        write(templates.resolve("__project__/_UP_readme"), "x\n");
        Path generated = generate(templates, Map.of("project", "demo"));
        PathTags.reset();
        GenerationRecord record = ProvenanceRecorder.read(generated);
        var error = assertThrows(IllegalStateException.class,
                () -> org.chenile.jgen.provenance.Regenerator.requireRecordedExtensions(record));
        assertTrue(error.getMessage().contains("upper"), error.getMessage());
    }

    private Path generate(Path templates, Map<String, Object> values) throws Exception {
        BlueprintConfig blueprint = new BlueprintConfig();
        blueprint.name = "tags-test";
        blueprint.templateFolder = templates.toString();
        InputField project = new InputField();
        project.name = "project"; project.type = FieldType.STRING;
        InputField extras = new InputField();
        extras.name = "withExtras"; extras.type = FieldType.STRING; extras.required = false;
        blueprint.inputFields = List.of(project, extras);
        Config config = new ConfigProvider().obtainDefaultConfig();
        Path out = temporary.resolve("out");
        Map<String, Object> map = new HashMap<>(new BlueprintInputService().buildInputMap(blueprint, config, values));
        map.put("destFolder", out.toString());
        new BlueprintExecutor().execute(blueprint, config, map);
        return out.resolve("demo");
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
