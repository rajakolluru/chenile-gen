package org.chenile.jgen.blueprint.minimonolith;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InitMinimonolithBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String minimonolith = (String)map.get("monolith");
            map.put("Monolith", CapUtils.capitalizeFirst(minimonolith));
            // populate with a dummy service
            map.put("service","myService");
            map.put("serviceVersion","myServiceVersion");
            List<String> list = new ArrayList<>();
            list.add("myService-service");
            map.put("services",list);
        };
    }
}
