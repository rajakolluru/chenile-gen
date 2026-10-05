package org.chenile.jgen.util;

import org.junit.jupiter.api.Test;

import static org.chenile.jgen.util.JavaNameUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaNameUtilsTest {
    @Test void validatesIdentifiersWithoutRewritingThem() {
        assertEquals("batch_2", identifier("batch_2"));
        for (String value : new String[]{null, "", "class", "true", "null", "_", "1Name", "../Escape", "a-b", "$name"}) {
            assertThrows(IllegalArgumentException.class, () -> identifier(value));
        }
    }

    @Test void validatesUppercaseTypeNames() {
        assertEquals("BatchIn", upperIdentifier("BatchIn"));
        assertThrows(IllegalArgumentException.class, () -> upperIdentifier("batchIn"));
        assertThrows(IllegalArgumentException.class, () -> upperIdentifier(""));
    }

    @Test void extractsSimpleModelNames() {
        assertEquals("BatchIn", modelName("BatchIn"));
        assertEquals("BatchIn", modelName("com.example.model.BatchIn"));
        assertThrows(IllegalArgumentException.class, () -> modelName(null));
        assertThrows(IllegalArgumentException.class, () -> modelName(" "));
    }

    @Test void preservesExistingNameSanitization() {
        assertEquals("classsvc", safePackageSegment("class"));
        assertEquals("classEntity", safeVariableName("class"));
        assertEquals("some_name", safeVariableName("some-name"));
    }
}
