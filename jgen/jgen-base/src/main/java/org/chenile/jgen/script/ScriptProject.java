package org.chenile.jgen.script;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.model.FieldType;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The {@code project} object given to postProcess and final hook scripts: file operations confined to the generated
 * project folder. Every path is relative to the project; a path that leaves it is refused.
 */
public class ScriptProject {
    private final Path root;
    private final BlueprintConfig blueprint;
    private final Map<String, Object> inputs;

    ScriptProject(Path root, BlueprintConfig blueprint, Map<String, Object> inputs) {
        this.root = root.toAbsolutePath().normalize();
        this.blueprint = blueprint;
        this.inputs = inputs;
    }

    private Path resolve(String relative) {
        if (relative == null) throw new IllegalArgumentException("A path is required");
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new SecurityException("Path " + relative + " is outside the project");
        return path;
    }

    /** The project folder's name. */
    @HostAccess.Export
    public String name() {
        return root.getFileName().toString();
    }

    @HostAccess.Export
    public boolean exists(String relative) {
        return Files.exists(resolve(relative));
    }

    @HostAccess.Export
    public String read(String relative) throws IOException {
        return Files.readString(resolve(relative), StandardCharsets.UTF_8);
    }

    /** Writes a text file, creating folders as needed and replacing an existing file. */
    @HostAccess.Export
    public void write(String relative, String text) throws IOException {
        Path path = resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text == null ? "" : text, StandardCharsets.UTF_8);
    }

    @HostAccess.Export
    public void move(String from, String to) throws IOException {
        Path target = resolve(to);
        Files.createDirectories(target.getParent());
        Files.move(resolve(from), target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Deletes a file, or a folder with everything in it. */
    @HostAccess.Export
    public void delete(String relative) throws IOException {
        Path path = resolve(relative);
        if (path.equals(root)) throw new SecurityException("The project folder itself cannot be deleted");
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    /** Names of the entries in a folder of the project ("" for the project root), sorted. */
    @HostAccess.Export
    public String[] list(String relative) throws IOException {
        Path path = resolve(relative == null ? "" : relative);
        if (!Files.isDirectory(path)) return new String[0];
        try (Stream<Path> entries = Files.list(path)) {
            return entries.map(p -> p.getFileName().toString()).sorted().toArray(String[]::new);
        }
    }

    /** Copies a FILE input supplied by the user (e.g. a workflow XML) into the project. */
    @HostAccess.Export
    public void copyInput(String field, String relative) throws IOException {
        boolean declared = blueprint.inputFields != null && blueprint.inputFields.stream()
                .anyMatch(f -> f.name.equals(field) && f.type == FieldType.FILE);
        if (!declared) throw new IllegalArgumentException(field + " is not a FILE input of blueprint " + blueprint.name);
        Object value = inputs.get(field);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("No file given for " + field);
        Path target = resolve(relative);
        Files.createDirectories(target.getParent());
        Files.copy(Path.of(value.toString()), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
