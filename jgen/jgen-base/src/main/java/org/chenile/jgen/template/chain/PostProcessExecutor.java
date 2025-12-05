package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

public class PostProcessExecutor implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) throws Exception {
        if(context.blueprintConfig.postProcessHook != null)
            context.blueprintConfig.postProcessHook.accept(context.map);
    }
}
