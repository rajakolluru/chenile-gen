package org.chenile.jgen.blueprint.wfcustom;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.InitHook;
import org.chenile.jgen.util.CapUtils;
import org.chenile.workflow.cli.CLIHelper;
import org.chenile.workflow.cli.CLIParams;
import org.chenile.workflow.testcases.Testcase;

import java.io.File;
import java.util.List;
import java.util.Map;

public class InitWfcustomBlueprint implements InitHook {
    CLIHelper cliHelper = new CLIHelper();
    @Override
    public void init(BlueprintConfig blueprintConfig) {
        blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
            String service = (String)map.get("service");
            map.put("Service", CapUtils.capitalizeFirst(service));
            String xmlFile = (String)map.get("xmlFile");
            processXmlFile(xmlFile,map);
        };
        blueprintConfig.postProcessHook = (Map<String,Object> map) -> {
                String xmlFile = (String)map.get("xmlFile");
                String destPath  = "${destFolder}/${service}/${service}-service/src/main/resources/${com}/${company}/${org}/${service}/${service}-states.xml";
                StringSubstitutor stringSubstitutor = new StringSubstitutor(map);
                destPath = stringSubstitutor.replace(destPath);
                File dest = new File(destPath);
                File xml = new File(xmlFile);
                try {
                    FileUtils.copyFile(xml, dest);
                }catch(Exception e){
                    throw new RuntimeException("Unable to copy " + xmlFile + " to " + destPath,e );
                }
            };
    }

    private void processXmlFile(String xmlFile, Map<String, Object> map) {
        CLIParams params = new CLIParams();
        params.xmlFile = new File(xmlFile);
        try {
            Map<String,Object> statesMap = cliHelper.toMap(params);
            map.put("workflow",statesMap);
            List<Testcase> testcases = cliHelper.renderTestCasesAsObject(params);
             map.put("testcases",testcases);
        }catch (Exception e){
            throw new RuntimeException("Unable to process XML file ", e);
        }
    }
}
