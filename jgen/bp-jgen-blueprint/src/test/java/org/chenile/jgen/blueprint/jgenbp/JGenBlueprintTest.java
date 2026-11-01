package org.chenile.jgen.blueprint.jgenbp;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JGenBlueprintTest {
    @TempDir Path output;
    private final Config config = new ConfigProvider().obtainDefaultConfig();

    private Path generate(Map<String, Object> inputs) throws Exception {
        BlueprintConfig blueprint = Registry.blueprints.get("jgen-blueprint");
        Map<String, Object> supplied = new HashMap<>(inputs);
        supplied.put("destFolder", output.toString());
        Map<String, Object> map = new HashMap<>(new BlueprintInputService().buildInputMap(blueprint, config, supplied));
        new BlueprintExecutor().execute(blueprint, config, map);
        return output.resolve("bp-" + inputs.get("blueprintName"));
    }

    @Test void moduleForTheChenileGenBuildByDefault() throws Exception {
        Path project = generate(Map.of("blueprintName", "demo"));
        String pom = Files.readString(project.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>jgen</artifactId>"), "parent is the jgen build");
        assertFalse(Files.exists(project.resolve(".gitignore")));
        assertFalse(Files.exists(project.resolve(".github")));
        assertFalse(Files.exists(project.resolve(".git")));
        assertStarterBlueprint(project);
    }

    @Test void standaloneRepositoryReadyToPush() throws Exception {
        Path project = generate(Map.of("blueprintName", "demo", "standaloneRepo", "y",
                "groupId", "com.acme", "blueprintVersion", "1.0.0", "githubRepository", "acme/jgen-demo"));
        String pom = Files.readString(project.resolve("pom.xml"));
        assertTrue(pom.contains("<groupId>com.acme</groupId>"));
        assertTrue(pom.contains("<revision>1.0.0</revision>"));
        assertTrue(pom.contains("<scope>provided</scope>"));
        assertFalse(pom.contains("<artifactId>jgen</artifactId>"), "no parent");
        assertTrue(Files.isRegularFile(project.resolve(".gitignore")));
        assertTrue(Files.isRegularFile(project.resolve(".github/workflows/release.yml")));
        assertTrue(Files.readString(project.resolve("README.md")).contains("jgen -g github:acme/jgen-demo"));
        assertStarterBlueprint(project);

        if (gitAvailable()) {
            assertTrue(Files.isDirectory(project.resolve(".git")), "a local repository");
            if (git(project, "rev-parse", "--verify", "HEAD").exit == 0) {
                assertEquals("1", git(project, "rev-list", "--count", "HEAD").out.trim());
                assertEquals("main", git(project, "rev-parse", "--abbrev-ref", "HEAD").out.trim());
                assertEquals(".jgen/generation.json", git(project, "ls-files", ".jgen/generation.json").out.trim(),
                        "the generation record is part of the first commit");
            }
        }
    }

    @Test void rejectsAMalformedRepositoryName() {
        assertThrows(Exception.class, () -> generate(Map.of("blueprintName", "demo", "standaloneRepo", "y",
                "githubRepository", "not a repo")));
    }

    private static void assertStarterBlueprint(Path project) throws Exception {
        Path template = project.resolve("src/main/resources/demo-template/__projectName__");
        assertTrue(Files.readString(template.resolve("README.md.mustache")).contains("{{ProjectName}}"),
                "the starter template keeps its own Mustache tags");
        assertTrue(Files.isRegularFile(template.resolve("%%includeExtras=true%%/EXTRAS.md.mustache")));
        assertTrue(Files.isRegularFile(project.resolve("src/test/java/org/chenile/jgen/blueprint/demo/DemoBlueprintTest.java")));
        assertTrue(Files.readString(project.resolve("src/main/resources/META-INF/blueprint.json"))
                .contains("\"templateFolder\": \"demo-template\""));
    }

    private record Result(int exit, String out) { }

    private static boolean gitAvailable() {
        try { return git(Path.of("."), "--version").exit == 0; } catch (Exception e) { return false; }
    }

    private static Result git(Path dir, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), out);
    }
}
