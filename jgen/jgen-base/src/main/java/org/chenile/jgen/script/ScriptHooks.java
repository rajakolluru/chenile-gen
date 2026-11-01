package org.chenile.jgen.script;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.provenance.ProvenanceRecorder;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Blueprint hooks written in JavaScript instead of Java, declared in blueprint.json:
 * <pre>
 *   "scripts": { "postInputCapture": "scripts/hooks.js", "postProcess": "scripts/hooks.js" }
 * </pre>
 * The named file must define a function of the hook's name:
 * <pre>
 *   function postInputCapture(inputs, jgen)                         // returns values to add to the inputs
 *   function childBlueprintInputs(inputs, child, attributes, jgen)  // returns the child's inputs
 *   function postProcess(inputs, project, jgen)                     // works on the generated files
 *   function final(inputs, project, jgen)                           // runs last, after the record and Git
 * </pre>
 * Inputs and results are plain JSON values. {@code jgen} is a {@link ScriptApi}; {@code project} is a
 * {@link ScriptProject} confined to the generated folder.
 *
 * <p>Scripts run in GraalJS with no access to Java classes, the file system, the network, processes, threads or the
 * environment, and with a statement limit, so a blueprint with scripts can still be a template-only blueprint.
 * Scripts are compiled when the blueprint loads: a syntax error or a missing function is a blueprint error. Script
 * hooks run after any Java hook of the same kind.</p>
 */
public final class ScriptHooks {
    public static final List<String> HOOKS = List.of("postInputCapture", "childBlueprintInputs", "postProcess", "final");
    /** Statements a single hook call may execute; protects against runaway loops. */
    static final long STATEMENT_LIMIT = 50_000_000L;

    private static final ObjectMapper MAPPER = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static volatile Engine engine;

    private ScriptHooks() { }

    /**
     * Compiles the blueprint's scripts and wires them into its hooks.
     *
     * @param resources finds a resource of the blueprint (its jar, folder or class path) by path
     */
    public static void install(BlueprintConfig blueprint, Function<String, URL> resources) {
        for (Map.Entry<String, String> declared : blueprint.scripts.entrySet()) {
            String hook = declared.getKey();
            if (!HOOKS.contains(hook))
                throw new IllegalArgumentException("Blueprint " + blueprint.name + ": unknown script hook " + hook
                        + "; use one of " + HOOKS);
            Source source = load(blueprint, declared.getValue(), resources);
            verify(blueprint, source, hook);
            wire(blueprint, hook, source);
        }
    }

    private static Source load(BlueprintConfig blueprint, String path, Function<String, URL> resources) {
        URL url = resources.apply(path);
        if (url == null)
            throw new IllegalArgumentException("Blueprint " + blueprint.name + ": script " + path + " not found");
        try (InputStream in = url.openStream()) {
            String code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Source.newBuilder("js", code, path).build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Blueprint " + blueprint.name + ": cannot read script " + path, e);
        }
    }

    private static void verify(BlueprintConfig blueprint, Source source, String hook) {
        try (Context context = context()) {
            context.eval(source);
            Value function = context.getBindings("js").getMember(hook);
            if (function == null || !function.canExecute())
                throw new IllegalArgumentException("Blueprint " + blueprint.name + ": script " + source.getName()
                        + " must define function " + hook + "(...)");
        } catch (PolyglotException e) {
            throw new IllegalArgumentException("Blueprint " + blueprint.name + ": script " + source.getName()
                    + " does not load: " + describe(e), e);
        }
    }

    private static void wire(BlueprintConfig blueprint, String hook, Source source) {
        switch (hook) {
            case "postInputCapture" -> blueprint.postInputCaptureHook = after(blueprint.postInputCaptureHook, map -> {
                Map<String, Object> added = call(blueprint, source, hook, map, false);
                if (added != null) map.putAll(added);
            });
            case "childBlueprintInputs" -> {
                if (blueprint.childBlueprintInputs != null)
                    throw new IllegalArgumentException("Blueprint " + blueprint.name
                            + " defines childBlueprintInputs both in Java and in a script; keep one");
                blueprint.childBlueprintInputs = (inputs, child, attributes) -> {
                    Map<String, Object> result = call(blueprint, source, hook, inputs, false, child, attributes);
                    return result == null ? new LinkedHashMap<>() : result;
                };
            }
            case "postProcess" -> blueprint.postProcessHook = after(blueprint.postProcessHook,
                    map -> call(blueprint, source, hook, map, true));
            case "final" -> blueprint.finalHook = after(blueprint.finalHook,
                    map -> call(blueprint, source, hook, map, true));
            default -> throw new IllegalArgumentException("Unknown script hook " + hook);
        }
    }

