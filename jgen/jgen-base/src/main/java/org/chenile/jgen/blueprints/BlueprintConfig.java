package org.chenile.jgen.blueprints;

import org.chenile.jgen.blueprints.model.InputField;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlueprintConfig {
    public String name;
    public String description;
    public Class<InitHook> initHook;
    public Consumer<Map<String,Object>> postInputCaptureHook;
    public String templateFolder;
    public List<InputField> inputFields;
    /**
     * Final processing post the code generation
     */
    public Consumer<Map<String,Object>> postProcessHook;
}
