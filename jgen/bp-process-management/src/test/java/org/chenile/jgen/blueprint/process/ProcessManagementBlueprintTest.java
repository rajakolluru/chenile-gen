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
        @SuppressWarnings("unchecked") var generated=(List<Map<String,Object>>)input.get("processes");
        var fileUpload=generated.stream().filter(p -> p.get("processType").equals("FileUpload")).findFirst().orElseThrow();
        assertEquals("AggregatedResult<ChunkUploadOutput>", fileUpload.get("outputType"));
        assertEquals("ChunkUploadOutput", fileUpload.get("childOutputType"));
        var cron=json.readValue(input.get("cronsJson").toString(),org.chenile.trigger.cron.Crontab[].class)[0];
        assertFalse(cron.enabled);
        assertEquals("FileUpload",json.readValue(cron.eventPayloadJson,ProcessDto.class).processDefName);
    }
    @Test void supportsThreeLevelsAndMultipleRoots() throws Exception {
        var input=input(example("three-level-processes.json")); prepare(input);
        var defs=definitions(input).processes.processMap;
        assertEquals("Partition",defs.get("Record").parentProcessType);
        assertEquals("Import",defs.get("Partition").parentProcessType);
        assertEquals(3,defs.size());
        @SuppressWarnings("unchecked") var generated=(List<Map<String,Object>>)input.get("processes");
        var importProcess=generated.stream().filter(p -> p.get("processType").equals("Import")).findFirst().orElseThrow();
        var partitionProcess=generated.stream().filter(p -> p.get("processType").equals("Partition")).findFirst().orElseThrow();
        assertEquals("AggregatedResult<AggregatedResult<RecordOutput>>", importProcess.get("outputType"));
        assertEquals("AggregatedResult<RecordOutput>", importProcess.get("childOutputType"));
        assertEquals("AggregatedResult<RecordOutput>", partitionProcess.get("outputType"));
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
        var registry=input(example("file-upload-processes.json")); registry.put("registryMode","existing");
        context.map=registry;
        assertTrue(assertThrows(IllegalArgumentException.class,()->new GenericInputValidator().execute(context)).getMessage().contains("existingRegistryUrl"));
    }
    @Test void rendersRegistryChoicesAndPerTypeApiAndServiceJarsThroughTheRealPipeline() throws Exception {
        for (String mode : List.of("colocated", "separate", "existing")) {
            var input=input(example("file-upload-processes.json")); withCrons(input,example("file-upload-crons.json"));
            input.put("registryMode", mode.equals("existing") ? "existing" : "create");
            input.put("registryPlacement", mode.equals("separate") ? "separate" : "server");
            input.put("existingRegistryUrl", "http://registry.example:9000");
            input.put("definitionSource", mode.equals("existing") ? "database" : "json");
            var blueprint=json.readValue(getClass().getResourceAsStream("/META-INF/blueprint.json"),BlueprintConfig.class);
            new InitProcessManagementBlueprint().init(blueprint);
            blueprint.templateFolder=Path.of(getClass().getResource("/process-management-template").toURI()).toString();
            var context=new TemplateContext(); context.blueprintConfig=blueprint;
            context.config=new ConfigProvider().obtainDefaultConfig(); context.config.company="example"; context.config.org="process"; context.map=input; context.isJar=false;
            var config=new XmlOrchConfigurator<TemplateContext>(); config.setBeanFactoryAdapter(new ProcessorFactory("org.chenile.jgen.template.chain."));
            config.setFilename("org/chenile/jgen/template/processors.xml");
            var executor=new OrchExecutorImpl<TemplateContext>(); executor.setOrchConfigurator(config); executor.execute(context);
            Path root=Path.of(input.get("destFolder").toString()).resolve("example");
            assertTrue(Files.isExecutable(root.resolve("run-server.sh")));
            assertTrue(Files.isExecutable(root.resolve("run-workers.sh")));
            if (mode.equals("separate")) {
                assertTrue(Files.isExecutable(root.resolve("run-registry.sh")));
            }
            assertFalse(Files.exists(root.resolve("example-api/src/main/java/com/example/process/example/model/ChunkUploadIn.java")));
            assertTrue(Files.exists(root.resolve("example-FileUpload-workers/api/pom.xml")));
            assertTrue(Files.exists(root.resolve("example-FileUpload-workers/service/pom.xml")));
            assertTrue(Files.exists(root.resolve("example-FileUpload-workers/service/src/main/java/com/example/process/example/worker/FileUploadAggregator.java")));
            assertTrue(Files.exists(root.resolve("example-FileUpload-workers/service/src/main/java/com/example/process/example/worker/FileUploadSplitter.java")));
            assertTrue(Files.exists(root.resolve("example-ChunkUpload-workers/api/src/main/java/com/example/process/example/model/ChunkUploadIn.java")));
            assertTrue(Files.exists(root.resolve("example-ChunkUpload-workers/api/src/main/java/com/example/process/example/model/ChunkUploadOutput.java")));
            assertTrue(Files.exists(root.resolve("example-ChunkUpload-workers/service/src/main/java/com/example/process/example/worker/ChunkUploadExecutor.java")));
            String childApiPom=Files.readString(root.resolve("example-FileUpload-workers/api/pom.xml"));
            assertTrue(childApiPom.contains("example-ChunkUpload-api"));
            String aggregator=Files.readString(root.resolve("example-FileUpload-workers/service/src/main/java/com/example/process/example/worker/FileUploadAggregator.java"));
            assertTrue(aggregator.contains("AggregatedResult<ChunkUploadOutput>"));
            assertTrue(aggregator.contains("out.outputs.add(childOutput)"));
            assertTrue(aggregator.contains("out.errors.addAll(error.errors)"));
            assertFalse(Files.exists(root.resolve("example-server-service/src/main/java/com/example/process/example/worker")));
            String service=Files.readString(root.resolve("example-server-service/src/main/java/com/example/process/example/service/FileUploadServiceImpl.java"));
            assertTrue(service.contains("manager.create(dto)")); assertFalse(service.contains("ProcessWorkers"));
            String serverServicePom=Files.readString(root.resolve("example-server-service/pom.xml"));
            assertTrue(serverServicePom.contains("<artifactId>chenile-kafka</artifactId>"));
            String serverConfig=Files.readString(root.resolve("example-server-service/src/main/java/com/example/process/example/configuration/ExampleProcessConfiguration.java"));
            assertTrue(serverConfig.contains("PubSubConfiguration.class"));
            assertTrue(serverConfig.contains("KafkaConfiguration.class"));
            assertFalse(Files.exists(root.resolve("example-server-service/src/main/java/com/example/process/example/configuration/ProcessKafkaPublisher.java")));
            String serverTest=Files.readString(root.resolve("example-server-service/src/test/java/com/example/process/example/bdd/CukesRestTest.java"));
            assertTrue(serverTest.contains("extends org.chenile.mqtt.test.KafkaBaseTest"));
            String workerTest=Files.readString(root.resolve("example-worker-listener/src/test/java/com/example/process/example/worker/WorkerListenerTest.java"));
            assertTrue(workerTest.contains("extends org.chenile.mqtt.test.KafkaBaseTest"));
            assertTrue(workerTest.contains("kafka.send(publication)"));
            assertFalse(workerTest.contains("listener.auto-startup=false"));
            String steps=Files.readString(root.resolve("example-server-service/src/test/java/com/example/process/example/bdd/CukesSteps.java"));
            assertTrue(steps.contains("the committed process is published to Kafka"));
            assertTrue(steps.contains("pubsub.publish.waitTime=30000"));
            assertFalse(steps.contains("localhost:9092"));
            String workerPom=Files.readString(root.resolve("exampleWorker/exampleWorker-package/pom.xml"));
            assertTrue(workerPom.contains("example-FileUpload-service"));
            assertTrue(workerPom.contains("example-ChunkUpload-service"));
            assertFalse(workerPom.contains("process-service"));
            String serverPom=Files.readString(root.resolve("exampleServer/exampleServer-package/pom.xml"));
            assertEquals(mode.equals("colocated"), serverPom.contains("service-registry-service"));
            assertTrue(serverPom.contains("spring-boot-h2console"));
            String serverProperties=Files.readString(root.resolve("exampleServer/exampleServer-configurations/src/main/resources/application.properties"));
            assertTrue(serverProperties.contains("spring.h2.console.enabled=${PROCESS_H2_CONSOLE_ENABLED:true}"));
            assertTrue(serverProperties.contains("pubsub.topic.separator=_"));
            assertTrue(
                serverProperties.contains("pubsub.publish.waitTime=${PUBSUB_PUBLISH_WAIT_TIME_MS:30000}"),
                serverProperties);
            String serverYaml=Files.readString(root.resolve("exampleServer/exampleServer-configurations/src/main/resources/application.yml"));
            assertTrue(serverYaml.contains("enabled: ${PROCESS_H2_CONSOLE_ENABLED:true}"));
            assertEquals(mode.equals("separate"), Files.exists(root.resolve("exampleRegistry")));
            String properties=Files.readString(root.resolve("exampleWorker/exampleWorker-configurations/src/main/resources/application.properties"));
            assertTrue(properties.contains("DataSourceAutoConfiguration")); assertTrue(properties.contains("server.port=${SERVER_PORT:8001}"));
            String registry=Files.readString(root.resolve("exampleWorker/exampleWorker-configurations/src/main/resources/application.yml"));
            assertTrue(registry.contains(mode.equals("existing") ? "http://registry.example:9000" : mode.equals("separate") ? "http://localhost:8002" : "http://localhost:8000"));
            try (var paths=Files.walk(root)) { assertFalse(paths.anyMatch(p -> p.toString().endsWith(".mustache") || p.toString().contains("%{{") || p.toString().contains(".jgen-child-"))); }
        }
    }
}
