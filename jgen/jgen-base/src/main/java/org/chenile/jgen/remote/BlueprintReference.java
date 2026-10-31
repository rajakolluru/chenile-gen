package org.chenile.jgen.remote;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a blueprint comes from. Phase 1 supports GitHub releases:
 * <pre>
 *   github:owner/repo                       latest release
 *   github:owner/repo@2.1.0                 a release tag
 *   github:owner/repo/blueprint-name@2.1.0  when the jar holds several blueprints
 *   ...#sha256:&lt;hex&gt;                         pin the jar's SHA-256 (recommended for CI)
 * </pre>
 * A plain name (no {@code github:} prefix) refers to a blueprint that is already installed.
 */
public record BlueprintReference(String owner, String repo, String blueprintName, String version, String sha256) {

    private static final Pattern GITHUB = Pattern.compile(
            "github:([A-Za-z0-9][A-Za-z0-9-]*)/([A-Za-z0-9._-]+)(?:/([A-Za-z0-9._-]+))?(?:@([A-Za-z0-9._+-]+))?(?:#sha256:([0-9a-fA-F]{64}))?");

    public static boolean isReference(String text) {
        return text != null && text.startsWith("github:");
    }

    public static BlueprintReference parse(String text) {
        Matcher m = GITHUB.matcher(text == null ? "" : text.trim());
        if (!m.matches())
            throw new IllegalArgumentException("Not a blueprint reference: " + text
                    + " (expected github:owner/repo[/blueprint][@version][#sha256:<hex>])");
        return new BlueprintReference(m.group(1), m.group(2), m.group(3), m.group(4),
                m.group(5) == null ? null : m.group(5).toLowerCase());
    }

    public BlueprintReference withVersion(String resolved) {
        return new BlueprintReference(owner, repo, blueprintName, resolved, sha256);
    }

    /** owner/repo, the unit of trust. */
    public String repository() {
        return owner + "/" + repo;
    }

    /** The canonical form, without the hash: github:owner/repo[/name]@version. */
    @Override
    public String toString() {
        return "github:" + repository() + (blueprintName == null ? "" : "/" + blueprintName)
                + (version == null ? "" : "@" + version);
    }
}
