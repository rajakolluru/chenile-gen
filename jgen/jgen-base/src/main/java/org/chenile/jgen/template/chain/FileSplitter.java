package org.chenile.jgen.template.chain;

import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.jgen.template.tags.FileListTag;

import java.io.File;

/** @deprecated the chain now runs {@link FileListTag} through {@link PathTagProcessor}; kept for old chains. */
@Deprecated
public class FileSplitter extends FileProcessor {
    public static final String FILE_LIST_EXTENSION = FileListTag.FILE_LIST_EXTENSION;
    public static final String START = FileListTag.START;
    public static final String END_FILE = FileListTag.END_FILE;
    private final FileListTag tag = new FileListTag();

    @Override
    protected void doProcess(File file, TemplateContext context) {
        TagDelegate.apply(tag, file, context);
    }
}
