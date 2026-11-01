package org.chenile.jgen.ext;

import java.io.Reader;
import java.io.Writer;
import java.util.Map;

/**
 * A template language. The render step picks the engine by file extension: {@code Foo.java.mustache} is rendered by
 * the engine whose {@link #extension()} is ".mustache" into {@code Foo.java}. Mustache is built in; others are
 * added by extension jars listing their engines in {@code META-INF/services/org.chenile.jgen.ext.TemplateEngine}.
 *
 * <p>Engines should make the template helpers ({@link Extensions#helpers()}) available in their own idiom, e.g.
 * as filters, and must not escape output for HTML: JGen generates code and configuration.</p>
 */
public interface TemplateEngine {
    /** A unique name, e.g. "pebble". Blueprints use it in {@code requires}. */
    String name();

    /** The template file extension, with its dot, e.g. ".peb". Must be unique among engines. */
    String extension();

    /**
     * Renders one template.
     *
     * @param template     the template text
     * @param templateName the template's path, for error messages
     * @param data         the inputs (and derived values) of the generation
     */
    void render(Reader template, String templateName, Map<String, Object> data, Writer out) throws Exception;
}
