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
}
