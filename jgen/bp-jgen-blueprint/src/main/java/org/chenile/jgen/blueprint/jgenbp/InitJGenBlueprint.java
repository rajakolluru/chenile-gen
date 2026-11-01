package org.chenile.jgen.blueprint.jgenbp;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The blueprint that writes blueprints. With {@code standaloneRepo} it produces a self-contained project and sets
 * {@code initGit}, so JGen makes it a local Git repository; {@code githubRepository} (the blueprint's
 * {@code gitRepositoryField}) becomes its {@code origin} remote when given.
 */
public class InitJGenBlueprint implements InitHook {

    /**
     * The starter template of the generated blueprint contains a %%includeExtras=true%% folder. Stored under that
     * name, this blueprint's own conditional processor would act on it, so it is stored as if-includeExtras and
     * renamed after generation. (Dotfiles such as .gitignore need no help: they are stored as _DoT_gitignore, which
     * JGen's dot-replacer turns into .gitignore.)
     */
    static final String EXTRAS_PLACEHOLDER = "if-includeExtras";
    static final String EXTRAS_FOLDER = "%%includeExtras=true%%";

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String blueprintName = (String)map.get("blueprintName");
            map.put("BlueprintName", CapUtils.capitalizeFirst(blueprintName));
            // A standalone blueprint is generated as its own Git repository (JGen's git-initializer step).
            map.put("initGit", Boolean.TRUE.equals(map.get("standaloneRepo")));
        };
        blueprintConfig.postProcessHook = (Map<String,Object> map) -> {
            Path project = projectFolder(map);
            try {
                Path extras = project.resolve("src/main/resources")
                        .resolve(map.get("blueprintName") + "-template/__projectName__");
                if (Files.isDirectory(extras.resolve(EXTRAS_PLACEHOLDER)))
                    Files.move(extras.resolve(EXTRAS_PLACEHOLDER), extras.resolve(EXTRAS_FOLDER));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot finish " + project, e);
            }
        };
        // Runs after JGen's git-initializer: add the blueprint-specific release step to its instructions.
        blueprintConfig.finalHook = (Map<String,Object> map) -> {
            if (!Boolean.TRUE.equals(map.get("gitCommitted"))) return;
            Object repository = map.get("githubRepository");
            String repo = repository == null || repository.toString().isBlank()
                    ? "<owner>/jgen-" + map.get("blueprintName") : repository.toString();
            String g = "git -C " + projectFolder(map);
            Object version = map.get("blueprintVersion");
            System.out.println("Release it (after pushing): " + g + " tag " + version + " && " + g + " push origin " + version
                    + "\nThen anyone can use it with: jgen -g github:" + repo);
        };
    }

    static Path projectFolder(Map<String, Object> map) {
        return Path.of(String.valueOf(map.get("destFolder"))).toAbsolutePath().normalize()
                .resolve("bp-" + map.get("blueprintName"));
    }
}
