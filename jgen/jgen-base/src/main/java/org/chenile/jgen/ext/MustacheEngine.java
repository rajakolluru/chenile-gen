package org.chenile.jgen.ext;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.Reader;
import java.io.Writer;
import java.util.Map;

/**
 * The built-in engine for {@code .mustache} templates. Template helpers reach Mustache as lambdas in the data map
 * ({@code {{#snakeCase}}{{entity}}{{/snakeCase}}}), put there by the lambda-loader step.
 */
public class MustacheEngine implements TemplateEngine {
    public static final String EXTENSION = ".mustache";
    private final MustacheFactory factory = new DefaultMustacheFactory();

    @Override public String name() { return "mustache"; }
    @Override public String extension() { return EXTENSION; }

    @Override
    public void render(Reader template, String templateName, Map<String, Object> data, Writer out) throws Exception {
        Mustache mustache = factory.compile(template, templateName);
        mustache.execute(out, data).flush();
    }
}
