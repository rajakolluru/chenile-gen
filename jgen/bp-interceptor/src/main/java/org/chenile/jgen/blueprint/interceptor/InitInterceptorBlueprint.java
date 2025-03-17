package org.chenile.jgen.blueprint.interceptor;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

import java.util.Map;

public class InitInterceptorBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String interceptorName = (String)map.get("interceptorName");
            map.put("InterceptorName", CapUtils.capitalizeFirst(interceptorName));
        };
    }
}
