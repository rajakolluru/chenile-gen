package org.chenile.jgen.blueprints;

import org.chenile.jgen.blueprints.model.InputField;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlueprintConfig {
    public String name;
    public String description;
    public String category;
    public String version;
    /** Minimum Chenile runtime version required by this blueprint, if any. */
    public String sinceVersion;
    public Class<InitHook> initHook;
    public Consumer<Map<String,Object>> postInputCaptureHook;
    /** Registered by the init hook when this blueprint includes child blueprint markers. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public ChildBlueprintInputs childBlueprintInputs;
    public String templateFolder;
    public List<InputField> inputFields;
    /** Defaults to the first input field, which conventionally names the generated project. */
    public String projectNameField;
    /** Prefix used by the template's top-level directory, e.g. "bp-" or "bdd-it-". */
    public String projectDirectoryPrefix = "";
    /** Optional boolean input enabling a registry client that requires serviceRegistryUrl. */
    public String registryClientFlag;
    /** Optional emitters for FILE inputs, registered by the blueprint's init hook. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Map<String,SampleInputFile> sampleInputFiles = new java.util.LinkedHashMap<>();
    /**
     * Final processing post the code generation
     */
    public Consumer<Map<String,Object>> postProcessHook;
    /**
     * Runs last, for a top-level generation only: after the generation record is written and after the Git
     * repository (if {@code initGit}) is created. Use it for actions outside the generated file tree; files it
     * changes are not part of the initial commit.
     */
    public Consumer<Map<String,Object>> finalHook;
    /**
     * Names the input field that holds the GitHub repository (owner/repo) of the generated project. The field must be
     * declared with validation GITHUB_REPOSITORY. When the input map has {@code initGit=true} and this field has a
     * value, JGen adds it as the {@code origin} remote. Null (the default) means the blueprint has no such field.
     */
    public String gitRepositoryField;
    /** The message of the initial commit when {@code initGit} is true; may use ${input} placeholders. */
    public String gitCommitMessage;
    /** Where this blueprint's descriptor was loaded from (a jar: or file: URL); recorded in generation records. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String source;
    /** The class loader that holds this blueprint's templates and hooks; null means JGen's own class path. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public ClassLoader classLoader;
    /** Set when the blueprint was fetched from the web: the exact reference it was resolved to, and its jar's hash. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String fetchedFrom;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String sha256;
}
