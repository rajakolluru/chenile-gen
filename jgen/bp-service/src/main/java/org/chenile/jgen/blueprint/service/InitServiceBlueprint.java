package org.chenile.jgen.blueprint.service;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

import java.util.Map;

public class InitServiceBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String serviceName = (String)map.get("api");
            map.put("Service", CapUtils.capitalizeFirst(serviceName));
        };
    }
}
