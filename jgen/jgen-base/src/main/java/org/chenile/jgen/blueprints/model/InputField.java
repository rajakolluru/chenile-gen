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
    public ValidationType validation;
    public String accept;
    public Long maxSizeBytes;
    public Boolean multiple;
    public Map<String, Object> visibleWhen;
    /** With validation PATTERN: a Java regular expression the whole value must match. */
    public String pattern;
    /** With validation PATTERN: what a valid value looks like, used in error messages, e.g. "must look like ABC-123". */
    public String patternMessage;
}
