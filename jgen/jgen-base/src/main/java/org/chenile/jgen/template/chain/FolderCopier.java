package org.chenile.jgen.template.chain;

import org.apache.commons.io.FileUtils;
import org.chenile.jgen.util.CopyFromJar;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

import java.io.File;
import java.nio.file.Paths;

/**
 * Copies input folder to output folder
 */
public class FolderCopier implements Command<TemplateContext> {
    CopyFromJar copyFromJar = new CopyFromJar();
    @Override
    public void execute(TemplateContext context) throws Exception {
        if(context.isJar)
            copyFromJar.copyFromJar(context.blueprintConfig.templateFolder, Paths.get(context.getOutput()));
        else
            copyFolder(new File(context.blueprintConfig.templateFolder),new File(context.getOutput()));
        context.destination = new File(context.getOutput());
    }

    private void copyFolder(File input, File output) throws Exception{
        FileUtils.copyDirectory(input, output);
    }
}
