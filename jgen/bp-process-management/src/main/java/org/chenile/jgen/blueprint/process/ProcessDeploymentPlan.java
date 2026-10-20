package org.chenile.jgen.blueprint.process;

import java.net.URI;
import java.util.*;
import org.chenile.orchestrator.process.config.model.ProcessDef;

/** Owns process deployment decisions; the shared expander only handles generic maps and lists. */
public class ProcessDeploymentPlan {
  public static void prepare(
      Map<String, Object> input,
      List<Map<String, Object>> processes,
      Map<String, ProcessDef> definitions) {
    String app = text(input, "application");
    input.putIfAbsent(
        "registryMode",
        Boolean.TRUE.equals(input.get("registerInServiceRegistry")) ? "existing" : "create");
    input.putIfAbsent("existingRegistryUrl", input.getOrDefault("serviceRegistryUrl", ""));
    input.putIfAbsent("registryPlacement", "server");
    input.putIfAbsent("serverDeploymentUrl", "http://localhost:8000");
    input.putIfAbsent("workerDeploymentUrl", "http://localhost:8001");
    input.putIfAbsent("registryDeploymentUrl", "http://localhost:8002");
    input.putIfAbsent("transport", "kafka");
    input.putIfAbsent("workTopic", app + "-work");
    String mode = text(input, "registryMode"), placement = text(input, "registryPlacement");
    if (!Set.of("create", "existing").contains(mode))
      throw new IllegalArgumentException("Invalid registryMode");
    if (mode.equals("create") && !Set.of("server", "separate").contains(placement))
      throw new IllegalArgumentException("Invalid registryPlacement");
    if (!"kafka".equals(input.get("transport")))
      throw new IllegalArgumentException("Unsupported transport: " + input.get("transport"));
    boolean separate = mode.equals("create") && placement.equals("separate");
    boolean colocated = mode.equals("create") && !separate;
    input.put("separateRegistry", separate);
    input.put("colocatedRegistry", colocated);
    input.put("registerInServiceRegistry", true);
    input.put(
        "serviceRegistryUrl",
        mode.equals("existing")
            ? input.get("existingRegistryUrl")
            : input.get(colocated ? "serverDeploymentUrl" : "registryDeploymentUrl"));
    Set<String> urls = new HashSet<>();
    for (String key :
        separate
            ? List.of("serverDeploymentUrl", "workerDeploymentUrl", "registryDeploymentUrl")
            : List.of("serverDeploymentUrl", "workerDeploymentUrl"))
      if (!urls.add(input.get(key).toString().replaceAll("/+$", "")))
        throw new IllegalArgumentException("Mini-monolith deployment URLs must be distinct");
    Set<String> topics = new TreeSet<>();
    for (int i = 0; i < processes.size(); i++) {
      Map<String, Object> process = new LinkedHashMap<>(processes.get(i));
      String type = process.get("processType").toString();
      process.put("workerModule", app + "-" + type + "-workers");
      processes.set(i, process);
      ProcessDef definition = definitions.get(type);
      if (definition.config == null) definition.config = new LinkedHashMap<>();
      String topic =
          definition.config.computeIfAbsent("queue", ignored -> text(input, "workTopic"));
      if (topic == null || !topic.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,248}") || topic.equals(".."))
        throw new IllegalArgumentException("Invalid Kafka queue for " + type);
      topics.add(topic);
    }
    input.put("workTopics", String.join(",", topics));
    input.put("serverMonolith", app + "Server");
    input.put("workerMonolith", app + "Worker");
    input.put("registryMonolith", app + "Registry");
  }

