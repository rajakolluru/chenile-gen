package org.chenile.jgen.blueprint.it;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

import java.util.Map;

public class InitItBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String app = (String)map.get("app");
            map.put("App", CapUtils.capitalizeFirst(app));
            String entity = (String)map.get("entity");
            map.put("Entity", CapUtils.capitalizeFirst(entity));
        };
    }
}
