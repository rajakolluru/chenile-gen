package org.chenile.jgen.blueprints;

/**
 * One problem (or warning) found in a blueprint input payload.
 *
 * @param path    where it is: {@code $} for the whole payload, a field name, or a nested path such as
 *                {@code fields[0].name}
 * @param code    a stable machine-readable code (see the constants)
 * @param message a human-readable explanation, specific enough to fix the value from
 */
public record PayloadIssue(String path, String code, String message) {
    /** The payload is not syntactically valid JSON (or has duplicate keys). */
    public static final String INVALID_JSON = "INVALID_JSON";
    /** The payload is valid JSON but not a JSON object. */
    public static final String NOT_AN_OBJECT = "NOT_AN_OBJECT";
    /** A required, visible field is missing or empty. */
    public static final String REQUIRED = "REQUIRED";
    /** A field's value has the wrong type or shape, or breaks a declared rule. */
    public static final String INVALID_VALUE = "INVALID_VALUE";
    /** A cross-cutting input (package segments, project name, versions, registry URL) is invalid. */
    public static final String INVALID_INPUT = "INVALID_INPUT";
    /** Warning: the payload carries a key the blueprint does not declare. It is ignored. */
    public static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    /** Warning: the payload supplies a field that is hidden by its visibleWhen condition. It is ignored. */
    public static final String NOT_VISIBLE = "NOT_VISIBLE";

    @Override
    public String toString() {
        return path + ": " + message;
    }
}
