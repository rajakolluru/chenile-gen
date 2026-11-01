package org.chenile.jgen.ext;

/** A named input validation rule contributed by a {@link LanguagePack}. */
public interface ValidationRule {
    /** The name blueprints use in {@code "validation"}, e.g. "PYTHON_MODULE". */
    String name();

    /** What a valid value looks like, for error messages, e.g. "a Python module name such as order_service". */
    String description();

    boolean isValid(String value);

    /** A rule from a regular expression the whole value must match, plus an optional extra check. */
    static ValidationRule of(String name, String description, String regex, java.util.function.Predicate<String> extra) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        return new ValidationRule() {
            @Override public String name() { return name; }
            @Override public String description() { return description; }
            @Override public boolean isValid(String value) {
                return value != null && pattern.matcher(value).matches() && (extra == null || extra.test(value));
            }
        };
    }
}
