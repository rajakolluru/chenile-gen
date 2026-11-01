package org.chenile.jgen.template.tags;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;
import java.io.FileWriter;

/**
 * Splits a rendered {@code name.filelist} into the files it lists and deletes it:
 * <pre>
 * --START--abc.txt
 * Contents of abc.txt
 * --END--
 * </pre>
 * The files are created next to the .filelist.
 */
public class FileListTag implements PathTag {
    public static final String FILE_LIST_EXTENSION = ".filelist";
    public static final String START = "--START--";
    public static final String END_FILE = "--END--";

    @Override public String name() { return "filelist"; }
    @Override public int order() { return 100; }
    @Override public boolean matches(String name) { return name.endsWith(FILE_LIST_EXTENSION); }

    @Override
    public void process(File file, TemplateContext context) throws Exception {
        if (!file.isFile()) return;
        String folder = file.getParent();
        FileWriter writer = null;
        LineIterator iterator = FileUtils.lineIterator(file);   // commons-io 2.4: not Closeable
        try {
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.trim().startsWith(START)) {
                    if (writer != null) writer.close();
                    writer = new FileWriter(folder + File.separator + line.substring(START.length()));
                    continue;
                }
                if (line.trim().startsWith(END_FILE)) {
                    if (writer != null) {
                        writer.close();
                        writer = null;
                    }
                    continue;
                }
                if (writer != null) {
                    writer.append(line);
                    writer.append("\n");
                }
            }
        } finally {
            LineIterator.closeQuietly(iterator);
            if (writer != null) writer.close();
        }
        boolean ignored = file.delete();
    }
}
