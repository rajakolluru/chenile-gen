package org.chenile.jgen.provenance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * What JGen writes to {@code <project>/.jgen/generation.json} after generating a project: which blueprint (and
 * which version of it) cut the project, with which config and inputs, and a hash of every file it wrote. It lets a
 * person or an agent regenerate the project with a newer blueprint and tell pattern changes apart from hand edits.
 */
public class GenerationRecord {
    public static final String SCHEMA = "jgen.generation/1";

    public String schema = SCHEMA;
    /** ISO-8601 instant of generation. */
    public String generatedAt;
    /** The jgen-base version that generated the project, if known. */
    public String jgenVersion;
    public BlueprintRef blueprint = new BlueprintRef();
    /** The generated project's folder name (project-directory prefix + project name). */
    public String projectDirectory;
    /** The JGen config used, as a map. */
    public Map<String, Object> config = new LinkedHashMap<>();
    /** The validated inputs for the blueprint's declared fields, before any hook added derived values. */
    public Map<String, Object> inputs = new LinkedHashMap<>();
    /** FILE inputs, copied into the record: field name to a path relative to the project folder. */
    public Map<String, String> fileInputs = new LinkedHashMap<>();
    /** Every generated file (path relative to the project folder, '/'-separated) and its SHA-256. */
    public Map<String, String> files = new TreeMap<>();

    public static class BlueprintRef {
        public String name;
        /** The {@code version} declared in blueprint.json, if any. */
        public String version;
        /** Maven coordinates of the jar the blueprint came from, if it came from a jar. */
        public String groupId;
        public String artifactId;
        public String artifactVersion;
    }
}
