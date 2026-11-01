package org.chenile.jgen.blueprints.model;

/**
 * Opt-in, reusable validation rules declared by blueprint input fields. Prefer a named rule; use {@link #PATTERN}
 * only for a format that is specific to one blueprint.
 */
public enum ValidationType {
    JAVA_IDENTIFIER, JAVA_TYPE, JAVA_PACKAGE, PROJECT_NAME, VERSION, HTTP_URL, KAFKA_TOPIC,
    /** A GitHub repository as owner/repo, e.g. acme/jgen-terraform. */
    GITHUB_REPOSITORY,
    /** The whole value must match the field's {@code pattern}; the field's {@code patternMessage} explains it. */
    PATTERN,
    /**
     * A rule from a language pack, such as PYTHON_MODULE or NPM_PACKAGE. Blueprints write the rule's own name in
     * {@code validation}; JGen keeps it in {@link InputField#rule} and sets this value.
     */
    EXTENSION
}
