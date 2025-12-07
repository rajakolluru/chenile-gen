package org.chenile.jgen.template.chain;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.apache.commons.io.FileUtils;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Looks for Mustache files. Treats them as templates and passes the map to mustache for execution.
 * The mustache suffix is dropped from the processed file.
 */
public class FileTemplateExecutor extends FileProcessor {
    public static final String MUSTACHE_EXTENSION = ".mustache";
    MustacheFactory mf = new DefaultMustacheFactory();

    @Override
    protected void doProcess(File file, TemplateContext context) {
        String n = file.getPath();
        if(!n.endsWith(MUSTACHE_EXTENSION)) return;
        String out = n.substring(0,n.length() - MUSTACHE_EXTENSION.length());
        try (java.io.FileReader fileReader = new FileReader(n)) {
            Mustache m = mf.compile(fileReader,n);
            FileWriter fileWriter = new FileWriter(out);
            m.execute(fileWriter,context.map).flush();
            fileWriter.close();
            FileUtils.deleteQuietly(new File(n));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
