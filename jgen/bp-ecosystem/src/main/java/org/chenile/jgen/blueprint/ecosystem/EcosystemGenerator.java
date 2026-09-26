package org.chenile.jgen.blueprint.ecosystem;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.config.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Composes the established JGen blueprints into one runnable Chenile ecosystem. */
public class EcosystemGenerator {
    private final BlueprintInputService inputService = new BlueprintInputService();

    public void generate(BlueprintConfig ecosystemBlueprint, Map<String, Object> input) {
        Config config = config(input);
        try {
            generateProject("chenile-service", config, serviceInput(input), string(input, "service"));
            generateProject("minimonolith", config, monolithInput(input, string(input, "monolith"),
                    dependency(config, string(input, "service"), "-service", string(input, "ecosystemVersion")), false, false),
                    string(input, "monolith"));

            if (enabled(input, "includeServiceRegistry")) {
                generateProject("minimonolith", config, monolithInput(input, string(input, "serviceRegistryMonolith"),
                        List.of(), true, false), string(input, "serviceRegistryMonolith"));
            }
            if (enabled(input, "includeHeadlessService")) {
                generateProject("chenile-headless-service", config, headlessServiceInput(input), string(input, "headlessService"));
                generateProject("minimonolith", config, monolithInput(input, string(input, "headlessMonolith"),
                        dependency(config, string(input, "headlessService"), "-service", string(input, "ecosystemVersion")), false, false),
                        string(input, "headlessMonolith"));
            }
            if (enabled(input, "includeQueryService")) {
                generateProject("mybatisQuery", config, queryServiceInput(input), string(input, "queryNamespace"));
                generateProject("minimonolith", config, monolithInput(input, string(input, "queryMonolith"),
                        List.of(dependency(config.chenilePackage, string(input, "queryNamespace") + "-query-service",
                                string(input, "ecosystemVersion"))), false, true), string(input, "queryMonolith"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate Chenile ecosystem", e);
        }
    }

    private void generateProject(String name, Config config, Map<String, Object> supplied, String projectName) throws Exception {
        BlueprintConfig blueprint = Registry.blueprints.get(name);
        if (blueprint == null) throw new IllegalStateException("Required blueprint is unavailable: " + name);
        Path destination = Paths.get(string(supplied, "destFolder"));
        Files.createDirectories(destination);
        Path staging = Files.createTempDirectory(destination, ".jgen-ecosystem-");
        try {
            supplied.put("destFolder", staging.toString());
            new BlueprintExecutor().execute(blueprint, config, inputService.buildInputMap(blueprint, config, supplied));
            Path project = staging.resolve(projectName);
            if (!Files.isDirectory(project)) {
                throw new IllegalStateException("Blueprint " + name + " did not generate expected project " + projectName);
            }
            Files.move(project, destination.resolve(projectName));
        } finally {
            deleteDirectory(staging);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to remove ecosystem staging directory", e);
                }
            });
        }
    }

    private Map<String, Object> serviceInput(Map<String, Object> input) {
        Map<String, Object> result = common(input);
        result.put("service", string(input, "service"));
        result.put("serviceVersion", string(input, "ecosystemVersion"));
        return result;
    }

    private Map<String, Object> headlessServiceInput(Map<String, Object> input) {
        Map<String, Object> result = common(input);
        result.put("service", string(input, "headlessService"));
        result.put("serviceVersion", string(input, "ecosystemVersion"));
        result.put("registerInServiceRegistry", enabled(input, "includeServiceRegistry"));
        return result;
    }

    private Map<String, Object> queryServiceInput(Map<String, Object> input) {
        Map<String, Object> result = common(input);
        result.put("namespace", string(input, "queryNamespace"));
        result.put("namespaceVersion", string(input, "ecosystemVersion"));
        result.put("security", enabled(input, "security"));
        return result;
    }

    private Map<String, Object> monolithInput(Map<String, Object> input, String name, List<Map<String, Object>> dependencies,
                                               boolean registryServer, boolean queryService) {
        Map<String, Object> result = common(input);
        result.put("monolith", name);
        result.put("monolithVersion", string(input, "ecosystemVersion"));
        result.put("jpa", enabled(input, "jpa"));
        result.put("security", enabled(input, "security"));
        result.put("enableServiceRegistry", registryServer);
        result.put("enableServiceRegistryDelegate", !registryServer && enabled(input, "includeServiceRegistry"));
        if (!registryServer && enabled(input, "includeServiceRegistry")) result.put("serviceRegistryUrl", string(input, "serviceRegistryUrl"));
        result.put("enableCconfig", !registryServer && !queryService && name.equals(string(input, "monolith")) && enabled(input, "includeCconfig"));
        result.put("enableQueryController", queryService);
        result.put("dependencies", dependencies);
        return result;
    }

    private Map<String, Object> common(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("destFolder", string(input, "destFolder"));
        return result;
    }

    private List<Map<String, Object>> dependency(Config config, String service, String suffix, String version) {
        return List.of(dependency(config.com + "." + config.company + "." + config.org + "." + service, service + suffix, version));
    }

    private Map<String, Object> dependency(String group, String artifact, String version) {
        return Map.of("dependencyGroup", group, "dependencyName", artifact, "dependencyVersion", version);
    }

    private Config config(Map<String, Object> input) {
        Config config = new Config();
        config.chenilePackage = string(input, "chenilePackage");
        config.chenileVersion = string(input, "chenileVersion");
        config.chenileBddVersion = string(input, "chenileBddVersion");
        config.com = string(input, "com");
        config.company = string(input, "company");
        config.org = string(input, "org");
        config.defaultServiceName = string(input, "defaultServiceName");
        config.defaultVersion = string(input, "defaultVersion");
        config.defaultDestFolder = string(input, "defaultDestFolder");
        return config;
    }

    private boolean enabled(Map<String, Object> input, String key) {
        return Boolean.TRUE.equals(input.get(key));
    }

    private String string(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : value.toString();
    }
}
