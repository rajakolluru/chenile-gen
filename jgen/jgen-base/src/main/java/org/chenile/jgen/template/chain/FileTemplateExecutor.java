package org.chenile.jgen.template.chain;

import org.apache.commons.io.FileUtils;
import org.chenile.jgen.ext.Extensions;
import org.chenile.jgen.ext.TemplateEngine;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Renders every template file with the engine its extension selects ({@code .mustache} built in; others from
 * extension jars) and drops the extension: {@code Foo.java.mustache} becomes {@code Foo.java}. Generated shell
 * scripts are made executable.
 */
public class FileTemplateExecutor extends FileProcessor {
    /** @deprecated use {@link org.chenile.jgen.ext.MustacheEngine#EXTENSION}. */
    @Deprecated
    public static final String MUSTACHE_EXTENSION = org.chenile.jgen.ext.MustacheEngine.EXTENSION;

    @Override
    protected void doProcess(File file, TemplateContext context) {
        if (!file.isFile()) return;
        TemplateEngine engine = Extensions.engineFor(file.getName());
        if (engine == null) return;
        String n = file.getPath();
        String out = n.substring(0, n.length() - engine.extension().length());
        try {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
                 Writer writer = Files.newBufferedWriter(new File(out).toPath(), StandardCharsets.UTF_8)) {
                engine.render(reader, n, context.map, writer);
            }
            if (out.endsWith(".sh") && !new File(out).setExecutable(true, false)) {
                throw new IOException("Unable to make generated shell script executable: " + out);
            }
            FileUtils.deleteQuietly(file);
            context.enginesUsed.add(engine.name());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot render " + n + " with " + engine.name() + ": " + e.getMessage(), e);
        }
    }
}
