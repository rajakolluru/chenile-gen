package org.chenile.jgen.remote;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The catalogs JGen searches for short blueprint names: the official catalog first (unless switched off), then the
 * ones in {@code ~/.jgen/settings.json}, in order. Each catalog is cached under {@code ~/.jgen/cache/catalogs} and
 * refreshed after an hour; offline, or when GitHub cannot be reached, the cached copy is used.
 */
public class Catalogs {
    public static final String OFFICIAL_SOURCE = "github:ajapros/jgen-catalog";
    static final Duration REFRESH_AFTER = Duration.ofHours(1);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** A loaded catalog and how it is configured. */
    public record Loaded(String alias, String source, boolean trusted, Catalog catalog) { }

    /** A catalog entry found by name. */
    public record Match(Loaded catalog, String name, Catalog.Entry entry) { }

    private final Path home;
    private final BlueprintFetcher fetcher;
    private final boolean offline;
    private List<Loaded> loaded;

    public Catalogs(Path home, BlueprintFetcher fetcher, boolean offline) {
        this.home = home;
        this.fetcher = fetcher;
        this.offline = offline;
    }

    /** The configured catalogs, in search order, loaded from the cache or GitHub. Unreadable catalogs are skipped. */
    public List<Loaded> all() throws IOException {
        if (loaded == null) loaded = load(false);
        return loaded;
    }

    /** Catalogs as cached, without touching the network (for shell completion). */
    public List<Loaded> cachedOnly() throws IOException {
        return load(true);
    }

    /** Finds a name in one catalog (by alias) or in all of them, in order. */
    public Optional<Match> find(String alias, String name) throws IOException {
        for (Loaded catalog : all()) {
            if (alias != null && !alias.equals(catalog.alias())) continue;
            Catalog.Entry entry = catalog.catalog().blueprints.get(name);
            if (entry != null) return Optional.of(new Match(catalog, name, entry));
        }
        return Optional.empty();
    }

    /** Entries whose name, description or tags contain the text (case-insensitive); all entries for blank text. */
    public List<Match> search(String text) throws IOException {
        String needle = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        List<Match> matches = new ArrayList<>();
        for (Loaded catalog : all()) {
            for (Map.Entry<String, Catalog.Entry> e : catalog.catalog().blueprints.entrySet()) {
                Catalog.Entry entry = e.getValue();
                String haystack = (e.getKey() + " " + nullToEmpty(entry.description) + " "
                        + String.join(" ", entry.tags)).toLowerCase(Locale.ROOT);
                if (needle.isEmpty() || haystack.contains(needle)) matches.add(new Match(catalog, e.getKey(), entry));
            }
        }
        return matches;
    }

    private List<Loaded> load(boolean cacheOnly) throws IOException {
        JgenSettings settings = JgenSettings.load(home);
        List<JgenSettings.CatalogSource> sources = new ArrayList<>();
        if (settings.officialCatalog) sources.add(new JgenSettings.CatalogSource(OFFICIAL_SOURCE, null, true));
        if (settings.catalogs != null) sources.addAll(settings.catalogs);
        List<Loaded> result = new ArrayList<>();
        for (JgenSettings.CatalogSource source : sources) {
            BlueprintReference repo;
            try {
                repo = BlueprintReference.parse(source.source);
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: ignoring catalog " + source.source + ": " + e.getMessage());
                continue;
            }
            String alias = source.alias == null || source.alias.isBlank() ? repo.owner() : source.alias;
            Catalog catalog = read(repo, cacheOnly);
            if (catalog != null) result.add(new Loaded(alias, "github:" + repo.repository(), source.trusted, catalog));
        }
        return result;
    }

    private Catalog read(BlueprintReference repo, boolean cacheOnly) {
        Path cached = home.resolve("cache").resolve("catalogs").resolve(repo.owner()).resolve(repo.repo())
                .resolve("catalog.json");
        boolean fresh = Files.isRegularFile(cached) && isFresh(cached);
        if (!fresh && !offline && !cacheOnly) {
            try {
                byte[] bytes = fetcher.catalog(repo.owner(), repo.repo());
                Catalog parsed = parse(bytes, repo);
                Files.createDirectories(cached.getParent());
                Path partial = cached.resolveSibling("catalog.json.part");
                Files.write(partial, bytes);
                Files.move(partial, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return parsed;
            } catch (IOException | RuntimeException e) {
                if (!Files.isRegularFile(cached)) {
                    System.err.println("Warning: catalog github:" + repo.repository() + " is unavailable: " + e.getMessage());
                    return null;
                }
                System.err.println("Warning: using the cached copy of catalog github:" + repo.repository()
                        + " (" + e.getMessage() + ")");
            }
        }
        if (!Files.isRegularFile(cached)) return null;
        try {
            return parse(Files.readAllBytes(cached), repo);
        } catch (IOException | RuntimeException e) {
            System.err.println("Warning: ignoring cached catalog github:" + repo.repository() + ": " + e.getMessage());
            return null;
        }
    }

    static Catalog parse(byte[] bytes, BlueprintReference repo) throws IOException {
        Catalog catalog = MAPPER.readValue(bytes, Catalog.class);
        if (catalog.schema != null && !Catalog.SCHEMA.equals(catalog.schema))
            throw new IOException("unsupported catalog schema " + catalog.schema);
        if (catalog.blueprints == null) catalog.blueprints = new java.util.LinkedHashMap<>();
        catalog.blueprints.entrySet().removeIf(e -> {
            Catalog.Entry entry = e.getValue();
            boolean valid = entry != null && entry.source != null && BlueprintReference.isReference(entry.source);
            if (valid) {
                try { BlueprintReference.parse(entry.source); } catch (IllegalArgumentException ex) { valid = false; }
            }
            if (!valid) System.err.println("Warning: catalog github:" + repo.repository() + " entry " + e.getKey()
                    + " has no valid github: source; ignored");
            else {
                if (entry.tags == null) entry.tags = new ArrayList<>();
                if (entry.versions == null) entry.versions = new java.util.LinkedHashMap<>();
            }
            return !valid;
        });
        return catalog;
    }

    private static boolean isFresh(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isAfter(Instant.now().minus(REFRESH_AFTER));
        } catch (IOException e) {
            return false;
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
