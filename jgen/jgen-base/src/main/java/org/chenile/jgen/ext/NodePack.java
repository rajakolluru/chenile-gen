package org.chenile.jgen.ext;

import java.util.List;
import java.util.Set;

/** Built-in language pack for JavaScript and TypeScript projects. */
public class NodePack implements LanguagePack {
    static final Set<String> RESERVED = Set.of("break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for", "function",
            "if", "implements", "import", "in", "instanceof", "interface", "let", "new", "null", "package", "private",
            "protected", "public", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof",
            "var", "void", "while", "with", "yield", "await");

    @Override public String name() { return "node"; }

    @Override
    public List<ValidationRule> rules() {
        return List.of(
                ValidationRule.of("NPM_PACKAGE", "an npm package name: lowercase, optionally scoped, at most 214 characters (e.g. @acme/orders)",
                        "(@[a-z0-9-~][a-z0-9-._~]*/)?[a-z0-9-~][a-z0-9-._~]*", v -> v.length() <= 214),
                ValidationRule.of("JS_IDENTIFIER", "a JavaScript identifier that is not a reserved word (e.g. orderService)",
                        "[A-Za-z_$][A-Za-z0-9_$]*", v -> !RESERVED.contains(v)));
    }
}