    private static Consumer<Map<String, Object>> after(Consumer<Map<String, Object>> java,
                                                        Consumer<Map<String, Object>> script) {
        return java == null ? script : java.andThen(script);
    }

    /**
     * Runs one hook function in a fresh, sandboxed context. Inputs go in as JSON; an object result comes back as a
     * map (null for no result).
     */
    static Map<String, Object> call(BlueprintConfig blueprint, Source source, String hook, Map<String, Object> inputs,
                                    boolean withProject, Object... extra) {
        try (Context context = context()) {
            context.eval(source);
            Value function = context.getBindings("js").getMember(hook);
            Value jsInputs = context.eval("js", "JSON.parse").execute(toJson(inputs));
            ScriptApi api = new ScriptApi(blueprint, inputs);
            Object[] arguments;
            if (withProject) {
                Path root = ProvenanceRecorder.projectDirectory(blueprint, inputs);
                arguments = new Object[]{jsInputs, new ScriptProject(root, blueprint, inputs), api};
            } else if (extra.length == 2) {
                Value attributes = context.eval("js", "JSON.parse").execute(toJson(extra[1]));
                arguments = new Object[]{jsInputs, extra[0], attributes, api};
            } else {
                arguments = new Object[]{jsInputs, api};
            }
            Value result = function.execute(arguments);
            if (result == null || result.isNull()) return null;
            if (!result.hasMembers() || result.hasArrayElements())
                throw new IllegalStateException("Script " + source.getName() + ": " + hook
                        + " must return an object (or nothing)");
            String json = context.eval("js", "(v) => JSON.stringify(v)").execute(result).asString();
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (PolyglotException e) {
            throw new IllegalStateException("Script " + source.getName() + ", " + hook + "(): " + describe(e), e);
        } catch (IOException e) {
            throw new IllegalStateException("Script " + source.getName() + ", " + hook + "(): " + e.getMessage(), e);
        }
    }

    /** A sandbox: exported API methods only, no class lookup, I/O, processes, threads, native code or environment. */
    static Context context() {
        return Context.newBuilder("js")
                .engine(engine())
                .allowHostAccess(HostAccess.newBuilder()
                        .allowAccessAnnotatedBy(HostAccess.Export.class)
                        .allowArrayAccess(true)
                        .build())
                .allowHostClassLookup(className -> false)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .resourceLimits(ResourceLimits.newBuilder().statementLimit(STATEMENT_LIMIT, null).build())
                .build();
    }

    private static Engine engine() {
        Engine current = engine;
        if (current == null) {
            synchronized (ScriptHooks.class) {
                current = engine;
                if (current == null) {
                    // Without a JIT-enabled JVM GraalJS runs interpreted; fine for short hook scripts.
                    current = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
                    engine = current;
                }
            }
        }
        return current;
    }

    /** JSON for a value; map entries that cannot be represented as JSON (Java objects added by hooks) are left out. */
    private static String toJson(Object value) throws IOException {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                try {
                    MAPPER.writeValueAsString(e.getValue());
                    safe.put(String.valueOf(e.getKey()), e.getValue());
                } catch (Exception ignored) {
                    // not JSON-representable; scripts don't see it
                }
            }
            return MAPPER.writeValueAsString(safe);
        }
        return MAPPER.writeValueAsString(value);
    }

    private static String describe(PolyglotException e) {
        if (e.isResourceExhausted()) return "stopped after " + STATEMENT_LIMIT + " statements (a runaway loop?)";
        SourceSection at = e.getSourceLocation();
        return e.getMessage() + (at == null ? "" : " (line " + at.getStartLine() + ")");
    }
}
