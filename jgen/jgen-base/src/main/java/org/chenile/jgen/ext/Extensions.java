package org.chenile.jgen.ext;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.blueprints.model.ValidationType;
import org.chenile.jgen.template.tags.ConditionalTag;
import org.chenile.jgen.template.tags.DotTag;
import org.chenile.jgen.template.tags.FileListTag;
import org.chenile.jgen.template.tags.PathTag;
import org.chenile.jgen.template.tags.PlaceholderTag;
import org.chenile.jgen.util.NameCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Every JGen extension, built in or added: {@link PathTag path tags}, {@link TemplateEngine template engines} and
 * {@link LanguagePack language packs}. Added ones are found with {@link ServiceLoader} on JGen's class path, which
 * includes the jars in the blueprints folders, so an extension is installed by dropping its jar there.
 *
 * <p>Rules that keep extensions from changing what JGen already does:</p>
 * <ul>
 *   <li>Extension names are unique across all kinds, and the built-in names are taken.</li>
 *   <li>Engine extensions, validation rule names and helper names are unique; a rule cannot reuse a built-in
 *       {@link ValidationType} name.</li>
 * </ul>
 * Blueprints say what they need with {@code "requires": ["pebble", "python@2.1"]}; {@link #checkRequires} refuses a
 * blueprint whose requirements are not installed.
 */
public final class Extensions {
    public static final String TAG = "tag", ENGINE = "engine", PACK = "pack";

    /** One installed extension. */
    public record Entry(String kind, String name, Object implementation, boolean builtIn) {
        /** The extension jar's Implementation-Version, or null. */
        public String version() {
            Package pkg = implementation.getClass().getPackage();
            return pkg == null ? null : pkg.getImplementationVersion();
        }

        /** {@code kind:name@version} (or {@code kind:name}), as written to the generation record. */
        public String describe() {
            String version = version();
            return kind + ":" + name + (version == null ? "" : "@" + version);
        }
    }

    private record Snapshot(List<PathTag> tags, List<TemplateEngine> engines, List<LanguagePack> packs,
                            Map<String, Entry> byName, Map<String, ValidationRule> rules,
                            Map<String, LanguagePack> ruleOwners, Map<String, Function<String, String>> helpers) { }

    private static final List<Object> registered = new ArrayList<>();
    private static volatile Snapshot snapshot;

    private Extensions() { }

    // ---- built-ins ---------------------------------------------------------------------------------------------

    private static List<Object> builtIns() {
        return List.of(new FileListTag(), new PlaceholderTag(), new DotTag(), new ConditionalTag(),
                new MustacheEngine(), new PythonPack(), new NodePack());
    }

    /** Helpers every engine and script gets, before any pack's. */
    private static Map<String, Function<String, String>> coreHelpers() {
        Map<String, Function<String, String>> helpers = new LinkedHashMap<>();
        helpers.put("capitalize", NameCase::capitalize);
        helpers.put("uncapitalize", NameCase::uncapitalize);
        helpers.put("camelCase", NameCase::camelCase);
        helpers.put("pascalCase", NameCase::pascalCase);
        helpers.put("snakeCase", NameCase::snakeCase);
        helpers.put("upperSnakeCase", NameCase::upperSnakeCase);
        helpers.put("kebabCase", NameCase::kebabCase);
        return helpers;
    }

    // ---- queries -----------------------------------------------------------------------------------------------

    /** Path tags in the order they run. */
    public static List<PathTag> tags() { return snapshot().tags(); }

    public static List<TemplateEngine> engines() { return snapshot().engines(); }

    public static List<LanguagePack> packs() { return snapshot().packs(); }

    /** Every installed extension by name. */
    public static Map<String, Entry> all() { return snapshot().byName(); }

    /** A validation rule from a language pack, or null. */
    public static ValidationRule rule(String name) { return snapshot().rules().get(name); }

    /** Template helpers by name: the core ones plus every pack's. */
    public static Map<String, Function<String, String>> helpers() { return snapshot().helpers(); }

    /** The engine for a template file name (the longest matching extension wins), or null for a plain file. */
    public static TemplateEngine engineFor(String fileName) {
        TemplateEngine best = null;
        for (TemplateEngine engine : engines()) {
            if (fileName.endsWith(engine.extension()) && fileName.length() > engine.extension().length()
                    && (best == null || engine.extension().length() > best.extension().length())) best = engine;
        }
        return best;
    }

    public static Entry entry(Object implementation) {
        for (Entry entry : all().values()) if (entry.implementation() == implementation) return entry;
        return null;
    }

    // ---- requirements and records ------------------------------------------------------------------------------

    /**
     * Refuses a blueprint whose {@code requires} are not installed. An entry is {@code name} or
     * {@code name@minimumVersion}; the version is compared with the extension jar's Implementation-Version.
     */
    public static void checkRequires(BlueprintConfig blueprint) {
        checkRequires(blueprint.name, blueprint.requires);
    }

    /** As {@link #checkRequires(BlueprintConfig)}, before the descriptor is fully read. */
    public static void checkRequires(String blueprintName, List<String> requires) {
        if (requires == null) return;
        List<String> problems = new ArrayList<>();
        for (String requirement : requires) {
            String problem = unmet(requirement);
            if (problem != null) problems.add(problem);
        }
        if (!problems.isEmpty())
            throw new IllegalArgumentException("Blueprint " + blueprintName + " needs extensions that are not installed: "
                    + String.join("; ", problems) + ". Add their jars to ~/.jgen/blueprints (or JGEN_BLUEPRINTS).");
    }

    /** Null when the requirement is met, otherwise what is missing. */
    public static String unmet(String requirement) {
        String name = requirement.trim();
        String minimum = null;
        int at = name.indexOf('@');
        if (at >= 0) {
            minimum = name.substring(at + 1).trim();
            name = name.substring(0, at).trim();
        }
        int colon = name.indexOf(':');            // kind:name is accepted too
        if (colon >= 0) name = name.substring(colon + 1);
        Entry entry = all().get(name);
        if (entry == null) return name + " is not installed";
        if (minimum == null || minimum.isEmpty() || entry.builtIn()) return null;
        String version = entry.version();
        if (version == null) return null;          // an unversioned jar (e.g. a local build) is accepted
        if (new ComparableVersion(version).compareTo(new ComparableVersion(minimum)) < 0)
            return name + " " + version + " is older than " + minimum;
        return null;
    }

    /**
     * The added (not built-in) extensions a generation depended on, for the generation record: every added path tag
     * (they always run), the added engines that rendered files, the packs whose rules the blueprint uses, and
     * whatever the blueprint requires.
     */
    public static List<String> usedBy(BlueprintConfig blueprint, Collection<String> enginesUsed) {
        TreeSet<String> used = new TreeSet<>();
        Snapshot current = snapshot();
        for (Entry entry : current.byName().values()) {
            if (entry.builtIn()) continue;
            boolean depends = switch (entry.kind()) {
                case TAG -> true;
                case ENGINE -> enginesUsed != null && enginesUsed.contains(entry.name());
                case PACK -> usesRuleOf(blueprint.inputFields, (LanguagePack) entry.implementation(), current);
                default -> false;
            };
            if (depends) used.add(entry.describe());
        }
        if (blueprint.requires != null) {
            for (String requirement : blueprint.requires) {
                String name = requirement.contains("@") ? requirement.substring(0, requirement.indexOf('@')) : requirement;
                if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
                Entry entry = current.byName().get(name.trim());
                if (entry != null && !entry.builtIn()) used.add(entry.describe());
            }
        }
        return new ArrayList<>(used);
    }

    private static boolean usesRuleOf(List<InputField> fields, LanguagePack pack, Snapshot current) {
        if (fields == null) return false;
        for (InputField field : fields) {
            if (field.validation == ValidationType.EXTENSION && current.ruleOwners().get(field.rule) == pack) return true;
            if (usesRuleOf(field.childFields, pack, current)) return true;
        }
        return false;
    }

    /** The name in a recorded {@code kind:name@version}. */
    public static String recordedName(String recorded) {
        String name = recorded;
        if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        if (name.contains("@")) name = name.substring(0, name.indexOf('@'));
        if (name.contains(" ")) name = name.substring(0, name.indexOf(' '));
        return name;
    }

    // ---- registration ------------------------------------------------------------------------------------------

    /** Adds an extension programmatically (tests, embedding applications). */
    public static synchronized void register(Object extension) {
        if (!(extension instanceof PathTag || extension instanceof TemplateEngine || extension instanceof LanguagePack))
            throw new IllegalArgumentException(extension.getClass().getName()
                    + " is not a PathTag, TemplateEngine or LanguagePack");
        registered.add(extension);
        snapshot = null;
    }

    /** Forgets registered extensions and discovers again on next use. */
    public static synchronized void reset() {
        registered.clear();
        snapshot = null;
    }

    private static Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            synchronized (Extensions.class) {
                current = snapshot;
                if (current == null) {
                    current = discover();
                    snapshot = current;
                }
            }
        }
        return current;
    }

    private static Snapshot discover() {
        List<Object> builtIns = builtIns();
        List<Object> found = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = Extensions.class.getClassLoader();
        load(PathTag.class, loader, found, builtIns);
        load(TemplateEngine.class, loader, found, builtIns);
        load(LanguagePack.class, loader, found, builtIns);
        found.addAll(registered);

        Map<String, Entry> byName = new LinkedHashMap<>();
        List<PathTag> tags = new ArrayList<>();
        List<TemplateEngine> engines = new ArrayList<>();
        List<LanguagePack> packs = new ArrayList<>();
        List<Object> everything = new ArrayList<>(builtIns);
        everything.addAll(found);
        for (Object extension : everything) {
            boolean builtIn = builtIns.contains(extension);
            String kind;
            String name;
            if (extension instanceof PathTag tag) { kind = TAG; name = tag.name(); tags.add(tag); }
            else if (extension instanceof TemplateEngine engine) { kind = ENGINE; name = engine.name(); engines.add(engine); }
            else { LanguagePack pack = (LanguagePack) extension; kind = PACK; name = pack.name(); packs.add(pack); }
            if (name == null || name.isBlank())
                throw new IllegalStateException("Extension " + extension.getClass().getName() + " has no name");
            Entry clash = byName.putIfAbsent(name, new Entry(kind, name, extension, builtIn));
            if (clash != null)
                throw new IllegalStateException("Two extensions are named " + name + ": "
                        + clash.implementation().getClass().getName() + " (" + clash.kind() + ") and "
                        + extension.getClass().getName() + " (" + kind + ")");
        }
        tags.sort(Comparator.comparingInt(PathTag::order).thenComparing(PathTag::name));

        Map<String, TemplateEngine> byExtension = new HashMap<>();
        for (TemplateEngine engine : engines) {
            String extension = engine.extension();
            if (extension == null || !extension.startsWith(".") || extension.length() < 2)
                throw new IllegalStateException("Template engine " + engine.name() + " has an invalid extension "
                        + extension + "; it must look like .peb");
            TemplateEngine clash = byExtension.putIfAbsent(extension, engine);
            if (clash != null)
                throw new IllegalStateException("Template engines " + clash.name() + " and " + engine.name()
                        + " both claim " + extension);
        }

        Map<String, ValidationRule> rules = new LinkedHashMap<>();
        Map<String, LanguagePack> ruleOwners = new HashMap<>();
        Map<String, Function<String, String>> helpers = coreHelpers();
        for (LanguagePack pack : packs) {
            for (ValidationRule rule : pack.rules()) {
                if (isBuiltInRule(rule.name()))
                    throw new IllegalStateException("Language pack " + pack.name() + " defines rule " + rule.name()
                            + ", which is a built-in rule");
                if (rules.putIfAbsent(rule.name(), rule) != null)
                    throw new IllegalStateException("Validation rule " + rule.name() + " is defined by "
                            + ruleOwners.get(rule.name()).name() + " and " + pack.name());
                ruleOwners.put(rule.name(), pack);
            }
            for (Map.Entry<String, Function<String, String>> helper : pack.helpers().entrySet()) {
                if (helpers.putIfAbsent(helper.getKey(), helper.getValue()) != null)
                    throw new IllegalStateException("Language pack " + pack.name() + " defines helper "
                            + helper.getKey() + ", which already exists");
            }
        }
        return new Snapshot(List.copyOf(tags), List.copyOf(engines), List.copyOf(packs),
                java.util.Collections.unmodifiableMap(byName), rules, ruleOwners,
                java.util.Collections.unmodifiableMap(helpers));
    }

    private static boolean isBuiltInRule(String name) {
        for (ValidationType type : ValidationType.values()) if (type.name().equals(name)) return true;
        return false;
    }

    private static <T> void load(Class<T> type, ClassLoader loader, List<Object> found, List<Object> builtIns) {
        try {
            for (T extension : ServiceLoader.load(type, loader)) {
                boolean duplicateOfBuiltIn = builtIns.stream().anyMatch(b -> b.getClass() == extension.getClass());
                boolean alreadyFound = found.stream().anyMatch(f -> f.getClass() == extension.getClass());
                if (!duplicateOfBuiltIn && !alreadyFound) found.add(extension);
            }
        } catch (ServiceConfigurationError e) {
            throw new IllegalStateException("Cannot load a JGen " + type.getSimpleName() + " extension: "
                    + e.getMessage(), e);
        }
    }
}
