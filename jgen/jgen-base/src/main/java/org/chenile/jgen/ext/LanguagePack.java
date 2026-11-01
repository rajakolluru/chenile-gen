package org.chenile.jgen.ext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Support for generating projects in a language: validation rules for its names ({@code PYTHON_MODULE},
 * {@code NPM_PACKAGE}) and template helpers. Python and Node packs are built in; others are added by extension jars
 * listing their packs in {@code META-INF/services/org.chenile.jgen.ext.LanguagePack}.
 */
public interface LanguagePack {
    /** A unique name, e.g. "python". Blueprints use it in {@code requires}. */
    String name();

    /** Rules blueprints can name in an input field's {@code validation}. Names are UPPER_SNAKE and unique. */
    default List<ValidationRule> rules() { return List.of(); }

    /** Helpers every template engine and script can use, by name. Names must be unique across packs. */
    default Map<String, Function<String, String>> helpers() { return Map.of(); }
}
