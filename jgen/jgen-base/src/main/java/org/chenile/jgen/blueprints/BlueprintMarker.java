package org.chenile.jgen.blueprints;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A folder named %{{blueprint key=value key2="quoted value"}}. */
public record BlueprintMarker(String blueprintName, Map<String, String> attributes) {
    public static boolean isMarker(String name) {
        return name.startsWith("%{{");
    }

    public static BlueprintMarker parse(String name) {
        if (!isMarker(name) || !name.endsWith("}}")) throw invalid(name, "expected %{{blueprint key=value}}");
        String body = name.substring(3, name.length() - 2).trim();
        int cursor = 0;
        while (cursor < body.length() && !Character.isWhitespace(body.charAt(cursor))) cursor++;
        String blueprint = body.substring(0, cursor);
        if (!blueprint.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) throw invalid(name, "invalid blueprint name");
        Map<String, String> attributes = new LinkedHashMap<>();
        while (cursor < body.length()) {
            while (cursor < body.length() && Character.isWhitespace(body.charAt(cursor))) cursor++;
            if (cursor == body.length()) break;
            int start = cursor;
            while (cursor < body.length() && !Character.isWhitespace(body.charAt(cursor)) && body.charAt(cursor) != '=') cursor++;
            String key = body.substring(start, cursor);
            if (!key.matches("[A-Za-z_][A-Za-z0-9_.-]*") || cursor == body.length() || body.charAt(cursor) != '=')
                throw invalid(name, "expected attribute=value");
            cursor++;
            StringBuilder value = new StringBuilder();
            if (cursor < body.length() && (body.charAt(cursor) == '\'' || body.charAt(cursor) == '"')) {
                char quote = body.charAt(cursor++);
                boolean closed = false;
                while (cursor < body.length()) {
                    char ch = body.charAt(cursor++);
                    if (ch == quote) { closed = true; break; }
                    if (ch == '\\') {
                        if (cursor == body.length()) throw invalid(name, "unfinished escape");
                        char escaped = body.charAt(cursor++);
                        if (escaped != quote && escaped != '\\') throw invalid(name, "only quotes and backslashes can be escaped");
                        ch = escaped;
                    }
                    value.append(ch);
                }
                if (!closed) throw invalid(name, "unterminated quoted value");
                if (cursor < body.length() && !Character.isWhitespace(body.charAt(cursor)))
                    throw invalid(name, "expected whitespace after quoted value");
            } else {
                while (cursor < body.length() && !Character.isWhitespace(body.charAt(cursor))) {
                    char ch = body.charAt(cursor++);
                    if (ch == '\'' || ch == '"' || ch == '=') throw invalid(name, "quote values containing quotes or equals signs");
                    value.append(ch);
                }
                if (value.isEmpty()) throw invalid(name, "empty values must be quoted");
            }
            if (attributes.putIfAbsent(key, value.toString()) != null) throw invalid(name, "duplicate attribute: " + key);
        }
        return new BlueprintMarker(blueprint, Collections.unmodifiableMap(attributes));
    }

    private static IllegalArgumentException invalid(String name, String reason) {
        return new IllegalArgumentException("Invalid blueprint marker " + name + ": " + reason);
    }
}
