package org.chenile.jgen.template.chain;

import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

import java.util.Map;

public class ConfigDefaulter implements Command <TemplateContext>{
    private ConfigProvider configProvider = new ConfigProvider();
    @Override
    public void execute(TemplateContext context) throws Exception {
        if (context.config == null){
            context.config = configProvider.obtainDefaultConfig();
        }
        Map<String, Object> map = configProvider.getConfigAsMap(context.config);
        map.putAll(context.map);
        context.map = map;
    }
}
