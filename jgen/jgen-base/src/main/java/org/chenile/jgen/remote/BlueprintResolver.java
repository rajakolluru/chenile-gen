package org.chenile.jgen.remote;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.Registry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds a blueprint by name or by reference. Installed blueprints always win; a {@code github:} reference is
 * fetched from the repository's release, verified, cached under {@code ~/.jgen/cache} and loaded through its own
 * class loader.
 *
 * <p>Trust: a blueprint jar contains Java hooks that run with the user's permissions. Sources must be trusted
 * (see {@link JgenSettings}) or approved through the {@link TrustPrompt}. The SHA-256 of each fetched version is
 * recorded on first use and checked on every later use; a {@code #sha256:} pin in the reference is checked too.</p>
 */
public class BlueprintResolver {
    private final Path home;
    private final BlueprintFetcher fetcher;
    private final TrustPrompt prompt;
    private final boolean offline;
    private final boolean trustAll;
    private final Map<String, List<BlueprintConfig>> loaded = new HashMap<>();

    public BlueprintResolver(Path home, BlueprintFetcher fetcher, TrustPrompt prompt, boolean offline, boolean trustAll) {
        this.home = home;
        this.fetcher = fetcher;
        this.prompt = prompt;
        this.offline = offline;
        this.trustAll = trustAll;
    }

    /** ~/.jgen, GitHub, the given prompt. */
    public static BlueprintResolver defaults(TrustPrompt prompt, boolean offline, boolean trustAll) {
        return new BlueprintResolver(JgenSettings.home(), new GitHubFetcher(), prompt, offline, trustAll);
    }

    public BlueprintConfig resolve(String nameOrReference) throws IOException {
        if (!BlueprintReference.isReference(nameOrReference)) {
            BlueprintConfig local = Registry.blueprints.get(nameOrReference);
            if (local != null) return local;
            throw new IllegalArgumentException("Unknown blueprint " + nameOrReference + ". Installed: "
                    + Registry.blueprints.keySet().stream().sorted().toList()
                    + ". To fetch one from GitHub, use github:owner/repo[@version].");
        }
        return resolve(BlueprintReference.parse(nameOrReference));
    }

    public BlueprintConfig resolve(BlueprintReference requested) throws IOException {
        checkTrust(requested);
        BlueprintReference reference = requested.version() != null ? requested
                : requested.withVersion(offline ? newestCached(requested) : fetcher.latestVersion(requested));
        if (reference.version() == null) throw new IOException("No release found for " + requested);

        Path jar = cachedOrDownloaded(reference);
        String sha256 = sha256(jar);
        if (reference.sha256() != null && !reference.sha256().equals(sha256))
            throw new SecurityException("SHA-256 mismatch for " + reference + ": expected " + reference.sha256()
                    + ", got " + sha256 + " (" + jar + ")");
        String key = "github:" + reference.repository() + "@" + reference.version();
        Map<String, String> lock = JgenSettings.loadLock(home);
        String known = lock.get(key);
        if (known != null && !known.equals(sha256))
            throw new SecurityException(key + " has changed since it was first used (SHA-256 " + known + ", now "
                    + sha256 + "). The release asset was replaced; refusing to run it. Remove the entry from "
                    + home.resolve("blueprints.lock.json") + " only if you trust the new jar.");
        if (known == null) {
            lock.put(key, sha256);
            JgenSettings.saveLock(home, lock);
        }

        List<BlueprintConfig> blueprints = loaded.get(key);
        if (blueprints == null) {
            try {
                blueprints = Registry.loadJar(jar);
            } catch (IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Cannot load blueprint jar " + jar + ": " + e.getMessage(), e);
            }
            for (BlueprintConfig blueprint : blueprints) {
                blueprint.fetchedFrom = new BlueprintReference(reference.owner(), reference.repo(),
                        blueprints.size() > 1 ? blueprint.name : null, reference.version(), null).toString();
                blueprint.sha256 = sha256;
            }
            loaded.put(key, blueprints);
        }
        return pick(reference, blueprints);
    }

    private void checkTrust(BlueprintReference reference) throws IOException {
        if (trustAll) return;
        JgenSettings settings = JgenSettings.load(home);
        if (settings.trusts(reference)) return;
        switch (prompt.ask(reference)) {
            case ALLOW_ONCE -> { }
            case ALLOW_ALWAYS -> {
                settings.trust(reference);
                settings.save(home);
            }
            case DENY -> throw new SecurityException("github:" + reference.repository() + " is not a trusted blueprint "
                    + "source. Blueprints run Java code on your machine. Add it to trustedSources in "
                    + home.resolve("settings.json") + ", or pass --trust for this run.");
        }
    }

    private Path cachedOrDownloaded(BlueprintReference reference) throws IOException {
        Path dir = cacheDir(reference).resolve(reference.version());
        Path cached = singleJar(dir);
        if (cached != null) return cached;
        if (offline) throw new IOException("Offline, and " + reference + " is not in the cache " + dir);
        BlueprintFetcher.Asset asset = chooseAsset(reference, fetcher.assets(reference));
        if (asset.name().contains("/") || asset.name().contains("\\") || asset.name().startsWith("."))
            throw new IOException("Unsafe asset name " + asset.name());
        Files.createDirectories(dir);
        Path target = dir.resolve(asset.name());
        fetcher.download(asset, target);
        return target;
    }

    static BlueprintFetcher.Asset chooseAsset(BlueprintReference reference, List<BlueprintFetcher.Asset> assets) throws IOException {
        List<BlueprintFetcher.Asset> jars = assets.stream()
                .filter(a -> a.name().endsWith(".jar"))
                .filter(a -> !a.name().endsWith("-sources.jar") && !a.name().endsWith("-javadoc.jar")
                        && !a.name().endsWith("-tests.jar"))
                .toList();
        if (jars.size() == 1) return jars.get(0);
        List<BlueprintFetcher.Asset> named = jars.stream().filter(a -> a.name().startsWith(reference.repo())).toList();
        if (named.size() == 1) return named.get(0);
        throw new IOException("Release " + reference + " must have exactly one blueprint jar asset (or one named "
                + reference.repo() + "-*.jar); found " + jars.stream().map(BlueprintFetcher.Asset::name).toList());
    }

    private static BlueprintConfig pick(BlueprintReference reference, List<BlueprintConfig> blueprints) {
        if (reference.blueprintName() != null) {
            return blueprints.stream().filter(b -> reference.blueprintName().equals(b.name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(reference + " contains no blueprint named "
                            + reference.blueprintName() + "; it has " + names(blueprints)));
        }
        if (blueprints.size() == 1) return blueprints.get(0);
        throw new IllegalArgumentException(reference + " contains several blueprints " + names(blueprints)
                + "; name one, e.g. github:" + reference.repository() + "/" + blueprints.get(0).name
                + "@" + reference.version());
    }

    private static List<String> names(List<BlueprintConfig> blueprints) {
        return blueprints.stream().map(b -> b.name).sorted().toList();
    }

    private Path cacheDir(BlueprintReference reference) {
        return home.resolve("cache").resolve("github").resolve(reference.owner()).resolve(reference.repo());
    }

    private String newestCached(BlueprintReference reference) throws IOException {
        Path dir = cacheDir(reference);
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> versions = Files.list(dir)) {
            return versions.filter(v -> {
                        try { return singleJar(v) != null; } catch (IOException e) { return false; }
                    })
                    .map(v -> v.getFileName().toString())
                    .max(BlueprintResolver::compareVersions).orElse(null);
        }
    }

    private static Path singleJar(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jars = files.filter(f -> f.getFileName().toString().endsWith(".jar")).toList();
            return jars.size() == 1 ? jars.get(0) : null;
        }
    }

    /** Compares numeric parts numerically (2.10.0 > 2.9.1); a leading 'v' is ignored. */
    static int compareVersions(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("[^0-9A-Za-z]+");
        String[] y = b.replaceFirst("^[vV]", "").split("[^0-9A-Za-z]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "", q = i < y.length ? y[i] : "";
            int c = p.matches("\\d+") && q.matches("\\d+")
                    ? Long.compare(Long.parseLong(p), Long.parseLong(q))
                    : Comparator.<String>naturalOrder().compare(p, q);
            if (c != 0) return c;
        }
        return 0;
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
