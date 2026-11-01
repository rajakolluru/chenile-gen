package org.chenile.jgen.ext;

import java.util.List;
import java.util.Set;

/** Built-in language pack for Python projects. */
public class PythonPack implements LanguagePack {
    static final Set<String> KEYWORDS = Set.of("False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
            "with", "yield");

    @Override public String name() { return "python"; }

    @Override
    public List<ValidationRule> rules() {
        return List.of(
                ValidationRule.of("PYTHON_MODULE", "a Python module name: lowercase letters, digits and _, not a keyword (e.g. order_service)",
                        "[a-z_][a-z0-9_]*", v -> !KEYWORDS.contains(v)),
                ValidationRule.of("PYTHON_PACKAGE", "dot-separated Python module names (e.g. acme.orders)",
                        "[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*",
                        v -> java.util.Arrays.stream(v.split("\\.")).noneMatch(KEYWORDS::contains)),
                ValidationRule.of("PYPI_PROJECT", "a PyPI project name: letters, digits, ., _ or -, starting and ending with a letter or digit (e.g. acme-orders)",
                        "[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?", null));
    }
}
