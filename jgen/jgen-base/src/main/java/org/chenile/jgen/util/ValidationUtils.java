package org.chenile.jgen.util;

/** Shared validation helpers for blueprint input and generation contracts. */
public final class ValidationUtils {
    private ValidationUtils() {}

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
