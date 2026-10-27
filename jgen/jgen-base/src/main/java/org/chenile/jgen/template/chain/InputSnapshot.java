package org.chenile.jgen.template.chain;

import org.chenile.jgen.provenance.ProvenanceRecorder;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

/**
 * Captures the validated inputs of a top-level generation before any hook adds derived values, so the generation
 * record holds exactly what is needed to reproduce the project.
 */
public class InputSnapshot implements Command<TemplateContext> {
    @Override
    public void execute(TemplateContext context) {
        if (context.isChild() || !ProvenanceRecorder.enabled()) return;
        context.inputSnapshot = ProvenanceRecorder.snapshotInputs(context.blueprintConfig, context.map);
    }
}
