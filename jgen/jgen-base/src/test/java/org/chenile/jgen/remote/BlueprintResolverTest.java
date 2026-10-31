package org.chenile.jgen.remote;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.provenance.GenerationRecord;
import org.chenile.jgen.provenance.ProvenanceRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintResolverTest {
    @TempDir Path temporary;
    private Path home;
    private FakeFetcher fetcher;
    private final List<String> asked = new ArrayList<>();

    @BeforeEach void setUp() throws Exception {
        home = temporary.resolve("jgen-home");
        fetcher = new FakeFetcher();
        fetcher.publish("1.0.0", jar("remote-demo", "Hello {{greeting}} v1"));
        fetcher.publish("1.1.0", jar("remote-demo", "Hello {{greeting}} v1.1"));
    }

    @AfterEach void unregister() { Registry.blueprints.remove("remote-demo"); }

    private BlueprintResolver resolver(TrustPrompt.Decision decision, boolean offline) {
        return new BlueprintResolver(home, fetcher, ref -> { asked.add(ref.repository()); return decision; }, offline, false);
    }

    @Test void fetchesTheLatestReleaseOfATrustedSourceAndGeneratesFromIt() throws Exception {
        BlueprintConfig blueprint = resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-remote-demo");
        assertEquals("remote-demo", blueprint.name);
        assertEquals("github:ajapros/jgen-remote-demo@1.1.0", blueprint.fetchedFrom);
        assertTrue(asked.isEmpty(), "ajapros is trusted by default");
        assertNotNull(blueprint.classLoader);

        Config config = new ConfigProvider().obtainDefaultConfig();
        Path out = temporary.resolve("out");
        Map<String, Object> map = new HashMap<>(new BlueprintInputService()
                .buildInputMap(blueprint, config, Map.of("project", "demo", "greeting", "world")));
        map.put("destFolder", out.toString());
        new BlueprintExecutor().execute(blueprint, config, map);

        assertEquals("Hello world v1.1", Files.readString(out.resolve("demo/README.md")));
        GenerationRecord record = ProvenanceRecorder.read(out.resolve("demo"));
        assertEquals("github:ajapros/jgen-remote-demo@1.1.0", record.blueprint.reference);
        assertEquals(blueprint.sha256, record.blueprint.sha256);
    }

    @Test void untrustedSourcesNeedApprovalAndAlwaysIsRemembered() throws Exception {
        assertThrows(SecurityException.class,
                () -> resolver(TrustPrompt.Decision.DENY, false).resolve("github:stranger/jgen-remote-demo@1.0.0"));
        assertEquals(0, fetcher.downloads, "nothing is downloaded before trust is granted");

        resolver(TrustPrompt.Decision.ALLOW_ALWAYS, false).resolve("github:stranger/jgen-remote-demo@1.0.0");
        asked.clear();
        Registry.blueprints.remove("remote-demo");
        resolver(TrustPrompt.Decision.DENY, false).resolve("github:stranger/jgen-remote-demo@1.0.0");
        assertTrue(asked.isEmpty(), "trusted after ALLOW_ALWAYS");
        assertTrue(JgenSettings.load(home).trustedSources.contains("github:stranger/jgen-remote-demo"));
    }

    @Test void pinnedHashesAndTheLockFileCatchReplacedJars() throws Exception {
        BlueprintConfig first = resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-remote-demo@1.0.0");
        String good = first.sha256;
        Registry.blueprints.remove("remote-demo");

        assertThrows(SecurityException.class, () -> resolver(TrustPrompt.Decision.DENY, false)
                .resolve("github:ajapros/jgen-remote-demo@1.0.0#sha256:" + "0".repeat(64)));

        // The release asset is replaced and the cache is cleared: the lock file still remembers the first hash.
        fetcher.publish("1.0.0", jar("remote-demo", "tampered"));
        deleteTree(home.resolve("cache"));
        assertThrows(SecurityException.class,
                () -> resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-remote-demo@1.0.0"));
        assertNotEquals("", good);
    }

    @Test void offlineUsesTheNewestCachedVersionAndNeverTheNetwork() throws Exception {
        resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-remote-demo@1.0.0");
        Registry.blueprints.remove("remote-demo");
        int calls = fetcher.calls;
        BlueprintConfig offline = resolver(TrustPrompt.Decision.DENY, true).resolve("github:ajapros/jgen-remote-demo");
        assertEquals("github:ajapros/jgen-remote-demo@1.0.0", offline.fetchedFrom);
        assertEquals(calls, fetcher.calls);
        assertThrows(IOException.class, () -> resolver(TrustPrompt.Decision.DENY, true)
                .resolve("github:ajapros/jgen-remote-demo@1.1.0"));
    }

    @Test void installedBlueprintsWinAndCannotBeShadowed() throws Exception {
        BlueprintConfig local = new BlueprintConfig();
        local.name = "remote-demo";
        local.source = "file:/local/blueprint.json";
        Registry.registerBlueprint(local);
        assertSame(local, resolver(TrustPrompt.Decision.DENY, false).resolve("remote-demo"));
        assertThrows(IllegalStateException.class,
                () -> resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-remote-demo@1.0.0"));
    }

    @Test void referencesAndVersionsParse() {
        var ref = BlueprintReference.parse("github:acme/jgen-x/terraform@2.0.1#sha256:" + "a".repeat(64));
        assertEquals("acme", ref.owner());
        assertEquals("jgen-x", ref.repo());
        assertEquals("terraform", ref.blueprintName());
        assertEquals("2.0.1", ref.version());
        assertEquals("github:acme/jgen-x/terraform@2.0.1", ref.toString());
        assertThrows(IllegalArgumentException.class, () -> BlueprintReference.parse("github:acme"));
        assertTrue(BlueprintResolver.compareVersions("2.10.0", "2.9.1") > 0);
        assertTrue(BlueprintResolver.compareVersions("v1.2", "1.2.1") < 0);
    }

    /** A blueprint jar with a descriptor and a one-file template, built in memory. */
    private byte[] jar(String name, String readme) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            put(jar, "META-INF/", null);
            put(jar, "META-INF/blueprint.json", """
                {"name":"%s","description":"a fetched blueprint","templateFolder":"%s-template",
                 "inputFields":[{"name":"project","type":"STRING"},{"name":"greeting","type":"STRING","defaultValue":"hi"}]}
                """.formatted(name, name));
            put(jar, name + "-template/", null);
            put(jar, name + "-template/__project__/", null);
            put(jar, name + "-template/__project__/README.md.mustache", readme);
        }
        return bytes.toByteArray();
    }

    private static void put(JarOutputStream jar, String path, String content) throws IOException {
        jar.putNextEntry(new JarEntry(path));
        if (content != null) jar.write(content.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    /** Serves releases from memory instead of GitHub. */
    static class FakeFetcher implements BlueprintFetcher {
        final Map<String, byte[]> releases = new java.util.LinkedHashMap<>();
        int calls, downloads;

        void publish(String version, byte[] jar) { releases.put(version, jar); }

        @Override public String latestVersion(BlueprintReference reference) {
            calls++;
            return releases.keySet().stream().max(BlueprintResolver::compareVersions).orElse(null);
        }

        @Override public List<Asset> assets(BlueprintReference reference) {
            calls++;
            return List.of(new Asset(reference.repo() + "-" + reference.version() + ".jar", reference.version()),
                    new Asset(reference.repo() + "-" + reference.version() + "-sources.jar", "sources"));
        }

        @Override public void download(Asset asset, Path target) throws IOException {
            calls++;
            downloads++;
            try (OutputStream out = Files.newOutputStream(target)) { out.write(releases.get(asset.url())); }
        }
    }
}
