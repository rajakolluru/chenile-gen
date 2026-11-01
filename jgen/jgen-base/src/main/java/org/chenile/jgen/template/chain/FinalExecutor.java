package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

/**
 * Runs the blueprint's {@code finalHook} after everything else, including the generation record. Child blueprints
 * expanded inside a parent do not run it: the parent owns the finished project.
 */
public class FinalExecutor implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) {
        if (context.isChild() || context.blueprintConfig.finalHook == null) return;
        context.blueprintConfig.finalHook.accept(context.map);
    }
}
