package org.chenile.jgen.blueprint.headlessservice;

import java.util.Map;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;

public class InitHeadlessServiceBlueprint implements InitHook {

	@Override
	public void init(BlueprintConfig blueprintConfig) {
		blueprintConfig.postInputCaptureHook = (Map<String, Object> map) -> {
			String serviceName = (String) map.get("service");
			map.put("Service", CapUtils.capitalizeFirst(serviceName));
		};
	}
}
