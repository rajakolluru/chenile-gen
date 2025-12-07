package org.chenile.jgen.template.chain;

import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

import java.util.Map;

/**
 * Defaults the config from a config provider if the config has not been passed in the invocation.
 * Ensures that the input map is merged with the config. (input map has a higher priority than config)
 */
public class ConfigDefaulter implements Command <TemplateContext>{
    private ConfigProvider configProvider = new ConfigProvider();
    @Override
    public void execute(TemplateContext context) throws Exception {
        if (context.config == null){
            context.config = configProvider.obtainDefaultConfig();
        }
        Map<String, Object> map = configProvider.getConfigAsMap(context.config);
        map.forEach(context.map::putIfAbsent);
    }
}
