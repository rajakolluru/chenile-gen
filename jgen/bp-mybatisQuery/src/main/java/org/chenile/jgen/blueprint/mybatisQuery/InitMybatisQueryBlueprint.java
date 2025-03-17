package org.chenile.jgen.blueprint.mybatisQuery;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

import java.util.Map;

public class InitMybatisQueryBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String mybatisQuery = (String)map.get("namespace");
            map.put("Namespace", CapUtils.capitalizeFirst(mybatisQuery));
        };
    }
}
