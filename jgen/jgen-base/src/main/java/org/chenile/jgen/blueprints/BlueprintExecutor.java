package org.chenile.jgen.blueprints;

import org.chenile.jgen.config.Config;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.ProcessorFactory;
import org.chenile.owiz.config.impl.XmlOrchConfigurator;
import org.chenile.owiz.impl.OrchExecutorImpl;

import java.util.Map;

public class BlueprintExecutor {
    OrchExecutorImpl<TemplateContext> orchExecutor ;
    public BlueprintExecutor(){
        XmlOrchConfigurator<TemplateContext> configurator = new XmlOrchConfigurator<>();
        ProcessorFactory pf = new ProcessorFactory("org.chenile.jgen.template.chain.");
        configurator.setBeanFactoryAdapter(pf);
        configurator.setFilename("org/chenile/jgen/template/processors.xml");
        orchExecutor = new OrchExecutorImpl<>();
        orchExecutor.setOrchConfigurator(configurator);
    }

    public void execute(BlueprintConfig blueprintConfig, Config config, Map<String,Object> inputMap) throws Exception {
        TemplateContext templateContext = new TemplateContext();
        templateContext.blueprintConfig = blueprintConfig;
        templateContext.config = config;
        templateContext.map = inputMap;
        orchExecutor.execute(templateContext);
    }
}
