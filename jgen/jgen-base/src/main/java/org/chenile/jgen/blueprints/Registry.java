package org.chenile.jgen.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All known blueprints, by name. Blueprints are discovered through a class loader: JGen's own class path at
 * start-up ({@link #getAllBlueprints()}), and one jar at a time for blueprints fetched or dropped in later
 * ({@link #loadJar(Path)}). Both go through the same discovery and loading code; only the class loader differs.
 */
public class Registry {
    private static final String DESCRIPTOR = "META-INF/blueprint.json";
    private static final String INDEX = "META-INF/blueprints.list";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static Map<String,BlueprintConfig> blueprints = new HashMap<>();
    static {
        getAllBlueprints();
    }

    public static void registerBlueprint(BlueprintConfig blueprintConfig){
        blueprints.put(blueprintConfig.name,blueprintConfig);
    }

    /**
     * Registers every blueprint on JGen's class path. A blueprint that fails to load is reported and skipped, so one
     * broken blueprint does not take the others down.
     */
    public static void getAllBlueprints() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<URL> descriptors;
        try {
            descriptors = descriptors(classLoader);
        } catch (Exception e) {
            System.err.println("Cannot discover blueprints: " + e.getMessage());
            return;
        }
        for (URL descriptor : descriptors) {
            try {
                registerBlueprint(load(descriptor, classLoader));
            } catch (Exception e) {
                System.err.println("Cannot initialize blueprint " + descriptor + " error = " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Loads the blueprints in one jar through their own class loader (child of JGen's), so a downloaded blueprint
     * cannot clash with JGen's libraries or with other blueprints. Unlike class-path discovery this is strict: any
     * failure fails the whole jar. A blueprint whose name is already registered from somewhere else is refused rather
     * than replaced: local and built-in blueprints always win.
     *
     * @return the blueprints the jar contains
     */
    public static synchronized List<BlueprintConfig> loadJar(Path jar) throws Exception {
        BlueprintJarLoader loader = new BlueprintJarLoader(jar);
        List<URL> descriptors = descriptors(loader);
        if (descriptors.isEmpty()) throw new IOException("No " + DESCRIPTOR + " in " + jar);
        List<BlueprintConfig> loaded = new ArrayList<>();
        for (URL descriptor : descriptors) {
            BlueprintConfig blueprint = load(descriptor, loader);
            BlueprintConfig existing = blueprints.get(blueprint.name);
            if (existing != null && !blueprint.source.equals(existing.source))
                throw new IllegalStateException("Blueprint " + blueprint.name + " from " + jar
                        + " clashes with a blueprint of the same name from " + existing.source);
            loaded.add(blueprint);
        }
        loaded.forEach(Registry::registerBlueprint);
        return loaded;
    }

    /** Every blueprint descriptor visible to the loader: META-INF/blueprint.json files and META-INF/blueprints.list entries. */
    private static List<URL> descriptors(ClassLoader loader) throws IOException {
        List<URL> descriptors = new ArrayList<>(resources(loader, DESCRIPTOR));
        for (URL index : resources(loader, INDEX)) {
            try (var lines = new BufferedReader(new InputStreamReader(index.openStream()))) {
                for (String line; (line = lines.readLine()) != null; ) {
                    String name = line.trim();
                    if (name.isEmpty()) continue;
                    URL descriptor = resource(loader, name);
                    if (descriptor == null) throw new IOException("Missing blueprint resource " + name + " listed in " + index);
                    descriptors.add(descriptor);
                }
            }
        }
        return descriptors;
    }

    /**
     * Reads one descriptor and runs its init hook, resolving the hook class and any classes the hook loads through
     * the given class loader.
     */
    private static BlueprintConfig load(URL descriptor, ClassLoader loader) throws Exception {
        ObjectMapper mapper = objectMapper.copy().setTypeFactory(TypeFactory.defaultInstance().withClassLoader(loader));
        BlueprintConfig blueprint = mapper.readValue(descriptor, BlueprintConfig.class);
        blueprint.source = descriptor.toString();
        blueprint.classLoader = loader;
        if (blueprint.initHook != null) {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);
            try {
                blueprint.initHook.getDeclaredConstructor().newInstance().init(blueprint);
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
        return blueprint;
    }

    /** A blueprint jar's loader sees only its own jar's resources; any other loader sees the whole class path. */
    private static List<URL> resources(ClassLoader loader, String name) throws IOException {
        return Collections.list(loader instanceof BlueprintJarLoader jar ? jar.findResources(name) : loader.getResources(name));
    }

    private static URL resource(ClassLoader loader, String name) {
        return loader instanceof BlueprintJarLoader jar ? jar.findResource(name) : loader.getResource(name);
    }

    /** The class loader of one blueprint jar, a child of JGen's own. */
    static final class BlueprintJarLoader extends URLClassLoader {
        BlueprintJarLoader(Path jar) throws IOException {
            super(new URL[]{jar.toUri().toURL()}, Registry.class.getClassLoader());
        }
    }
}
