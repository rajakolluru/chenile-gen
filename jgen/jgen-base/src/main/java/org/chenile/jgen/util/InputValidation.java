package org.chenile.jgen.util;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.PayloadIssue;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.blueprints.model.ValidationType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Shared declarative validation for every input path, including composed blueprints. */
public final class InputValidation {
    private InputValidation() { }

    /** GitHub owner: 1-39 letters, digits or single hyphens, not starting or ending with a hyphen. */
    private static final Pattern GITHUB_OWNER = Pattern.compile("[A-Za-z0-9](?:-?[A-Za-z0-9])*");
    private static final Pattern GITHUB_REPO = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    /** Checks a value against a named rule. {@link ValidationType#PATTERN} needs the field; use the field form. */
    public static boolean isValid(ValidationType rule, String value) {
        if (rule == null) return true;
        try { validate(rule, value); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    /** Checks a value against the field's rule, including a {@link ValidationType#PATTERN} rule. */
    public static boolean isValid(InputField field, String value) {
        if (field.validation == null) return true;
        try { validate(field, value); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    public static void validate(InputField field, String value) {
        if (field.validation != ValidationType.PATTERN) {
            validate(field.validation, value);
            return;
        }
        ValidationUtils.require(field.pattern != null && compiled(field.pattern).matcher(value).matches(),
                "Value " + describe(field));
    }

    public static void validate(ValidationType rule, String value) {
        switch (rule) {
            case JAVA_IDENTIFIER -> JavaNameUtils.identifier(value);
            case JAVA_TYPE -> JavaNameUtils.upperIdentifier(value);
            case JAVA_PACKAGE -> {
                for (String segment : value.split("\\.", -1)) JavaNameUtils.identifier(segment);
            }
            case PROJECT_NAME -> ValidationUtils.require(value.matches("[A-Za-z][A-Za-z0-9_-]*"), "Invalid project name");
            case VERSION -> ValidationUtils.require(value.matches("[A-Za-z0-9_.-]+"), "Invalid version");
            case HTTP_URL -> BlueprintValidationUtils.validateHttpUrl(value, "Value");
            case KAFKA_TOPIC -> ValidationUtils.require(value.matches("[A-Za-z0-9._-]{1,249}")
                    && !value.equals(".") && !value.equals(".."), "Invalid Kafka topic");
            case GITHUB_REPOSITORY -> {
                String[] parts = value.split("/", -1);
                ValidationUtils.require(parts.length == 2 && parts[0].length() <= 39
                        && GITHUB_OWNER.matcher(parts[0]).matches()
                        && GITHUB_REPO.matcher(parts[1]).matches()
                        && !parts[1].equals(".") && !parts[1].equals(".."), "Invalid GitHub repository");
            }
            case PATTERN -> throw new IllegalArgumentException("PATTERN needs the field's pattern; validate the field");
        }
    }

    /** What a valid value looks like, for error messages. */
    public static String describe(InputField field) {
        if (field.validation == null) return "has an invalid value";
        return switch (field.validation) {
            case JAVA_IDENTIFIER -> "must satisfy JAVA_IDENTIFIER (a Java identifier that is not a keyword)";
            case JAVA_TYPE -> "must satisfy JAVA_TYPE (a Java identifier starting with an uppercase letter)";
            case JAVA_PACKAGE -> "must satisfy JAVA_PACKAGE (dot-separated Java identifiers)";
            case PROJECT_NAME -> "must satisfy PROJECT_NAME (a letter, then letters, digits, _ or -)";
            case VERSION -> "must satisfy VERSION (letters, digits, dots, _ or -)";
            case HTTP_URL -> "must satisfy HTTP_URL (an absolute http or https URL with a host)";
            case KAFKA_TOPIC -> "must satisfy KAFKA_TOPIC (1-249 letters, digits, dots, _ or -)";
            case GITHUB_REPOSITORY -> "must satisfy GITHUB_REPOSITORY (owner/repo, e.g. acme/jgen-terraform)";
            case PATTERN -> field.patternMessage != null && !field.patternMessage.isBlank()
                    ? field.patternMessage : "must match " + field.pattern;
        };
    }

    /**
     * Checks a blueprint's own declarations, so that mistakes surface when the blueprint is loaded rather than when
     * someone first uses it: a PATTERN rule needs a valid regular expression and a patternMessage.
     */
    public static void checkDeclarations(BlueprintConfig blueprint) {
        if (blueprint.inputFields != null) checkDeclarations(blueprint.name, blueprint.inputFields, "");
        String repositoryField = blueprint.gitRepositoryField;
        if (repositoryField != null) {
            boolean declared = blueprint.inputFields != null && blueprint.inputFields.stream()
                    .anyMatch(f -> repositoryField.equals(f.name) && f.validation == ValidationType.GITHUB_REPOSITORY);
            if (!declared)
                throw new IllegalArgumentException("Blueprint " + blueprint.name + ": gitRepositoryField "
                        + repositoryField + " must name an input field with validation GITHUB_REPOSITORY");
        }
    }

    private static void checkDeclarations(String blueprint, List<InputField> fields, String prefix) {
        for (InputField field : fields) {
            String path = prefix + field.name;
            if (field.validation == ValidationType.PATTERN) {
                if (field.pattern == null || field.pattern.isBlank())
                    throw new IllegalArgumentException("Blueprint " + blueprint + ": field " + path
                            + " uses PATTERN but declares no pattern");
                if (field.patternMessage == null || field.patternMessage.isBlank())
                    throw new IllegalArgumentException("Blueprint " + blueprint + ": field " + path
                            + " uses PATTERN but declares no patternMessage");
                try { compiled(field.pattern); }
                catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("Blueprint " + blueprint + ": field " + path
                            + " has an invalid pattern: " + e.getDescription());
                }
            } else if (field.pattern != null) {
                throw new IllegalArgumentException("Blueprint " + blueprint + ": field " + path
                        + " declares a pattern but its validation is " + field.validation + ", not PATTERN");
            }
            if (field.childFields != null) checkDeclarations(blueprint, field.childFields, path + ".");
        }
    }

    private static Pattern compiled(String regex) {
        return PATTERNS.computeIfAbsent(regex, Pattern::compile);
    }

    /** Fail-fast form: throws on the first problem. Kept for the execution boundary and older callers. */
    public static void validateBlueprint(BlueprintConfig blueprint, Map<String,Object> input) {
        List<PayloadIssue> issues = new ArrayList<>();
        collectBlueprint(blueprint, input, issues);
        throwFirst(issues);
    }

    /** Collect-all form: appends one issue per invalid field (or list element) to {@code issues}. */
    public static void collectBlueprint(BlueprintConfig blueprint, Map<String,Object> input, List<PayloadIssue> issues) {
        if (blueprint.inputFields != null) collectFields(blueprint.inputFields, input, "", issues);
    }

    private static void collectFields(List<InputField> fields, Map<?,?> values, String prefix, List<PayloadIssue> issues) {
        var service = new BlueprintInputService();
        Map<String,Object> visibility = new HashMap<>();
        values.forEach((k,v) -> visibility.put(k.toString(), v));
        for (InputField field : fields) {
            if (!service.isVisible(field, visibility)) continue;
            collectField(field, values.get(field.name), prefix + field.name, issues);
        }
    }

    private static boolean hasValidation(InputField field) {
        return field.validation != null || field.childFields != null
                && field.childFields.stream().anyMatch(InputValidation::hasValidation);
    }

    /** Fail-fast form of {@link #collectField}. */
    public static void validateField(InputField field, Object value, String path) {
        List<PayloadIssue> issues = new ArrayList<>();
        collectField(field, value, path, issues);
        throwFirst(issues);
    }

    /**
     * Checks a field's declarative {@code validation} rule, and those of its child fields, appending one issue per
     * failing value. Fields with no rules anywhere in their tree are not checked here.
     */
    public static void collectField(InputField field, Object value, String path, List<PayloadIssue> issues) {
        if (!hasValidation(field)) return;
        if (field.validation != null) {
            if (field.type == FieldType.MULTI_SELECT && value instanceof String text && !text.isEmpty())
                value = Arrays.stream(text.split(",", -1)).map(String::trim).toList();
            if (value instanceof List<?> list) {
                if (list.isEmpty() && FieldUtils.isRequired(field))
                    issues.add(new PayloadIssue(path, PayloadIssue.REQUIRED,
                            "Field " + path + " is required (" + field.validation + ")"));
                for (int i = 0; i < list.size(); i++) collectValue(field, list.get(i), path + "[" + i + "]", issues);
            } else collectValue(field, value, path, issues);
        }
        if (field.childFields == null || value == null) return;
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?,?> record)) {
                    issues.add(new PayloadIssue(path + "[" + i + "]", PayloadIssue.INVALID_VALUE,
                            "Field " + path + "[" + i + "] must be a record"));
                    continue;
                }
                collectFields(field.childFields, record, path + "[" + i + "].", issues);
            }
        } else if (value instanceof Map<?,?> record) {
            collectFields(field.childFields, record, path + ".", issues);
        } else issues.add(new PayloadIssue(path, PayloadIssue.INVALID_VALUE, "Field " + path + " must contain records"));
    }

    private static void collectValue(InputField field, Object value, String path, List<PayloadIssue> issues) {
        if (value == null || value instanceof String text && text.isEmpty()) {
            if (FieldUtils.isRequired(field))
                issues.add(new PayloadIssue(path, PayloadIssue.REQUIRED,
                        "Field " + path + " is required (" + field.validation + ")"));
            return;
        }
        if (!(value instanceof String text) || !isValid(field, text))
            issues.add(new PayloadIssue(path, PayloadIssue.INVALID_VALUE, "Field " + path + " " + describe(field)));
    }

    private static void throwFirst(List<PayloadIssue> issues) {
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.get(0).message());
    }
}
