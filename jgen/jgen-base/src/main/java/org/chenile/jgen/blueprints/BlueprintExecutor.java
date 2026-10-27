package org.chenile.jgen.blueprints;

import org.chenile.jgen.config.Config;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.ProcessorFactory;
import org.chenile.owiz.config.impl.XmlOrchConfigurator;
import org.chenile.owiz.impl.OrchExecutorImpl;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

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
        templateContext.blueprintPath = blueprintConfig.name == null ? List.of() : List.of(blueprintConfig.name);
        templateContext.isJar = blueprintConfig.templateFolder == null
                || !java.nio.file.Files.isDirectory(java.nio.file.Path.of(blueprintConfig.templateFolder));
        orchExecutor.execute(templateContext);
    }
    /** Invokes a child with the same configuration and a separate input map and ancestry. */
    public void executeChild(BlueprintConfig blueprint, Map<String, Object> inputMap, TemplateContext parent) throws Exception {
        List<String> ancestry = parent.blueprintPath.isEmpty()
                ? List.of(parent.blueprintConfig.name) : parent.blueprintPath;
        if (ancestry.contains(blueprint.name))
            throw new IllegalArgumentException("Recursive blueprint inclusion: " + String.join(" -> ", ancestry) + " -> " + blueprint.name);
        TemplateContext child = new TemplateContext();
        child.blueprintConfig = blueprint;
        child.config = parent.config;
        child.map = inputMap;
        child.isJar = !java.nio.file.Files.isDirectory(java.nio.file.Path.of(blueprint.templateFolder));
        var path = new ArrayList<>(ancestry); path.add(blueprint.name);
        child.blueprintPath = List.copyOf(path);
        orchExecutor.execute(child);
    }
}
