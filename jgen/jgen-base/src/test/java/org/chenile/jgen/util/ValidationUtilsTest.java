package org.chenile.jgen.util;

import org.junit.jupiter.api.Test;

import static org.chenile.jgen.util.ValidationUtils.require;
import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilsTest {
    @Test void acceptsSatisfiedRequirements() {
        assertDoesNotThrow(() -> require(true, "unused"));
    }

    @Test void preservesValidationFailureMessage() {
        var failure = assertThrows(IllegalArgumentException.class, () -> require(false, "Missing input"));
        assertEquals("Missing input", failure.getMessage());
    }
}
