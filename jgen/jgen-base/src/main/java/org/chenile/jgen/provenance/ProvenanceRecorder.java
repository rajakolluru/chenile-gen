package org.chenile.jgen.provenance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.util.BlueprintValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Reads and writes {@link GenerationRecord}s and computes file manifests. */
public final class ProvenanceRecorder {
    public static final String RECORD_FOLDER = ".jgen";
    public static final String RECORD_FILE = "generation.json";
    /** Set the system property {@code jgen.record=false} to stop JGen writing records. */
    public static final String DISABLE_PROPERTY = "jgen.record";

    /** Folders never treated as generated content when hashing a project. */
    private static final Set<String> SKIPPED = Set.of(RECORD_FOLDER, ".git", "target", "build", "node_modules", ".idea");

    static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ProvenanceRecorder() { }

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY));
    }

    /** The declared, visible inputs as supplied, minus the output location. Deep-copied so hooks cannot change it. */
    public static Map<String, Object> snapshotInputs(BlueprintConfig blueprint, Map<String, Object> map) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        if (blueprint.inputFields == null) return inputs;
        for (InputField field : blueprint.inputFields) {
            if ("destFolder".equals(field.name) || !map.containsKey(field.name)) continue;
            inputs.put(field.name, map.get(field.name));
        }
        return MAPPER.convertValue(inputs, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    /** The generated project's folder: {@code destFolder/<prefix><project>}. */
    public static Path projectDirectory(BlueprintConfig blueprint, Map<String, Object> map) {
        String project = String.valueOf(map.get(BlueprintValidationUtils.projectField(blueprint)));
        String prefix = blueprint.projectDirectoryPrefix == null ? "" : blueprint.projectDirectoryPrefix;
        return Path.of(String.valueOf(map.get("destFolder"))).toAbsolutePath().normalize().resolve(prefix + project);
    }

    /** Writes {@code .jgen/generation.json} (and copies of FILE inputs) into the generated project. */
    public static GenerationRecord write(BlueprintConfig blueprint, Config config, Map<String, Object> inputs,
                                         Path projectDir) throws IOException {
        GenerationRecord record = new GenerationRecord();
        record.generatedAt = Instant.now().toString();
        record.jgenVersion = jgenVersion();
        record.blueprint = blueprintRef(blueprint);
        record.projectDirectory = projectDir.getFileName().toString();
        record.config = new ConfigProvider().getConfigAsMap(config);
        record.inputs = new LinkedHashMap<>(inputs);
        Path recordDir = projectDir.resolve(RECORD_FOLDER);
        Files.createDirectories(recordDir);
        if (blueprint.inputFields != null) {
            for (InputField field : blueprint.inputFields) {
                Object value = inputs.get(field.name);
                if (field.type != FieldType.FILE || value == null || value.toString().isBlank()) continue;
                Path source = Path.of(value.toString());
                if (!Files.isRegularFile(source)) continue;
                Path copy = recordDir.resolve("inputs").resolve(field.name).resolve(source.getFileName().toString());
                Files.createDirectories(copy.getParent());
                Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
                record.fileInputs.put(field.name, projectDir.relativize(copy).toString().replace('\\', '/'));
            }
        }
        record.files = manifest(projectDir);
        MAPPER.writeValue(recordDir.resolve(RECORD_FILE).toFile(), record);
        return record;
    }

    public static GenerationRecord read(Path projectDir) throws IOException {
        Path file = projectDir.resolve(RECORD_FOLDER).resolve(RECORD_FILE);
        if (!Files.isRegularFile(file))
            throw new IOException("No JGen generation record at " + file + "; was this project generated by JGen 2.1.35 or later?");
        GenerationRecord record = MAPPER.readValue(file.toFile(), GenerationRecord.class);
        if (!GenerationRecord.SCHEMA.equals(record.schema))
            throw new IOException("Unsupported generation record schema " + record.schema + " in " + file);
        return record;
    }

    /** SHA-256 of every file under the folder, keyed by '/'-separated relative path, skipping tool folders. */
    public static TreeMap<String, String> manifest(Path root) throws IOException {
        TreeMap<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(root)) return files;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path)) continue;
                Path relative = root.relativize(path);
                if (SKIPPED.contains(relative.getName(0).toString())) continue;
                files.put(relative.toString().replace('\\', '/'), sha256(path));
            }
        }
        return files;
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GenerationRecord.BlueprintRef blueprintRef(BlueprintConfig blueprint) {
        GenerationRecord.BlueprintRef ref = new GenerationRecord.BlueprintRef();
        ref.name = blueprint.name;
        ref.version = blueprint.version;
        Properties pom = jarPom(blueprint.source);
        if (pom != null) {
            ref.groupId = pom.getProperty("groupId");
            ref.artifactId = pom.getProperty("artifactId");
            ref.artifactVersion = pom.getProperty("version");
        }
        return ref;
    }

    /** The Maven pom.properties of the jar that holds a blueprint.json, or null if it was not loaded from a jar. */
    static Properties jarPom(String source) {
        if (source == null || !source.startsWith("jar:")) return null;
        try {
            JarURLConnection connection = (JarURLConnection) new URL(source).openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) {
                for (JarEntry entry : (Iterable<JarEntry>) jar.stream()::iterator) {
                    String name = entry.getName();
                    if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            Properties properties = new Properties();
                            properties.load(in);
                            return properties;
                        }
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String jgenVersion() {
        try (InputStream in = ProvenanceRecorder.class.getResourceAsStream(
                "/META-INF/maven/org.chenile/jgen-base/pom.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                return properties.getProperty("version");
            }
        } catch (IOException ignored) {
            // fall through
        }
        return ProvenanceRecorder.class.getPackage().getImplementationVersion();
    }
}
