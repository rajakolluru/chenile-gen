package org.chenile.jgen.template.chain;

import org.chenile.jgen.ext.Extensions;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

import java.util.function.Function;

/**
 * Puts the template helpers (capitalize, camelCase, snakeCase, … and any language pack's) into the map, where
 * Mustache uses them as lambdas: {@code {{#snakeCase}}{{entity}}{{/snakeCase}}}. An input of the same name wins.
 */
public class LambdaLoader implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) throws Exception {
        for (var helper : Extensions.helpers().entrySet()) {
            Function<String, String> function = helper.getValue();
            context.map.putIfAbsent(helper.getKey(), function);
        }
        // capitalize was always set, even over an input of that name; keep that
        context.map.put("capitalize", Extensions.helpers().get("capitalize"));
    }
}
