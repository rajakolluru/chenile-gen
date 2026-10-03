package org.chenile.jgen.blueprint.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.util.ProcessorFactory;
import org.chenile.owiz.config.impl.XmlOrchConfigurator;
import org.chenile.owiz.impl.OrchExecutorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessManagementBlueprintTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();

    private Map<String,Object> input(String spec) throws Exception {
        Path file = temporary.resolve(UUID.randomUUID() + ".json"); Files.writeString(file,spec);
        var map = new HashMap<String,Object>();
        map.put("application","example"); map.put("applicationVersion","0.0.1-SNAPSHOT");
        map.put("destFolder",temporary.resolve(UUID.randomUUID().toString()).toString());
        map.put("processSpec",file.toString()); map.put("com","com"); map.put("company","example"); map.put("org","process");
        map.put("definitionSource","json"); map.put("registerInServiceRegistry",false);
        return map;
    }
    private String example(String name) throws Exception { return Files.readString(Path.of("examples",name)); }
    private void prepare(Map<String,Object> input) { new InitProcessManagementBlueprint().prepare(input); }
    private void reject(String spec, String message) throws Exception {
        var input=input(spec);
        assertTrue(assertThrows(IllegalArgumentException.class,()->prepare(input)).getMessage().contains(message));
        assertFalse(Files.exists(Path.of(input.get("destFolder").toString())));
    }
    @Test void fileExampleProducesTypedDefinitionsAndCronPayload() throws Exception {
        var input=input(example("file-upload-processes.json")); prepare(input);
        var defs=json.readTree(input.get("defsJson").toString()).get("processMap");
        assertFalse(defs.get("FileUpload").get("leaf").asBoolean());
        assertTrue(defs.get("ChunkUpload").get("leaf").asBoolean());
        assertEquals("FileUpload",defs.get("ChunkUpload").get("parentProcessType").asText());
        assertEquals("1048576",defs.get("FileUpload").get("config").get("chunkBytes").asText());
        assertEquals("com.example.process.example.model.FileUploadIn",defs.get("FileUpload").get("args").asText());
        var cron=json.readTree(input.get("cronsJson").toString()).get(0);
        assertFalse(cron.get("enabled").asBoolean());
        assertEquals("FileUpload",cron.get("payload").get("processDefName").asText());
    }
    @Test void supportsThreeLevelsMultipleChildTypesAndMultipleRoots() throws Exception {
        var input=input(example("three-level-processes.json")); prepare(input);
        var defs=json.readTree(input.get("defsJson").toString()).get("processMap");
        assertEquals("Partition",defs.get("Audit").get("parentProcessType").asText());
        assertEquals("Import",defs.get("Partition").get("parentProcessType").asText());
        assertEquals(4,defs.size());
        var roots=input("{\"processes\":[{\"processType\":\"One\",\"fields\":{}},{\"processType\":\"Two\",\"fields\":{}}]}");
        prepare(roots); assertEquals(2,json.readTree(roots.get("defsJson").toString()).get("processMap").size());
    }
    @Test void rejectsDuplicateUnknownSharedAndCyclicChildren() throws Exception {
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{}},{\"processType\":\"A\",\"fields\":{}}]}","Duplicate processType");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{},\"children\":[\"Missing\"]}]}","Unknown child");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{},\"children\":[\"B\"]},{\"processType\":\"B\",\"fields\":{},\"children\":[\"A\"]}]}","Cycle");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{},\"children\":[\"C\"]},{\"processType\":\"B\",\"fields\":{},\"children\":[\"C\"]},{\"processType\":\"C\",\"fields\":{}}]}","only one parent");
    }
    @Test void rejectsUnsafeNamesTypesAndFileWorkerContracts() throws Exception {
        reject("{\"processes\":[{\"processType\":\"../Escape\",\"fields\":{}}]}","identifier");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{\"class\":\"string\"}}]}","identifier");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{\"value\":\"java.io.File\"}}]}","Unsupported field type");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{},\"implementation\":\"fileRead\"}]}","filename");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{\"filename\":\"string\"},\"implementation\":\"fileSplit\"}]}","exactly one child");
    }
    @Test void rejectsInvalidCronAndPredecessorCycles() throws Exception {
        reject(example("file-upload-processes.json").replace("0 0 2 * * ?","invalid"),"cronExpression");
        reject(example("file-upload-processes.json").replace("incoming/upload.dat","" ).replace("\"filename\": \"\"","\"filename\": 5"),"Wrong type");
        reject("{\"processes\":[{\"processType\":\"A\",\"fields\":{},\"predecessorProcessType\":\"B\"},{\"processType\":\"B\",\"fields\":{},\"predecessorProcessType\":\"A\"}]}","predecessor chain");
    }
    @Test void refusesOverwriteAndRegistryWithoutUrl() throws Exception {
        var input=input(example("file-upload-processes.json"));
        Files.createDirectories(Path.of(input.get("destFolder").toString()).resolve("example"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->prepare(input)).getMessage().contains("already exists"));
        var registry=input(example("file-upload-processes.json")); registry.put("registerInServiceRegistry",true);
        assertTrue(assertThrows(IllegalArgumentException.class,()->prepare(registry)).getMessage().contains("serviceRegistryUrl"));
    }
    @Test void rendersAllModulesWorkersInputsAndRegistrationOptionsThroughRealPipeline() throws Exception {
        for (boolean registry : List.of(false,true)) {
            var input=input(example("file-upload-processes.json")); input.put("registerInServiceRegistry",registry);
            input.put("serviceRegistryUrl","http://localhost:8082"); input.put("definitionSource",registry ? "database" : "json");
            var blueprint=json.readValue(getClass().getResourceAsStream("/META-INF/blueprint.json"),BlueprintConfig.class);
            new InitProcessManagementBlueprint().init(blueprint);
            blueprint.templateFolder=Path.of(getClass().getResource("/process-management-template").toURI()).toString();
            var context=new TemplateContext(); context.blueprintConfig=blueprint;
            context.config=new ConfigProvider().obtainDefaultConfig(); context.map=input; context.isJar=false;
            var config=new XmlOrchConfigurator<TemplateContext>(); config.setBeanFactoryAdapter(new ProcessorFactory("org.chenile.jgen.template.chain."));
            config.setFilename("org/chenile/jgen/template/processors.xml");
            var executor=new OrchExecutorImpl<TemplateContext>(); executor.setOrchConfigurator(config); executor.execute(context);
            Path root=Path.of(input.get("destFolder").toString()).resolve("example");
            String controller=Files.readString(root.resolve("example-service/src/main/java/com/example/process/example/configuration/controller/FileUploadController.java"));
            assertTrue(controller.contains("registerInServiceRegistry=" + registry));
            assertTrue(Files.exists(root.resolve("example-api/src/main/java/com/example/process/example/model/ChunkUploadIn.java")));
            assertTrue(Files.exists(root.resolve("example-service/src/main/java/com/example/process/example/worker/FileUploadAggregator.java")));
            assertTrue(Files.exists(root.resolve("example-service/src/main/java/com/example/process/example/worker/FileWork.java")));
            String pom=Files.readString(root.resolve("example-package/pom.xml"));
            assertEquals(registry,pom.contains("service-registry-delegate"));
            try (var paths=Files.walk(root)) { assertFalse(paths.anyMatch(p -> p.toString().endsWith(".mustache") || p.toString().contains("__application__"))); }
        }
    }
}
