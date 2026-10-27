package org.chenile.jgen.provenance;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.provenance.Regenerator.Change;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProvenanceTest {
    @TempDir Path temporary;
    private final Config config = new ConfigProvider().obtainDefaultConfig();
    private BlueprintConfig blueprint;

    @AfterEach void unregister() { if (blueprint != null) Registry.blueprints.remove(blueprint.name); }

    private BlueprintConfig blueprint() throws Exception {
        blueprint = new BlueprintConfig();
        blueprint.name = "provenance-test";
        blueprint.version = "1";
        blueprint.templateFolder = Files.createDirectories(temporary.resolve("templates")).toString();
        blueprint.inputFields = List.of(field("project", null), field("greeting", "hi"), field("extra", "n"));
        blueprint.inputFields.get(2).type = FieldType.BOOLEAN;
        Registry.registerBlueprint(blueprint);
        return blueprint;
    }

    private static InputField field(String name, String defaultValue) {
        InputField field = new InputField();
        field.name = name; field.type = FieldType.STRING; field.defaultValue = defaultValue;
        return field;
    }

    private void template(String path, String content) throws Exception {
        Path file = Path.of(blueprint.templateFolder).resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private Path generate(Map<String, Object> supplied) throws Exception {
        Path out = temporary.resolve("out");
        Map<String, Object> map = new HashMap<>(new BlueprintInputService().buildInputMap(blueprint, config, supplied));
        map.put("destFolder", out.toString());
        new BlueprintExecutor().execute(blueprint, config, map);
        return out.resolve(supplied.get("project").toString());
    }

    @Test void recordsBlueprintInputsAndFileHashes() throws Exception {
        blueprint();
        template("__project__/README.md.mustache", "Hello {{greeting}}");
        template("__project__/%%extra=true%%/extra.txt", "extra");
        Path project = generate(Map.of("project", "demo", "greeting", "hello"));

        GenerationRecord record = ProvenanceRecorder.read(project);
        assertEquals("provenance-test", record.blueprint.name);
        assertEquals("1", record.blueprint.version);
        assertEquals("demo", record.projectDirectory);
        assertEquals("hello", record.inputs.get("greeting"));
        assertEquals(Boolean.FALSE, record.inputs.get("extra"));
        assertFalse(record.inputs.containsKey("destFolder"));
        assertEquals(List.of("README.md"), List.copyOf(record.files.keySet()));
        assertEquals("mycompany", record.config.get("company"));
    }

    @Test void regenerationSeparatesBlueprintChangesFromHandEdits() throws Exception {
        blueprint();
        template("__project__/README.md.mustache", "Hello {{greeting}}");
        template("__project__/LICENSE", "v1");
        template("__project__/KEEP.md", "same");
        template("__project__/OLD.md", "old");
        Path project = generate(Map.of("project", "demo", "greeting", "hello"));

        // The user edits two files; the blueprint author changes three and adds and removes one.
        Files.writeString(project.resolve("README.md"), "Hello there, edited by hand");
        Files.writeString(project.resolve("KEEP.md"), "mine");
        template("__project__/README.md.mustache", "Hello {{greeting}}!");
        template("__project__/LICENSE", "v2");
        template("__project__/NEW.md", "new");
        Files.delete(Path.of(blueprint.templateFolder).resolve("__project__/OLD.md"));

        var report = new Regenerator().regenerate(project, temporary.resolve("regen"), null);

        assertEquals(List.of("README.md"), paths(report.only(Change.CONFLICT)));
        assertEquals(List.of("LICENSE"), paths(report.only(Change.SAFE_UPDATE)));
        assertEquals(List.of("KEEP.md"), paths(report.only(Change.KEEP_USER)));
        assertEquals(List.of("NEW.md"), paths(report.only(Change.NEW_FILE)));
        assertEquals(List.of("OLD.md"), paths(report.only(Change.SAFE_DELETE)));
        assertEquals("Hello there, edited by hand", Files.readString(project.resolve("README.md")), "original untouched");
        assertEquals("Hello hello!", Files.readString(report.regenerated().resolve("README.md")));
        assertTrue(Files.exists(Regenerator.writeReport(report)));
    }

    @Test void classificationCoversBothSidedChanges() {
        var base = Map.of("a", "1", "b", "1", "c", "1", "d", "1");
        var mine = Map.of("a", "2", "b", "1", "d", "1", "e", "9");       // c deleted by user, e added by user
        var theirs = Map.of("a", "2", "b", "1", "c", "2", "f", "1");     // a: same change both sides; d dropped
        var changes = Regenerator.classify(base, mine, theirs);
        assertEquals(List.of("c:USER_DELETED", "d:SAFE_DELETE", "f:NEW_FILE"),
                changes.stream().map(c -> c.path() + ":" + c.change()).toList());
    }

    @Test void recordingCanBeSwitchedOff() throws Exception {
        blueprint();
        template("__project__/README.md", "x");
        System.setProperty(ProvenanceRecorder.DISABLE_PROPERTY, "false");
        try {
            Path project = generate(Map.of("project", "quiet"));
            assertFalse(Files.exists(project.resolve(".jgen")));
        } finally {
            System.clearProperty(ProvenanceRecorder.DISABLE_PROPERTY);
        }
    }

    private static List<String> paths(List<Regenerator.FileChange> changes) {
        return changes.stream().map(Regenerator.FileChange::path).toList();
    }
}
