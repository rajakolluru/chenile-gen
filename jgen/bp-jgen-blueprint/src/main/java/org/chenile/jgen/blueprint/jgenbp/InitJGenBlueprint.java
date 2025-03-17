package org.chenile.jgen.blueprint.jgenbp;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

import java.util.Map;

public class InitJGenBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String blueprintName = (String)map.get("blueprintName");
            map.put("BlueprintName", CapUtils.capitalizeFirst(blueprintName));
        };
    }
}
