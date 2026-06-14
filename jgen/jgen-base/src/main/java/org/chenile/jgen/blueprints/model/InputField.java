package org.chenile.jgen.blueprints.model;

import java.util.List;
import java.util.Map;

public class InputField {
    public String name;
    public String description;
    public String helpText;
    public String defaultValue;
    public FieldType type;
    public Boolean required;
    public List<String> validValues;
    public List<InputField> childFields;
    public String accept;
    public Long maxSizeBytes;
    public Boolean multiple;
    public Map<String, Object> visibleWhen;
}
