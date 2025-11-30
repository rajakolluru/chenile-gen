package org.chenile.jgen.blueprint.batch;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.chenile.base.exception.ConfigurationException;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;
import org.chenile.orchestrator.process.config.model.ProcessDef;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class InitBatchBlueprint implements InitHook {

    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String batch = (String)map.get("batch");
            map.put("Batch", CapUtils.capitalizeFirst(batch));
            String batchJson = (String)map.get("batchJson");
            processBatchDefinitions(batchJson,map);
        };
        blueprintConfig.postProcessHook = (Map<String,Object> map) -> {
            String batchJson = (String)map.get("batchJson");
            String destPath  = "${destFolder}/${batch}/${batch}-service/src/test/resources/${com}/${company}/${org}/${batch}/${batch}-process-def.json";
            StringSubstitutor stringSubstitutor = new StringSubstitutor(map);
            destPath = stringSubstitutor.replace(destPath);
            File dest = new File(destPath);
            File xml = new File(batchJson);
            try {
                FileUtils.copyFile(xml, dest);
            }catch(Exception e){
                throw new RuntimeException("Unable to copy " + batchJson + " to " + destPath,e );
            }
        };
    }

    private void processBatchDefinitions(String batchJson, Map<String, Object> map) {
        ProcessConfigurator configurator = new ProcessConfigurator();
        try (InputStream inputStream = new FileInputStream(batchJson)){
            configurator.read(inputStream);
        }catch(Exception e){
            throw new ConfigurationException(1900,"File name " + batchJson + " cannot be processed. "+
                    " Error = " + e.getMessage());
        }
        Collection<ProcessDef> pdefs = configurator.processes.processMap.values();
        List<ProcessDefExt> enhancedPdefs = pdefs.stream().
                map(pd -> {
                    ProcessDefExt p = new ProcessDefExt(pd);
                    if (pd.parentProcessType == null || pd.parentProcessType.isEmpty())
                        p.root = true;
                    p.setChild(pdefs);
                    return p;
                }).toList();
        String root = enhancedPdefs.stream().filter(p -> p.root).findFirst().get().processType;
        map.put("processes", enhancedPdefs);
        map.put("root", root);
        map.put("Root", CapUtils.capitalizeFirst(root));
    }

    public static class ProcessDefExt extends ProcessDef{
        public boolean root = false;
        public String childProcessType ;
        public ProcessDefExt(ProcessDef processDef){
            this.processType = processDef.processType;
            this.parentProcessType = processDef.parentProcessType;
            this.aggregatorConfig = processDef.aggregatorConfig;
            this.splitterConfig = processDef.splitterConfig;
            this.executorConfig = processDef.executorConfig;
            this.leaf = processDef.leaf;
            this.successors = processDef.successors;
        }

        @Override
        public String toString() {
            return "ProcessDefExt{" +
                    "root=" + root +
                    ", childProcessType='" + childProcessType + '\'' +
                    ", successors=" + successors +
                    ", parentProcessType='" + parentProcessType + '\'' +
                    ", processType='" + processType + '\'' +
                    ", args='" + args + '\'' +
                    ", leaf=" + leaf +
                    ", splitterConfig=" + splitterConfig +
                    ", executorConfig=" + executorConfig +
                    ", aggregatorConfig=" + aggregatorConfig +
                    '}';
        }

        public void setChild(Collection<ProcessDef> pdefs) {
            for (ProcessDef pd: pdefs){
                if (processType.equals(pd.parentProcessType)) {
                    childProcessType = pd.processType;
                    return;
                }
            }
        }
    }
}
