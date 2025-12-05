package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.FileProcessingHelper;
import org.chenile.owiz.Command;

import java.io.File;

/**
 * Inherit from this class to do processing for every file or folder in the destination folder.
 */
public abstract class FileProcessor implements Command<TemplateContext> {
    public boolean depthFirst = true;

    @Override
    public final void execute(TemplateContext context) throws Exception {
        FileProcessingHelper.iterateFiles((file) -> doProcess(file,context),
                depthFirst,context.destination);

    }
    protected abstract void doProcess(File file, TemplateContext context);
}
