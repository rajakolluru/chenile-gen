package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.template.tags.PlaceholderTag;

import java.io.File;

/** @deprecated the chain now runs {@link PlaceholderTag} through {@link PathTagProcessor}; kept for old chains. */
@Deprecated
public class FileNameReplacer extends FileProcessor {
    private final PlaceholderTag tag = new PlaceholderTag();

    @Override
    protected void doProcess(File file, TemplateContext context) {
        TagDelegate.apply(tag, file, context);
    }
}
