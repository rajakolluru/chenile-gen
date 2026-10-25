package org.chenile.jgen.blueprints;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The outcome of validating a blueprint input payload with {@link BlueprintPayloadValidator}.
 * Every error is reported, not just the first, so a caller can fix (or ask a model to fix) all of them at once.
 *
 * @param inputs   the resolved, normalized input map ready for generation (config defaults applied,
 *                 {@code ${...}} references substituted); {@code null} when there are errors
 * @param errors   problems that make the payload unusable
 * @param warnings problems that do not block generation, such as undeclared keys that are ignored
 */
public record PayloadValidation(Map<String, Object> inputs, List<PayloadIssue> errors, List<PayloadIssue> warnings) {

    public PayloadValidation {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    /**
     * For fail-fast callers: returns the resolved inputs, or throws an {@link IllegalArgumentException} whose
     * message lists every error.
     */
    public Map<String, Object> orThrow() {
        if (valid()) return inputs;
        if (errors.size() == 1) throw new IllegalArgumentException(errors.get(0).message());
        throw new IllegalArgumentException(errors.stream().map(PayloadIssue::message)
                .collect(Collectors.joining("; ", errors.size() + " input errors: ", "")));
    }
}
