package org.chenile.jgen.template.chain;

import org.chenile.jgen.blueprints.BlueprintMarker;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.template.tags.PathTag;

import java.io.File;

/** Runs one tag on one file the way {@link PathTagProcessor} does; used by the deprecated single-tag steps. */
final class TagDelegate {
    private TagDelegate() { }

    static void apply(PathTag tag, File file, TemplateContext context) {
        String name = file.getName();
        if (BlueprintMarker.isMarker(name) || !tag.matches(name) || !file.exists()) return;
        try {
            tag.process(file, context);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Path tag " + tag.name() + " failed: " + e.getMessage(), e);
        }
    }
}
