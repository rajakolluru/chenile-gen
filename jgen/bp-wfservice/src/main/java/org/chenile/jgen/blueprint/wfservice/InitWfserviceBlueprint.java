package org.chenile.jgen.blueprint.wfservice;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;
import org.chenile.jgen.util.JavaNameUtils;

import java.util.Map;

public class InitWfserviceBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String wfservice = (String)map.get("service");
            map.put("Service", CapUtils.capitalizeFirst(wfservice));
            map.put("servicePackage", JavaNameUtils.safePackageSegment(wfservice));
            map.put("serviceVar", JavaNameUtils.safeVariableName(wfservice));
        };
    }
}
