package org.chenile.jgen.template.chain;

import org.apache.commons.io.FileUtils;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;

/**
 * Processes a folder of the form %%key=value%%
 * If the key value (in map) is equal to value then all the folders and files under this folder
 * are copied to the parent of this folder. Else the parent is left untouched.<br/>
 * This folder is deleted irrespective of whether it is copied or not.
 */
public class ConditionalProcessor extends FileProcessor {
    @Override
    protected void doProcess(File file, TemplateContext context) {
        String filename = file.getName();
        if (!filename.startsWith("%%")){
            return;
        }
        String expr = filename.substring(2);
        expr = expr.substring(0,expr.length() - 2);
        int index = expr.indexOf("=");
        String lhs = expr.substring(0,index);
        Object lhs_value = context.map.get(lhs);
        String rhs = expr.substring(index+1);
        if (lhs_value != null && lhs_value.toString().equals(rhs)){
            moveContentsToParent(file);
        }
        try {
            FileUtils.deleteDirectory(file);
        }catch(Exception ignoreme){}
    }

    private void moveContentsToParent(File file) {
        for(File f: file.listFiles()){
            File parent = new File(file.getParent() + File.separator + f.getName());
            boolean ignoreMe = f.renameTo(parent);
            if (!ignoreMe){
                System.out.println("Move unsuccessful");
            }
        }
    }

}
