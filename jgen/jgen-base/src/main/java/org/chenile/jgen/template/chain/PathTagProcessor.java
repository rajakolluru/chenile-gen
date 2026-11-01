package org.chenile.jgen.template.chain;

import org.chenile.jgen.blueprints.BlueprintMarker;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.template.tags.PathTag;
import org.chenile.jgen.template.tags.PathTags;
import org.chenile.jgen.util.FileProcessingHelper;
import org.chenile.owiz.Command;

/**
 * Applies every {@link PathTag} to the generated tree, one pass per tag in order. Replaces the separate file-splitter,
 * file-name-replacer, dot-replacer and conditional-processor steps, which are now the built-in tags.
 */
public class PathTagProcessor implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) throws Exception {
        for (PathTag tag : PathTags.all()) {
            try {
                FileProcessingHelper.iterateFiles(file -> {
                    String name = file.getName();
                    if (!BlueprintMarker.isMarker(name) && tag.matches(name) && file.exists()) tag.process(file, context);
                }, tag.depthFirst(), context.destination);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Path tag " + tag.name() + " failed: " + e.getMessage(), e);
            }
        }
    }
}
