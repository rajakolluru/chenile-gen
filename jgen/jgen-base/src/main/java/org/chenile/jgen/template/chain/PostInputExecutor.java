package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

/**
 * Invokes the post input executor after all inputs have been captured but before any processing begins.
 */
public class PostInputExecutor implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) throws Exception {
        if(context.blueprintConfig.postInputCaptureHook != null)
            context.blueprintConfig.postInputCaptureHook.accept(context.map);
    }
}
