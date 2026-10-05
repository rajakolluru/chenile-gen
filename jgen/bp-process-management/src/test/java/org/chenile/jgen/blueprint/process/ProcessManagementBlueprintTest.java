package org.chenile.jgen.blueprint.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.orchestrator.process.config.reader.ProcessConfigurator;
import org.chenile.orchestrator.process.config.model.PredecessorArgs;
import org.chenile.orchestrator.process.model.ProcessDto;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.template.chain.GenericInputValidator;
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
    private void withCrons(Map<String,Object> input, String cronJson) throws Exception {
        Path file=temporary.resolve(UUID.randomUUID() + "-crons.json"); Files.writeString(file,cronJson);
        input.put("cronSpec",file.toString());
    }
    @Test void acceptsTheRuntimeDefFixtureAndEmbeddedInputFields() throws Exception {
        String nativeDefs;
        try (var stream=getClass().getResourceAsStream("/native-def.json")) {
            nativeDefs=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        var input=input(nativeDefs); prepare(input);
        assertEquals(Set.of("feed","file","chunk"),definitions(input).processes.processMap.keySet());
        assertEquals("com.example.process.example.model.FeedIn",definitions(input).findByName("feed").args);
        assertEquals(0,json.readValue(input.get("cronsJson").toString(),org.chenile.trigger.cron.Crontab[].class).length);
        var embedded=input("""
            {"processMap":{"feed":{"leaf":true,"inputFields":{"batchId":"string"},"future":true}},"future":{}}
            """);
        prepare(embedded);
        @SuppressWarnings("unchecked") var generated=(List<Map<String,Object>>)embedded.get("processes");
        assertEquals(List.of(Map.of("name","batchId","javaType","String")),generated.get(0).get("fields"));
    }
    private String example(String name) throws Exception { return Files.readString(Path.of("examples",name)); }
    private void prepare(Map<String,Object> input) { new InitProcessManagementBlueprint().prepare(input); }
    private void reject(String spec, String message) throws Exception {
        var input=input(spec);
        assertTrue(assertThrows(IllegalArgumentException.class,()->prepare(input)).getMessage().contains(message));
        assertFalse(Files.exists(Path.of(input.get("destFolder").toString())));
    }
    private ProcessConfigurator definitions(Map<String,Object> input) throws Exception {
        var reader = new ProcessConfigurator();
        reader.read(new ByteArrayInputStream(input.get("defsJson").toString().getBytes(StandardCharsets.UTF_8)));
        return reader;
    }
    @Test void examplesProduceNativeDefinitionsAndTypedCronPayloads() throws Exception {
        var input=input(example("file-upload-processes.json")); withCrons(input,example("file-upload-crons.json")); prepare(input);
        var defs=definitions(input).processes.processMap;
        assertFalse(defs.get("FileUpload").leaf);
        assertTrue(defs.get("ChunkUpload").leaf);
        assertEquals("FileUpload",defs.get("ChunkUpload").parentProcessType);
        assertEquals("1048576",defs.get("FileUpload").config.get("chunkBytes"));
        assertEquals("com.example.process.example.model.FileUploadIn",defs.get("FileUpload").args);
        var cron=json.readValue(input.get("cronsJson").toString(),org.chenile.trigger.cron.Crontab[].class)[0];
        assertFalse(cron.enabled);
        assertEquals("FileUpload",json.readValue(cron.eventPayloadJson,ProcessDto.class).processDefName);
    }
    @Test void supportsThreeLevelsMultipleChildTypesAndMultipleRoots() throws Exception {
        var input=input(example("three-level-processes.json")); prepare(input);
        var defs=definitions(input).processes.processMap;
        assertEquals("Partition",defs.get("Audit").parentProcessType);
        assertEquals("Import",defs.get("Partition").parentProcessType);
        assertEquals(4,defs.size());
        var roots=input("""
            {"processMap":{"One":{"leaf":true},"Two":{"leaf":true}}}
            """);
        prepare(roots); assertEquals(2,definitions(roots).processes.processMap.size());
    }
    @Test void configIsOpaqueAndPredecessorArgumentsUseRuntimeContract() throws Exception {
        var input=input("""
            {"processMap":{
              "One":{"leaf":true,"config":{"chunkBytes":"application decides","anything":"opaque"}},
              "Two":{"leaf":true,"predecessorProcessType":"One","predecessorArgs":"OUTPUT"}
            }}
            """);
        prepare(input);
        var defs=definitions(input).processes.processMap;
        assertEquals("application decides",defs.get("One").config.get("chunkBytes"));
        assertEquals(PredecessorArgs.OUTPUT,defs.get("Two").predecessorArgs);
        assertEquals("One",defs.get("Two").predecessorProcessType);
    }
    @Test void rejectsDuplicateUnknownAndCyclicHierarchy() throws Exception {
        reject("""
            {"processMap":{"A":{"leaf":true},"A":{"leaf":true}}}
            ""","Duplicate field");
        reject("""
            {"processMap":{"A":{"leaf":true,"parentProcessType":"Missing"}}}
            ""","Unknown process");
        reject("""
            {"processMap":{"A":{"parentProcessType":"B"},"B":{"parentProcessType":"A"}}}
            ""","Cycle");
        reject("""
            {"processMap":{"A":{"leaf":false}}}
            ""","leaf must match");
    }
    @Test void rejectsUnsafeNamesTypesAndUnknownMetadata() throws Exception {
        reject("""
            {"processMap":{"../Escape":{"leaf":true}}}
            ""","identifier");
        reject("""
            {"processMap":{"A":{"leaf":true}},"inputFields":{"A":{"class":"string"}}}
            ""","identifier");
        reject("""
            {"processMap":{"A":{"leaf":true}},"inputFields":{"A":{"value":"java.io.File"}}}
            ""","Unsupported field type");
        reject("""
            {"processMap":{"A":{"leaf":true}},"inputFields":{"Missing":{}}}
            ""","Unknown inputFields");
        reject("""
            {"processMap":{"A":{"leaf":true,"args":"Shared"},"B":{"leaf":true,"args":"Shared"}}}
            ""","Duplicate or reserved");
    }
    @Test void rejectsInvalidCronAndPredecessorCycles() throws Exception {
        for (String cron : List.of(example("file-upload-crons.json").replace("0 0 2 * * ?","invalid"),
                example("file-upload-crons.json").replace("\"incoming/upload.dat\"","5"))) {
            var input=input(example("file-upload-processes.json")); withCrons(input,cron);
            assertThrows(IllegalArgumentException.class,()->prepare(input));
        }
        reject("""
            {"processMap":{"A":{"leaf":true,"predecessorProcessType":"B"},
                           "B":{"leaf":true,"predecessorProcessType":"A"}}}
            ""","predecessor chain");
    }
    @Test void refusesOverwriteAndRegistryWithoutUrl() throws Exception {
        var blueprint=json.readValue(getClass().getResourceAsStream("/META-INF/blueprint.json"),BlueprintConfig.class);
        var context=new TemplateContext(); context.blueprintConfig=blueprint;
        var input=input(example("file-upload-processes.json"));
        Files.createDirectories(Path.of(input.get("destFolder").toString()).resolve("example"));
        context.map=input;
        assertTrue(assertThrows(IllegalArgumentException.class,()->new GenericInputValidator().execute(context)).getMessage().contains("already exists"));
        var registry=input(example("file-upload-processes.json")); registry.put("registerInServiceRegistry",true);
        context.map=registry;
        assertTrue(assertThrows(IllegalArgumentException.class,()->new GenericInputValidator().execute(context)).getMessage().contains("serviceRegistryUrl"));
    }
    @Test void rendersAllModulesWorkersInputsAndRegistrationOptionsThroughRealPipeline() throws Exception {
        String models="/process-management-template/__application__/__application__-api/src/main/java/__com__/__company__/__org__/__application__/model/";
        assertNotNull(getClass().getResource(models + "ProcessResult.java.mustache"));
        try (var filelist=getClass().getResourceAsStream(models + "inputs.filelist.mustache")) {
            assertFalse(new String(filelist.readAllBytes(), StandardCharsets.UTF_8).contains("ProcessResult"));
        }
        for (boolean registry : List.of(false,true)) {
            var input=input(example("file-upload-processes.json")); withCrons(input,example("file-upload-crons.json")); input.put("registerInServiceRegistry",registry);
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
            String result=Files.readString(root.resolve("example-api/src/main/java/com/example/process/example/model/ProcessResult.java"));
            assertTrue(result.contains("public class ProcessResult"));
            assertTrue(result.contains("public List<Object> outputs"));
            assertFalse(result.contains("--START--"));
            assertTrue(Files.exists(root.resolve("example-service/src/main/java/com/example/process/example/worker/FileUploadAggregator.java")));
            assertFalse(Files.exists(root.resolve("example-service/src/main/java/com/example/process/example/worker/FileWork.java")));
            String splitter=Files.readString(root.resolve("example-service/src/main/java/com/example/process/example/worker/FileUploadSplitter.java"));
            assertTrue(splitter.contains("UnsupportedOperationException"));
            assertFalse(splitter.contains("chunkBytes"));
            assertFalse(splitter.contains("filename"));
            String bootstrap=Files.readString(root.resolve("example-package/src/main/java/com/example/process/example/bootstrap/GeneratedProcessBootstrap.java"));
            assertFalse(bootstrap.contains("readTree"));
            String pom=Files.readString(root.resolve("example-package/pom.xml"));
            assertEquals(registry,pom.contains("service-registry-delegate"));
            try (var paths=Files.walk(root)) { assertFalse(paths.anyMatch(p -> p.toString().endsWith(".mustache") || p.toString().contains("__application__"))); }
        }
    }
}
