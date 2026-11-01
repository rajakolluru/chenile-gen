package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.template.tags.ConditionalTag;

import java.io.File;

/** @deprecated the chain now runs {@link ConditionalTag} through {@link PathTagProcessor}; kept for old chains. */
@Deprecated
public class ConditionalProcessor extends FileProcessor {
    private final ConditionalTag tag = new ConditionalTag();

    @Override
    protected void doProcess(File file, TemplateContext context) {
        TagDelegate.apply(tag, file, context);
    }
}
