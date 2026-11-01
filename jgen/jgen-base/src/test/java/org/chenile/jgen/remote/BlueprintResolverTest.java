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

    @AfterEach void unregister() {
        Registry.blueprints.remove("remote-demo");
        Registry.blueprints.remove("tpl-demo");
    }

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

    /** A GitHub source archive of a template-only blueprint: one top folder, laid out like a jar's resources. */
    private byte[] sourceZip(String readme, boolean withInitHook) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        String top = "acme-jgen-tpl-demo-1a2b3c/src/main/resources/";
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            putZip(zip, "acme-jgen-tpl-demo-1a2b3c/README.md", "a template-only blueprint");
            putZip(zip, top + "META-INF/blueprint.json", """
                {"name":"tpl-demo","description":"template only",%s"templateFolder":"tpl-demo-template",
                 "inputFields":[{"name":"project","type":"STRING"},{"name":"greeting","type":"STRING","defaultValue":"hi"}]}
                """.formatted(withInitHook ? "\"initHook\":\"com.acme.Missing\"," : ""));
            putZip(zip, top + "tpl-demo-template/__project__/README.md.mustache", readme);
            putZip(zip, top + "tpl-demo-template/__project__/_DoT_gitignore", "target/\n");
        }
        return bytes.toByteArray();
    }

    private static void putZip(java.util.zip.ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new java.util.zip.ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private Path generateFrom(BlueprintConfig blueprint, String project) throws Exception {
        Config config = new ConfigProvider().obtainDefaultConfig();
        Path out = temporary.resolve("out-" + System.nanoTime());
        Map<String, Object> map = new HashMap<>(new BlueprintInputService()
                .buildInputMap(blueprint, config, Map.of("project", project, "greeting", "world")));
        map.put("destFolder", out.toString());
        new BlueprintExecutor().execute(blueprint, config, map);
        return out.resolve(project);
    }

    @Test void aReleaseWithoutAJarIsATemplateOnlyBlueprint() throws Exception {
        fetcher.publishSource("0.1.0", sourceZip("Hello {{greeting}} from a template", false));
        BlueprintConfig blueprint = resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-tpl-demo");
        assertEquals("tpl-demo", blueprint.name);
        assertEquals("github:ajapros/jgen-tpl-demo@0.1.0", blueprint.fetchedFrom);

        Path project = generateFrom(blueprint, "demo");
        assertEquals("Hello world from a template", Files.readString(project.resolve("README.md")));
        assertTrue(Files.isRegularFile(project.resolve(".gitignore")));
        assertEquals(blueprint.sha256, ProvenanceRecorder.read(project).blueprint.sha256);
    }

    @Test void templateOnlyBlueprintsAreTrustedAndLockedLikeJars() throws Exception {
        fetcher.publishSource("0.1.0", sourceZip("v1", false));
        assertThrows(SecurityException.class,
                () -> resolver(TrustPrompt.Decision.DENY, false).resolve("github:stranger/jgen-tpl-demo@0.1.0"));
        assertEquals(0, fetcher.downloads);

        String first = resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-tpl-demo@0.1.0").sha256;
        Registry.blueprints.remove("tpl-demo");
        fetcher.publishSource("0.1.0", sourceZip("tampered", false));
        deleteTree(home.resolve("cache"));
        assertThrows(SecurityException.class,
                () -> resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-tpl-demo@0.1.0"));
        assertNotNull(first);
    }

    @Test void templateOnlyBlueprintsMayNotDeclareHooks() throws Exception {
        fetcher.publishSource("0.1.0", sourceZip("x", true));
        var error = assertThrows(IllegalArgumentException.class,
                () -> resolver(TrustPrompt.Decision.DENY, false).resolve("github:ajapros/jgen-tpl-demo@0.1.0"));
        assertTrue(error.getMessage().contains("initHook"));
    }

    @Test void aLocalFolderIsATemplateOnlyBlueprint() throws Exception {
        Path repo = temporary.resolve("my-blueprint");
        Path resources = repo.resolve("src/main/resources");
        Files.createDirectories(resources.resolve("META-INF"));
        Files.writeString(resources.resolve("META-INF/blueprint.json"), """
            {"name":"tpl-demo","templateFolder":"tpl-demo-template",
             "inputFields":[{"name":"project","type":"STRING"},{"name":"greeting","type":"STRING","defaultValue":"hi"}]}
            """);
        Path template = resources.resolve("tpl-demo-template/__project__/README.md.mustache");
        Files.createDirectories(template.getParent());
        Files.writeString(template, "Local {{greeting}}");

        BlueprintConfig blueprint = resolver(TrustPrompt.Decision.DENY, false).resolve("dir:" + repo);
        assertEquals("dir:" + repo.toAbsolutePath().normalize(), blueprint.fetchedFrom);
        assertEquals("Local world", Files.readString(generateFrom(blueprint, "demo").resolve("README.md")));
        assertTrue(asked.isEmpty(), "local folders need no trust prompt");

        Files.writeString(template, "Edited {{greeting}}");
        BlueprintConfig edited = resolver(TrustPrompt.Decision.DENY, false).resolve("dir:" + repo);
        assertNotEquals(blueprint.sha256, edited.sha256, "edits change the content hash");
        assertEquals("Edited world", Files.readString(generateFrom(edited, "again").resolve("README.md")));
    }

    private void officialCatalog(String json) {
        fetcher.catalogs.put("ajapros/jgen-catalog", json);
    }

    @Test void aShortNameIsFoundInTheOfficialCatalog() throws Exception {
        officialCatalog("""
            {"schema":"jgen.catalog/1","blueprints":{
              "demo":{"source":"github:stranger/jgen-remote-demo","description":"A demo","tags":["demo"]}}}
            """);
        BlueprintConfig blueprint = resolver(TrustPrompt.Decision.DENY, false).resolve("demo");
        assertEquals("remote-demo", blueprint.name);
        assertEquals("github:stranger/jgen-remote-demo@1.1.0", blueprint.fetchedFrom);
        assertTrue(asked.isEmpty(), "sources listed in the official catalog are trusted");

        Registry.blueprints.remove("remote-demo");
        assertEquals("github:stranger/jgen-remote-demo@1.0.0",
                resolver(TrustPrompt.Decision.DENY, false).resolve("demo@1.0.0").fetchedFrom);
        Registry.blueprints.remove("remote-demo");
        assertEquals("github:stranger/jgen-remote-demo@1.0.0",
                resolver(TrustPrompt.Decision.DENY, false).resolve("ajapros/demo@1.0.0").fetchedFrom);
    }

    @Test void catalogPinsAreEnforced() throws Exception {
        officialCatalog("""
            {"blueprints":{"demo":{"source":"github:ajapros/jgen-remote-demo","versions":{"1.0.0":"%s"}}}}
            """.formatted("0".repeat(64)));
        assertThrows(SecurityException.class, () -> resolver(TrustPrompt.Decision.DENY, false).resolve("demo@1.0.0"));
        // an unpinned version is accepted
        assertEquals("github:ajapros/jgen-remote-demo@1.1.0",
                resolver(TrustPrompt.Decision.DENY, false).resolve("demo@1.1.0").fetchedFrom);
    }

    @Test void teamCatalogsAreSearchedInOrderAndNeedTrustUnlessMarkedTrusted() throws Exception {
        officialCatalog("{\"blueprints\":{}}");
        fetcher.catalogs.put("acme/jgen-catalog", """
            {"blueprints":{"demo":{"source":"github:stranger/jgen-remote-demo","description":"Acme demo"}}}
            """);
        JgenSettings settings = new JgenSettings();
        settings.catalogs.add(new JgenSettings.CatalogSource("github:acme/jgen-catalog", null, false));
        settings.save(home);

        assertThrows(SecurityException.class, () -> resolver(TrustPrompt.Decision.DENY, false).resolve("acme/demo"));
        assertEquals(List.of("stranger/jgen-remote-demo"), asked);

        settings.catalogs.get(0).trusted = true;
        settings.save(home);
        asked.clear();
        assertEquals("remote-demo", resolver(TrustPrompt.Decision.DENY, false).resolve("acme/demo").name);
        assertTrue(asked.isEmpty());
    }

    @Test void installedBlueprintsWinOverTheCatalogAndUnknownNamesExplainThemselves() throws Exception {
        officialCatalog("{\"blueprints\":{\"remote-demo\":{\"source\":\"github:ajapros/jgen-remote-demo\"}}}");
        BlueprintConfig local = new BlueprintConfig();
        local.name = "remote-demo";
        local.source = "file:/local/blueprint.json";
        Registry.registerBlueprint(local);
        assertSame(local, resolver(TrustPrompt.Decision.DENY, false).resolve("remote-demo"));
        var error = assertThrows(IllegalArgumentException.class,
                () -> resolver(TrustPrompt.Decision.DENY, false).resolve("nothing-like-this"));
        assertTrue(error.getMessage().contains("--search"));
    }

    @Test void searchMatchesNamesDescriptionsAndTagsAndCatalogsAreCached() throws Exception {
        officialCatalog("""
            {"blueprints":{
              "terraform":{"source":"github:ajapros/jgen-terraform","description":"A Terraform module","tags":["iac"]},
              "docs-site":{"source":"github:ajapros/jgen-docs-site","description":"A documentation site"}}}
            """);
        Catalogs catalogs = resolver(TrustPrompt.Decision.DENY, false).catalogs();
        assertEquals(List.of("terraform"), catalogs.search("IAC").stream().map(Catalogs.Match::name).toList());
        assertEquals(List.of("docs-site"), catalogs.search("documentation").stream().map(Catalogs.Match::name).toList());
        assertEquals(2, catalogs.search("").size());

        int reads = fetcher.catalogReads;
        fetcher.catalogs.clear();   // GitHub unreachable: the cached copy is still used
        assertEquals(2, resolver(TrustPrompt.Decision.DENY, true).catalogs().search("").size());
        assertEquals(2, resolver(TrustPrompt.Decision.DENY, false).catalogs().search(null).size());
        assertEquals(reads, fetcher.catalogReads, "a fresh cached catalog is not fetched again");
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
        final Map<String, byte[]> sources = new java.util.LinkedHashMap<>();
        final Map<String, String> catalogs = new java.util.LinkedHashMap<>();
        int catalogReads;

        @Override public byte[] catalog(String owner, String repo) throws IOException {
            catalogReads++;
            String json = catalogs.get(owner + "/" + repo);
            if (json == null) throw new IOException("no catalog " + owner + "/" + repo);
            return json.getBytes(StandardCharsets.UTF_8);
        }
        int calls, downloads;

        void publish(String version, byte[] jar) { releases.put(version, jar); }
        void publishSource(String version, byte[] zip) { sources.put(version, zip); }
        private boolean templateOnly(BlueprintReference reference) { return reference.repo().startsWith("jgen-tpl"); }

        @Override public Asset sourceArchive(BlueprintReference reference) {
            return new Asset(reference.repo() + "-" + reference.version() + "-source.zip", "src:" + reference.version());
        }

        @Override public String latestVersion(BlueprintReference reference) {
            calls++;
            var versions = templateOnly(reference) ? sources.keySet() : releases.keySet();
            return versions.stream().max(BlueprintResolver::compareVersions).orElse(null);
        }

        @Override public List<Asset> assets(BlueprintReference reference) {
            calls++;
            if (templateOnly(reference)) return List.of();
            return List.of(new Asset(reference.repo() + "-" + reference.version() + ".jar", reference.version()),
                    new Asset(reference.repo() + "-" + reference.version() + "-sources.jar", "sources"));
        }

        @Override public void download(Asset asset, Path target) throws IOException {
            calls++;
            downloads++;
            byte[] bytes = asset.url().startsWith("src:") ? sources.get(asset.url().substring(4)) : releases.get(asset.url());
            try (OutputStream out = Files.newOutputStream(target)) { out.write(bytes); }
        }
    }
}
