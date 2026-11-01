package org.chenile.jgen.template.tags;

import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;

/**
 * Turns every {@code _DoT_} in a file or folder name into a dot, so templates can carry dotfiles that Maven and Git
 * would otherwise drop: {@code _DoT_gitignore} becomes {@code .gitignore}, {@code _DoT_github/workflows} becomes
 * {@code .github/workflows}. Runs after placeholders, so both can be combined.
 */
public class DotTag implements PathTag {
    public static final String DOT = "_DoT_";

    @Override public String name() { return "dot"; }
    @Override public int order() { return 300; }
    @Override public boolean matches(String name) { return name.contains(DOT); }

    @Override
    public void process(File file, TemplateContext context) {
        String name = file.getName();
        File target = new File(file.getParentFile(), name.replace(DOT, "."));
        if (target.exists())
            throw new IllegalStateException("Cannot rename " + file + " to " + target.getName() + ": it already exists");
        if (!file.renameTo(target))
            throw new IllegalStateException("Cannot rename " + file + " to " + target.getName());
    }
}
