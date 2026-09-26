package org.chenile.jgen.blueprint.ecosystem;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;

import java.util.Map;

public class InitEcosystemBlueprint implements InitHook {
    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = input -> new EcosystemGenerator().generate(blueprintConfig, input);
    }
}
