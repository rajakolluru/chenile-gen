package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.template.tags.DotTag;

import java.io.File;

/** @deprecated the chain now runs {@link DotTag} through {@link PathTagProcessor}; kept for old chains. */
@Deprecated
public class DotReplacer extends FileProcessor {
    public static final String DOT = DotTag.DOT;
    private final DotTag tag = new DotTag();

    @Override
    protected void doProcess(File file, TemplateContext context) {
        TagDelegate.apply(tag, file, context);
    }
}
