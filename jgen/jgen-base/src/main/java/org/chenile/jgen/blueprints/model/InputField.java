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
    /** A built-in rule, or {@link ValidationType#EXTENSION} for a language pack's rule named in {@link #rule}. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public ValidationType validation;
    /** With validation EXTENSION: the language pack rule's name, e.g. PYTHON_MODULE. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String rule;
    public String accept;
    public Long maxSizeBytes;
    public Boolean multiple;
    public Map<String, Object> visibleWhen;
    /** With validation PATTERN: a Java regular expression the whole value must match. */
    public String pattern;
    /** With validation PATTERN: what a valid value looks like, used in error messages, e.g. "must look like ABC-123". */
    public String patternMessage;

    /** Reads {@code "validation"}: a built-in rule name, or the name of a rule from a language pack. */
    @com.fasterxml.jackson.annotation.JsonSetter("validation")
    public void setValidationName(String name) {
        if (name == null || name.isBlank()) {
            validation = null;
            rule = null;
            return;
        }
        try {
            validation = ValidationType.valueOf(name);
            rule = null;
        } catch (IllegalArgumentException notBuiltIn) {
            if (org.chenile.jgen.ext.Extensions.rule(name) == null)
                throw new IllegalArgumentException("Unknown validation rule " + name
                        + ": it is neither a built-in rule nor a rule of an installed language pack");
            validation = ValidationType.EXTENSION;
            rule = name;
        }
        if (validation == ValidationType.EXTENSION && rule == null)
            throw new IllegalArgumentException("validation EXTENSION is internal; name the language pack rule instead");
    }

    /** Writes {@code "validation"} as blueprints declare it: the built-in or language pack rule's name. */
    @com.fasterxml.jackson.annotation.JsonGetter("validation")
    public String getValidationName() {
        if (validation == null) return null;
        return validation == ValidationType.EXTENSION ? rule : validation.name();
    }
}
