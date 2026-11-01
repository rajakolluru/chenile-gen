package org.chenile.jgen.template.chain;

import org.chenile.jgen.blueprints.BlueprintMarker;
import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;

/**
 * Turns every {@code _DoT_} in a file or folder name into a dot, so templates can carry dotfiles and dot-folders
 * that Maven and Git would otherwise drop or treat specially: {@code _DoT_gitignore} becomes {@code .gitignore},
 * {@code _DoT_github/workflows} becomes {@code .github/workflows}, {@code app_DoT_properties} becomes
 * {@code app.properties}. Runs after path placeholders are replaced, so both can be combined.
 */
public class DotReplacer extends FileProcessor {
    public static final String DOT = "_DoT_";

    @Override
    protected void doProcess(File file, TemplateContext context) {
        String name = file.getName();
        if (!name.contains(DOT) || BlueprintMarker.isMarker(name)) return;
        File target = new File(file.getParentFile(), name.replace(DOT, "."));
        if (target.exists())
            throw new IllegalStateException("Cannot rename " + file + " to " + target.getName() + ": it already exists");
        if (!file.renameTo(target))
            throw new IllegalStateException("Cannot rename " + file + " to " + target.getName());
    }
}
