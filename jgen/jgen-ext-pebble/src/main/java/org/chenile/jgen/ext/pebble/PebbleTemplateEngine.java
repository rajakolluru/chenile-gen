package org.chenile.jgen.ext.pebble;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.chenile.jgen.ext.Extensions;
import org.chenile.jgen.ext.TemplateEngine;

import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Renders {@code .peb} templates with <a href="https://pebbletemplates.io">Pebble</a> (Twig/Jinja-style syntax:
 * {@code {{ entity | snakeCase }}}, {@code {% for f in fields %}…{% endfor %}}, {@code {% if jpa %}…{% endif %}}).
 * Every JGen template helper is a Pebble filter. Output is not HTML-escaped, and a missing variable renders as
 * empty, as in Mustache. Templates are self-contained: {@code include} and {@code extends} are not supported.
 */
public class PebbleTemplateEngine implements TemplateEngine {
    private volatile PebbleEngine engine;

    @Override public String name() { return "pebble"; }
    @Override public String extension() { return ".peb"; }

    @Override
    public void render(Reader template, String templateName, Map<String, Object> data, Writer out) throws Exception {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[8192];
        for (int n; (n = template.read(buffer)) > 0; ) text.append(buffer, 0, n);
        PebbleTemplate compiled = engine().getLiteralTemplate(text.toString());
        compiled.evaluate(out, new HashMap<>(data));
        out.flush();
    }

    private PebbleEngine engine() {
        PebbleEngine current = engine;
        if (current == null) {
            synchronized (this) {
                current = engine;
                if (current == null) {
                    current = new PebbleEngine.Builder()
                            .autoEscaping(false)
                            .strictVariables(false)
                            .newLineTrimming(false)
                            .cacheActive(false)
                            .extension(new HelperFilters(Extensions.helpers()))
                            .build();
                    engine = current;
                }
            }
        }
        return current;
    }

    /** JGen's template helpers as Pebble filters. */
    static final class HelperFilters extends AbstractExtension {
        private final Map<String, Filter> filters = new HashMap<>();

        HelperFilters(Map<String, Function<String, String>> helpers) {
            helpers.forEach((name, helper) -> filters.put(name, new Filter() {
                @Override public List<String> getArgumentNames() { return null; }
                @Override public Object apply(Object input, Map<String, Object> args, PebbleTemplate self,
                                              EvaluationContext context, int lineNumber) {
                    return input == null ? null : helper.apply(String.valueOf(input));
                }
            }));
        }

        @Override
        public Map<String, Filter> getFilters() {
            return filters;
        }
    }
}
