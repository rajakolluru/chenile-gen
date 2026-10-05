package org.chenile.jgen.util;

import javax.lang.model.SourceVersion;
import java.util.Set;

import static org.chenile.jgen.util.ValidationUtils.require;

public class JavaNameUtils {
    /** Validate a blueprint name without silently rewriting the user's input. */
    public static String identifier(String value) {
        require(value != null && value.matches("[A-Za-z][A-Za-z0-9_]*")
                && SourceVersion.isIdentifier(value) && !SourceVersion.isKeyword(value),
                "Invalid Java identifier: " + value);
        return value;
    }

    /** Validate a generated type name and require an uppercase first character. */
    public static String upperIdentifier(String name) {
        identifier(name);
        require(Character.isUpperCase(name.charAt(0)), "Type must start uppercase: " + name);
        return name;
    }

    /** Extract the simple model name from a simple or package-qualified name. */
    public static String modelName(String name) {
        require(name != null && !name.isBlank(), "Model name cannot be empty");
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "true", "false", "null");

    public static String safePackageSegment(String value) {
        String identifier = safeIdentifier(value);
        return isKeyword(identifier) ? identifier + "svc" : identifier;
    }

    public static String safeVariableName(String value) {
        String identifier = safeIdentifier(value);
        return isKeyword(identifier) ? identifier + "Entity" : identifier;
    }

    private static String safeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (i == 0) {
                builder.append(Character.isJavaIdentifierStart(ch) ? ch : '_');
            } else {
                builder.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
            }
        }
        return builder.toString();
    }

    private static boolean isKeyword(String value) {
        return JAVA_KEYWORDS.contains(value);
    }
}
