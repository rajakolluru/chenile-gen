package org.chenile.jgen.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Name conversions shared by template helpers, JavaScript hooks and language packs. Words are split at anything
 * that is not a letter or digit and at case changes: "purchase order", "purchase-order", "purchase_order",
 * "purchaseOrder" and "PurchaseOrder" are all the words [purchase, order]; "HTTPServer" is [http, server].
 */
public final class NameCase {
    private NameCase() { }

    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) return words;
        StringBuilder word = new StringBuilder();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!Character.isLetterOrDigit(c)) {
                flush(word, words);
                continue;
            }
            if (word.length() > 0 && Character.isUpperCase(c)) {
                char previous = chars[i - 1];
                boolean nextIsLower = i + 1 < chars.length && Character.isLowerCase(chars[i + 1]);
                // orderItem -> order|Item ; HTTPServer -> HTTP|Server
                if (Character.isLowerCase(previous) || Character.isDigit(previous)
                        || (Character.isUpperCase(previous) && nextIsLower)) flush(word, words);
            }
            word.append(c);
        }
        flush(word, words);
        return words;
    }

    private static void flush(StringBuilder word, List<String> words) {
        if (word.length() > 0) words.add(word.toString().toLowerCase());
        word.setLength(0);
    }

    /** "orders" becomes "Orders"; the rest is unchanged. */
    public static String capitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** "Orders" becomes "orders"; the rest is unchanged. */
    public static String uncapitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    /** "purchase order" becomes "purchaseOrder". */
    public static String camelCase(String text) {
        return uncapitalize(pascalCase(text));
    }

    /** "purchase order" becomes "PurchaseOrder". */
    public static String pascalCase(String text) {
        if (text == null) return null;
        StringBuilder out = new StringBuilder();
        for (String word : words(text)) out.append(capitalize(word));
        return out.toString();
    }

    /** "PurchaseOrder" becomes "purchase_order". */
    public static String snakeCase(String text) {
        return text == null ? null : String.join("_", words(text));
    }

    /** "PurchaseOrder" becomes "PURCHASE_ORDER". */
    public static String upperSnakeCase(String text) {
        return text == null ? null : snakeCase(text).toUpperCase();
    }

    /** "PurchaseOrder" becomes "purchase-order". */
    public static String kebabCase(String text) {
        return text == null ? null : String.join("-", words(text));
    }
}
