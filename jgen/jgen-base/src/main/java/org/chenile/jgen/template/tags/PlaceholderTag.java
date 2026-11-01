package org.chenile.jgen.template.tags;

import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;

/**
 * Replaces {@code __key__} in a file or folder name with the value of {@code key} in the inputs:
 * {@code __entity__Service.java} with entity = Order becomes {@code OrderService.java}.
 */
public class PlaceholderTag implements PathTag {
    @Override public String name() { return "placeholder"; }
    @Override public int order() { return 200; }
    @Override public boolean matches(String name) { return name.contains("__"); }

    @Override
    public void process(File file, TemplateContext context) {
        String name = file.getName();
        String newName = new StringSubstitutor(context.map, "__", "__").replace(name);
        if (!newName.equals(name)) {
            boolean ignored = file.renameTo(new File(file.getParentFile(), newName));
        }
    }
}
