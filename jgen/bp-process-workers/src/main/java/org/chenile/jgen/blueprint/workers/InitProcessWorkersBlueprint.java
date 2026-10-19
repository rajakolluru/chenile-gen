package org.chenile.jgen.blueprint.workers;

import org.chenile.jgen.blueprints.*;

public class InitProcessWorkersBlueprint implements InitHook {
    public void init(BlueprintConfig blueprint) {
        blueprint.postInputCaptureHook = input -> {
            input.put("composite", !Boolean.TRUE.equals(input.get("leaf")));
        };
    }
}
