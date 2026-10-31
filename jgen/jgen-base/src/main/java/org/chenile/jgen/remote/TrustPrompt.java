package org.chenile.jgen.remote;

/** Asks the user whether to run a blueprint from a source that is not trusted yet. */
@FunctionalInterface
public interface TrustPrompt {
    enum Decision { ALLOW_ONCE, ALLOW_ALWAYS, DENY }

    Decision ask(BlueprintReference reference);

    /** For non-interactive use: never trusts anything new. */
    TrustPrompt DENY = reference -> Decision.DENY;
}
