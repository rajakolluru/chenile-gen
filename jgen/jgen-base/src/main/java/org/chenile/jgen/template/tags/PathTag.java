package org.chenile.jgen.template.tags;

import org.chenile.jgen.template.model.TemplateContext;

import java.io.File;

/**
 * A tag in the name of a generated file or folder, such as {@code __key__}, {@code _DoT_} or {@code %%key=value%%}.
 * After the templates are rendered, the {@code path-tag-processor} stage walks the generated tree once per tag, in
 * {@link #order()}, and calls {@link #process} for every file or folder whose name {@link #matches}.
 *
 * <p>JGen's own tags are built in (see {@link PathTags}). More can be added without changing JGen: put a jar on
 * JGen's class path (for instance in {@code ~/.jgen/blueprints}) that lists its implementations in
 * {@code META-INF/services/org.chenile.jgen.template.tags.PathTag}.</p>
 *
 * <p>Tags never see child-blueprint markers ({@code %{{...}}}); those belong to the blueprint expander.</p>
 */
public interface PathTag {
    /** A unique, short name, e.g. "dot". Shown in errors and recorded in the generation record. */
    String name();

    /**
     * Where the tag runs relative to the others; lower runs first. The built-ins use 100 (filelist), 200
     * (placeholder), 300 (dot) and 400 (conditional), so 250 runs after placeholders are replaced and before dots.
     */
    int order();

    /** True to visit a folder after its contents (the default), false to visit it before. */
    default boolean depthFirst() { return true; }

    /** A cheap check on the file or folder name; {@link #process} is called only for names that match. */
    boolean matches(String name);

    /** Acts on one file or folder: rename it, split it, move its contents, delete it. */
    void process(File file, TemplateContext context) throws Exception;
}
