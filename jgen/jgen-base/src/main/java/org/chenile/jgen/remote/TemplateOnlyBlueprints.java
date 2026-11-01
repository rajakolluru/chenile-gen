package org.chenile.jgen.remote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Helpers for template-only blueprints: blueprints made of a descriptor and templates alone, with no Java, used
 * straight from a repository. They are laid out exactly like a jar's resources, so the same repository can later be
 * built into a jar unchanged:
 * <pre>
 *   src/main/resources/META-INF/blueprint.json
 *   src/main/resources/&lt;templateFolder&gt;/...
 * </pre>
 */
final class TemplateOnlyBlueprints {
    static final String RESOURCES = "src/main/resources";
    static final String DESCRIPTOR = "META-INF/blueprint.json";

    private TemplateOnlyBlueprints() { }

    /**
     * Extracts a source archive next to it (once) and returns the repository root inside it. GitHub archives have a
     * single top-level folder ({@code owner-repo-sha/}); that folder is the root.
     */
    static Path extract(Path zip) throws IOException {
        Path target = zip.resolveSibling("source");
        if (!Files.isDirectory(target)) {
            Path partial = zip.resolveSibling("source.part");
            deleteTree(partial);
            Files.createDirectories(partial);
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                    Path out = partial.resolve(entry.getName()).normalize();
                    if (!out.startsWith(partial))
                        throw new IOException("Archive entry escapes the extraction folder: " + entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        }
        try (Stream<Path> top = Files.list(target)) {
            List<Path> entries = top.toList();
            return entries.size() == 1 && Files.isDirectory(entries.get(0)) ? entries.get(0) : target;
        }
    }

    /** The folder that holds META-INF/blueprint.json: {@code <root>/src/main/resources}, or the root itself. */
    static Path resources(Path root) throws IOException {
        if (Files.isRegularFile(root.resolve(RESOURCES).resolve(DESCRIPTOR))) return root.resolve(RESOURCES);
        if (Files.isRegularFile(root.resolve(DESCRIPTOR))) return root;
        throw new IOException("Not a blueprint: no " + RESOURCES + "/" + DESCRIPTOR + " in " + root
                + ". A template-only blueprint is laid out like a jar's resources.");
    }

    /**
     * SHA-256 over the content of a resources folder (every file's relative path and bytes, in sorted order). Unlike
     * a hash of the archive, it does not change if GitHub re-packs the same tag.
     */
    static String contentHash(Path resources) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> files;
            try (Stream<Path> walk = Files.walk(resources)) {
                files = walk.filter(Files::isRegularFile)
                        .sorted(java.util.Comparator.comparing((Path f) -> resources.relativize(f).toString().replace('\\', '/')))
                        .toList();
            }
            for (Path file : files) {
                digest.update(resources.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream in = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
                }
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
