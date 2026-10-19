package org.chenile.jgen.blueprints;

import java.util.Map;

/** Computes a child's declared inputs from its enclosing blueprint and marker attributes. */
@FunctionalInterface
public interface ChildBlueprintInputs {
    Map<String, Object> computeChildInputs(Map<String, Object> currentInputs,
            String blueprintName, Map<String, String> attributes);
}
