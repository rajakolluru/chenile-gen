package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.CapUtils;
import org.chenile.owiz.Command;

import java.util.function.Function;

/**
 * Loads some useful Lambdas into the map for the templates to use.
 */
public class LambdaLoader implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) throws Exception {
        Function<String,String> capitalize = CapUtils::capitalizeFirst;
        context.map.put("capitalize", capitalize);
    }
}