  public Map<String, Object> childInputs(
      Map<String, Object> current, String blueprint, Map<String, String> attributes) {
    String app = text(current, "application"), version = text(current, "applicationVersion");
    String group = text(current, "packageName");
    Map<String, Object> child = new LinkedHashMap<>();
    child.put(
        "super_pom",
        Map.of(
            "groupId",
            group,
            "artifactId",
            app + "-parent",
            "version",
            version,
            "relativePath",
            "../pom.xml"));
    if (blueprint.equals("chenile-process-workers")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> process = (Map<String, Object>) current.get("process");
      child.putAll(process);
      child.put("application", app);
      child.put("applicationVersion", version);
      child.put("packageName", group);
      child.put("dependencies", List.of(dependency(group, app + "-api", version)));
      return child;
    }
    if (!blueprint.equals("minimonolith"))
      throw new IllegalArgumentException("Unknown child blueprint: " + blueprint);
    String role = attributes.get("role");
    if (role == null || !Set.of("server", "worker", "registry").contains(role))
      throw new IllegalArgumentException("Unknown mini-monolith role: " + role);
    String monolith = text(current, role + "Monolith");
    String deploymentUrl = text(current, role + "DeploymentUrl");
    boolean worker = role.equals("worker"),
        registry =
            role.equals("registry")
                || role.equals("server") && Boolean.TRUE.equals(current.get("colocatedRegistry"));
    child.put("monolith", monolith);
    child.put("monolithVersion", version);
    child.put("jpa", !worker);
    child.put("security", false);
    child.put("deploymentUrl", deploymentUrl);
    child.put("enableServiceRegistry", registry);
    child.put("enableServiceRegistryDelegate", !registry);
    child.put("serviceRegistryUrl", current.get("serviceRegistryUrl"));
    List<Map<String, Object>> deps = new ArrayList<>();
    if (worker) {
      deps.add(dependency(group, app + "-worker-listener", version));
      for (Object item : (List<?>) current.get("processes"))
        deps.add(dependency(group, ((Map<?, ?>) item).get("workerModule").toString(), version));
    } else if (role.equals("server")) {
      deps.add(dependency(group, app + "-server-service", version));
      deps.add(dependency(group, app + "-server-config", version));
    }
    if (!worker) deps.add(dependency("org.postgresql", "postgresql", null));
    child.put("dependencies", deps);
    List<Map<String, String>> properties = new ArrayList<>();
    property(properties, "server.port", "${SERVER_PORT:" + port(deploymentUrl) + "}");
    property(properties, "spring.application.name", monolith);
    if (worker) {
      property(
          properties,
          "spring.autoconfigure.exclude",
          "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration");
      property(properties, "chenile.service.registry.read-only", "true");
      property(properties, "process.work.topics", text(current, "workTopics"));
      property(
          properties,
          "process.work.consumer-group",
          "${PROCESS_WORK_CONSUMER_GROUP:" + app + "-workers}");
    } else {
      property(
          properties,
          "spring.datasource.url",
          "${PROCESS_DATABASE_URL:jdbc:h2:file:./."
              + monolith
              + "/data;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE}");
      property(properties, "spring.datasource.username", "${PROCESS_DATABASE_USERNAME:sa}");
      property(properties, "spring.datasource.password", "${PROCESS_DATABASE_PASSWORD:}");
      property(properties, "spring.jpa.hibernate.ddl-auto", "${PROCESS_SCHEMA_MODE:update}");
      property(properties, "spring.jpa.open-in-view", "false");
      property(properties, "spring.liquibase.enabled", "false");
      if (role.equals("server")) {
        property(properties, "chenile.process.configurator", text(current, "definitionSource"));
        property(properties, "chenile.process.outbox.run-dispatcher", "true");
        property(properties, "spring.sql.init.mode", "always");
        property(
            properties,
            "spring.sql.init.schema-locations",
            "classpath:chenile-process-outbox-schema.sql");
        property(properties, "chenile.process.management.enabled", "true");
        property(properties, "chenile.process.management.api-key", "${PROCESS_MANAGEMENT_API_KEY}");
      }
    }
    if (!role.equals("registry")) {
      property(
          properties,
          "spring.kafka.bootstrap-servers",
          "${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}");
      property(
          properties,
          "spring.kafka.producer.key-serializer",
          "org.apache.kafka.common.serialization.StringSerializer");
      property(
          properties,
          "spring.kafka.producer.value-serializer",
          "org.apache.kafka.common.serialization.StringSerializer");
      property(properties, "spring.kafka.producer.acks", "all");
    }
    if (worker) {
      property(
          properties,
          "spring.kafka.consumer.key-deserializer",
          "org.apache.kafka.common.serialization.StringDeserializer");
      property(
          properties,
          "spring.kafka.consumer.value-deserializer",
          "org.apache.kafka.common.serialization.StringDeserializer");
      property(properties, "spring.kafka.consumer.enable-auto-commit", "false");
      property(properties, "spring.kafka.consumer.auto-offset-reset", "earliest");
      property(properties, "spring.kafka.listener.ack-mode", "record");
    }
    child.put("runtimeProperties", properties);
    List<Map<String, String>> entities = new ArrayList<>(),
        repositories = new ArrayList<>(),
        scans = new ArrayList<>();
    if (role.equals("server")) {
      for (String name :
          List.of(
              "org.chenile.orchestrator.process.model",
              "org.chenile.orchestrator.process.configuration.model",
              "org.chenile.trigger.cron")) entities.add(Map.of("packageName", name));
      for (String name :
          List.of("org.chenile.orchestrator.process.configuration.dao", "org.chenile.trigger.cron"))
        repositories.add(Map.of("packageName", name));
      scans.add(Map.of("packageName", "org.chenile.orchestrator.process.configuration"));
      scans.add(Map.of("packageName", group + ".bootstrap"));
    }
    if (registry) {
      entities.add(Map.of("packageName", "org.chenile.service.registry.model"));
      repositories.add(Map.of("packageName", "org.chenile.service.registry.configuration.dao"));
    }
    child.put("entityPackages", entities);
    child.put("repositoryPackages", repositories);
    child.put("scanPackages", scans);
    return child;
  }

  private static void property(List<Map<String, String>> list, String key, String value) {
    list.add(Map.of("key", key, "value", value));
  }

  private static Map<String, Object> dependency(String group, String artifact, String version) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("groupId", group);
    value.put("artifactId", artifact);
    if (version != null) value.put("version", version);
    return value;
  }

  private static String text(Map<String, Object> map, String key) {
    return Objects.toString(map.get(key), "");
  }

  private static int port(String url) {
    URI uri = URI.create(url);
    return uri.getPort() >= 0 ? uri.getPort() : uri.getScheme().equals("https") ? 443 : 80;
  }
}
