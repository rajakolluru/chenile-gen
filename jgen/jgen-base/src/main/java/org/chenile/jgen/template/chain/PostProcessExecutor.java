package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

/**
 * Executes the post process executor for any post-processing that needs to be completed by the blueprint.
 */
public class PostProcessExecutor implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) throws Exception {
        if(context.blueprintConfig.postProcessHook != null)
            context.blueprintConfig.postProcessHook.accept(context.map);
    }
}
