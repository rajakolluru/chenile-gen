package org.chenile.jgen.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

public class CopyFromJar {
    public void copyFromJar(String source, final Path target) throws URISyntaxException, IOException {
        copyFromJar(getClass().getClassLoader(), source, target);
    }

    /**
     * Copies a resource folder from the given class loader. For a blueprint loaded from its own jar the folder is
     * looked up in that jar first, so a same-named folder elsewhere on the class path cannot shadow it.
     */
    public void copyFromJar(ClassLoader loader, String source, final Path target) throws URISyntaxException, IOException {
        java.net.URL url = loader instanceof java.net.URLClassLoader own ? own.findResource(source) : null;
        if (url == null) url = loader.getResource(source);
        if (url == null) throw new IOException("Template folder " + source + " not found");
        URI resource = url.toURI();
        if (resource.getScheme().equals("file")) {
            copyTree(Paths.get(resource), target);
            return;
        }
        FileSystem fileSystem;
        boolean closeFileSystem = false;
        try {
            fileSystem = FileSystems.newFileSystem(resource, Collections.emptyMap());
            closeFileSystem = true;
        } catch (FileSystemAlreadyExistsException ignored) {
            // Composite blueprints can copy several templates from one executable JAR.
            fileSystem = FileSystems.getFileSystem(resource);
        }

        try {
            copyTree(fileSystem.getPath(source), target);
        } finally {
            if (closeFileSystem) fileSystem.close();
        }
    }
    private void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
