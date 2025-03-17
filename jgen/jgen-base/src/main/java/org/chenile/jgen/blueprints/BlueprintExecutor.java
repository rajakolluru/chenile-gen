package org.chenile.jgen.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.TemplateCopier;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;

import java.util.Map;

public class BlueprintExecutor {
    ConfigProvider configProvider = new ConfigProvider();
    ObjectMapper objectMapper = new ObjectMapper();
    TemplateCopier templateCopier = new TemplateCopier();
    public void execute(BlueprintConfig blueprintConfig, Config config, Map<String,Object> inputMap) throws Exception {
        if (config == null){
            config = configProvider.obtainDefaultConfig();
        }
        Map<String, Object> map = configProvider.getConfigAsMap(config);
        map.putAll(inputMap);
        if(blueprintConfig.postInputCaptureHook != null)
            blueprintConfig.postInputCaptureHook.accept(map);
        String destFolder = (String)inputMap.get("destFolder");
        if (destFolder == null) throw new RuntimeException("Destination folder cannot be null");
        System.out.println("Using the following map to generate code:\n " + map );
        templateCopier.process(blueprintConfig.templateFolder,destFolder,true,map );
       if(blueprintConfig.postProcessHook != null)
          blueprintConfig.postProcessHook.accept(map);
    }
}
