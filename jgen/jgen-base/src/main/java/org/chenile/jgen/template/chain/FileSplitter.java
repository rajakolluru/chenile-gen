package org.chenile.jgen.template.chain;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileSplitter extends FileProcessor {
    public static final String FILE_LIST_EXTENSION = ".filelist";
    public static final String START = "--START--";
    public static final String END_FILE = "--END--";

    /**
     *
     * @param file
     * Given a file (sample.filelist)  that has the following format:
     * <code>
     * --START--abc.txt--
     * Contents of abc.txt
     * --END--
     * --START--xyz.txt--
     * Contents of xyz.txt
     * --END--
     * </code>
     * This script breaks the file into abc.txt and xyz.txt and deletes
     * sample.filelist. Both the files are created in the same folder
     * where the sample.filelist file resides.
     */
    protected void doProcess(File file, TemplateContext context)  {
        try {
            if (!file.isFile() || !file.getName().endsWith(FILE_LIST_EXTENSION))
                return;
            String folder = file.getParent();
            FileWriter writer = null;
            LineIterator iterator = FileUtils.lineIterator(file);
            while(iterator.hasNext()){
                String line = iterator.next();
                if (line.trim().startsWith(START)){
                    String filename = folder + File.separator + line.substring(START.length());
                    writer = new FileWriter(filename);
                    continue;
                }
                if (line.trim().startsWith(END_FILE)){
                    if (writer != null) {
                        writer.close();
                        writer = null;
                    }
                    continue;
                }
                if (writer != null){
                    writer.append(line);
                    writer.append("\n");
                }
            }
            boolean ignoreMe = file.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
