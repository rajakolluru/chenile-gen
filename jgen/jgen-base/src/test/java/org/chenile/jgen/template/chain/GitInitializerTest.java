package org.chenile.jgen.template.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.util.InputValidation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GitInitializerTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();

    private BlueprintConfig blueprint(String repositoryField) throws Exception {
        Path templates = temporary.resolve("templates");
        Files.createDirectories(templates.resolve("__project__"));
        Files.writeString(templates.resolve("__project__/README.md"), "hello\n");
        BlueprintConfig blueprint = json.readValue("""
            {"name":"git-test","inputFields":[
              {"name":"project","type":"STRING"},
              {"name":"initGit","type":"BOOLEAN","defaultValue":"n"},
              {"name":"repo","type":"STRING","required":false,"validation":"GITHUB_REPOSITORY"}]}
            """, BlueprintConfig.class);
        blueprint.templateFolder = templates.toString();
        blueprint.gitRepositoryField = repositoryField;
        InputValidation.checkDeclarations(blueprint);
        return blueprint;
    }

    private Path generate(BlueprintConfig blueprint, Config config, Map<String, Object> inputs) throws Exception {
        Path out = temporary.resolve("out-" + System.nanoTime());
        Map<String, Object> map = new HashMap<>(new BlueprintInputService().buildInputMap(blueprint, config, inputs));
        map.put("destFolder", out.toString());
        new BlueprintExecutor().execute(blueprint, config, map);
        return out.resolve(inputs.get("project").toString());
    }

    private Config config() { return new ConfigProvider().obtainDefaultConfig(); }

    @Test void nothingHappensUnlessInitGitIsTrue() throws Exception {
        Path project = generate(blueprint("repo"), config(), Map.of("project", "demo", "repo", "acme/demo"));
        assertFalse(Files.exists(project.resolve(".git")));
    }

    @Test void localRepositoryWithoutARepositoryField() throws Exception {
        assumeTrue(gitAvailable());
        Path project = generate(blueprint(null), config(), Map.of("project", "demo", "initGit", "y", "repo", "acme/demo"));
        assertTrue(Files.isDirectory(project.resolve(".git")));
        assertEquals("", git(project, "remote").trim(), "no gitRepositoryField, so no remote");
        assertEquals("main", git(project, "symbolic-ref", "--short", "HEAD").trim());
    }

    @Test void localRepositoryWhenTheRepositoryInputIsEmpty() throws Exception {
        assumeTrue(gitAvailable());
        Path project = generate(blueprint("repo"), config(), Map.of("project", "demo", "initGit", "y"));
        assertTrue(Files.isDirectory(project.resolve(".git")));
        assertEquals("", git(project, "remote").trim());
    }

    @Test void theGitHubRepositoryBecomesOrigin() throws Exception {
        assumeTrue(gitAvailable());
        Path ssh = generate(blueprint("repo"), config(), Map.of("project", "demo", "initGit", "y", "repo", "acme/demo"));
        assertEquals("git@github.com:acme/demo.git", git(ssh, "config", "--get", "remote.origin.url").trim());

        Config https = config();
        https.gitRemoteProtocol = "https";
        Path project = generate(blueprint("repo"), https, Map.of("project", "web", "initGit", "y", "repo", "acme/web"));
        assertEquals("https://github.com/acme/web.git", git(project, "config", "--get", "remote.origin.url").trim());
        if (git(project, "rev-list", "--count", "HEAD").trim().equals("1"))   // committed (Git has an identity)
            assertEquals(".jgen/generation.json", git(project, "ls-files", ".jgen/generation.json").trim());
    }

    @Test void theRepositoryFieldMustCarryTheGitHubRule() throws Exception {
        var blueprint = blueprint(null);
        blueprint.gitRepositoryField = "project";
        var error = assertThrows(IllegalArgumentException.class, () -> InputValidation.checkDeclarations(blueprint));
        assertTrue(error.getMessage().contains("GITHUB_REPOSITORY"));
        blueprint.gitRepositoryField = "missing";
        assertThrows(IllegalArgumentException.class, () -> InputValidation.checkDeclarations(blueprint));
    }

    @Test void flagAndRemoteFormats() {
        assertTrue(GitInitializer.enabled(Boolean.TRUE));
        assertTrue(GitInitializer.enabled("true"));
        assertFalse(GitInitializer.enabled(null));
        assertFalse(GitInitializer.enabled("y"), "booleans are normalised before the pipeline; raw y is not true");
        assertEquals("git@github.com:a/b.git", GitInitializer.remoteUrl("a/b", null));
        assertEquals("https://github.com/a/b.git", GitInitializer.remoteUrl("a/b", "HTTPS"));
    }

    private static boolean gitAvailable() {
        try { return new ProcessBuilder("git", "--version").start().waitFor() == 0; } catch (Exception e) { return false; }
    }

    private static String git(Path dir, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return out;
    }
}
