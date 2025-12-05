package org.chenile.jgen.template.chain;

import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;
import java.util.Map;

/**
 * Replaces strings of the form __xxx__ in the filename to the value of "xxx" in the template map.
 *  In every file under the output folder, look for files/folders whose names
 *  are of the form __XXX__ where XXX is any key found in the map that is passed to this method.<br/>
 *  Substitute __XXX__ with the value from the map. <br/>
 *  For example, if a file called __XXX__Foo.java is a file and if the value of XXX in map is "Abc"
 *  then the file would be renamed to AbcFoo.java
 */
public class FileNameReplacer extends FileProcessor {
    @Override
    protected void doProcess(File file, TemplateContext context) {
        Map<String, Object> map = context.map;
        StringSubstitutor substitutor = new StringSubstitutor(map,"__","__");
        String folder = file.getParent();
        String filename = file.getName();
        String newName = substitutor.replace(filename);
        if (!newName.equals(filename)) {
            String destination = folder + File.separator + newName;
            boolean ignore = file.renameTo(new File(destination));
        }
    }
}
