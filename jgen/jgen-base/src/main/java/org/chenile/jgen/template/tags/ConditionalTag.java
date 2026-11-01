package org.chenile.jgen.template.tags;

import org.apache.commons.io.FileUtils;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;

/**
 * A folder named {@code %%key=value%%}: when the input {@code key} equals {@code value} its contents move up into
 * the parent folder. The folder itself is always removed.
 */
public class ConditionalTag implements PathTag {
    @Override public String name() { return "conditional"; }
    @Override public int order() { return 400; }
    @Override public boolean matches(String name) { return name.startsWith("%%") && name.endsWith("%%") && name.length() > 4; }

    @Override
    public void process(File file, TemplateContext context) {
        String expr = file.getName().substring(2, file.getName().length() - 2);
        int index = expr.indexOf('=');
        if (index < 0)
            throw new IllegalArgumentException("Conditional folder " + file.getName() + " must look like %%key=value%%");
        Object value = context.map.get(expr.substring(0, index));
        if (value != null && value.toString().equals(expr.substring(index + 1))) moveContentsToParent(file);
        try {
            FileUtils.deleteDirectory(file);
        } catch (Exception ignored) {
            // nothing left to remove
        }
    }

    /**
     * Moves the folder's contents up. A sub-folder that already exists in the parent is merged into it; a file that
     * already exists there is kept and the conditional copy dropped, with a warning (as in earlier versions).
     */
    private void moveContentsToParent(File folder) {
        moveInto(folder, folder.getParentFile());
    }

    private void moveInto(File source, File targetFolder) {
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(targetFolder, child.getName());
            if (target.exists()) {
                if (child.isDirectory() && target.isDirectory()) moveInto(child, target);
                else System.err.println("Warning: " + target + " already exists; the copy from "
                        + source.getName() + " is not used");
                continue;
            }
            if (!child.renameTo(target))
                throw new IllegalStateException("Cannot move " + child + " to " + target);
        }
    }
}
