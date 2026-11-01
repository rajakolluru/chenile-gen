package org.chenile.jgen.template.tags;

import org.chenile.jgen.ext.Extensions;

import java.util.List;

/** The path tags, in the order they run. A view of {@link Extensions}, which finds and checks every extension. */
public final class PathTags {
    private PathTags() { }

    /** Every tag, in the order they run (by {@link PathTag#order()}, then name). */
    public static List<PathTag> all() {
        return Extensions.tags();
    }

    /** Adds a tag programmatically (tests, embedding applications). */
    public static void register(PathTag tag) {
        Extensions.register(tag);
    }

    /** Forgets registered extensions and discovers again; for tests. */
    public static void reset() {
        Extensions.reset();
    }
}
