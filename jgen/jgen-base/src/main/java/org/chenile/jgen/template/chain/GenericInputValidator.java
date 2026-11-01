package org.chenile.jgen.template.chain;

import org.chenile.jgen.blueprints.PomInputs;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.InputValidation;
import org.chenile.owiz.Command;

import static org.chenile.jgen.util.BlueprintValidationUtils.validateGenericInputs;

/** Validates common inputs for every blueprint, including ones without an input hook. */
public class GenericInputValidator implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) {
        PomInputs.normalizeBlueprintInputs(context.blueprintConfig, context.map);
        validateGenericInputs(context.map, context.blueprintConfig);
        InputValidation.validateBlueprint(context.blueprintConfig, context.map);
    }
}
