package org.chenile.jgen.portal;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class WorkspaceService {
    private final Path root = Path.of(".jgen-portal", "workspaces").toAbsolutePath().normalize();

    public WorkspaceService() throws IOException {
        Files.createDirectories(root);
    }

    public Map<String, Object> createSession() throws IOException {
        String id = UUID.randomUUID().toString();
        Path workspace = workspaceRoot(id);
        Files.createDirectories(workspace.resolve("uploads"));
        Files.createDirectories(workspace.resolve("generated"));
        Files.createDirectories(workspace.resolve("logs"));
        return Map.of("sessionId", id, "workspaceId", id);
    }

    public Map<String, Object> session(String workspaceId) {
        Path workspace = workspaceRoot(workspaceId);
        boolean exists = Files.isDirectory(workspace)
                && Files.isDirectory(workspace.resolve("generated"))
                && Files.isDirectory(workspace.resolve("logs"))
                && Files.isDirectory(workspace.resolve("uploads"));
        return Map.of("sessionId", workspaceId, "workspaceId", workspaceId, "exists", exists);
    }

    public Path workspaceRoot(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Missing workspace id.");
        }
        Path workspace = root.resolve(workspaceId).normalize();
        if (!workspace.startsWith(root)) {
            throw new IllegalArgumentException("Invalid workspace id.");
        }
        return workspace;
    }

    public Path generatedRoot(String workspaceId) {
        return workspaceRoot(workspaceId).resolve("generated");
    }

    public Path logsRoot(String workspaceId) {
        return workspaceRoot(workspaceId).resolve("logs");
    }

    public Path uploadsRoot(String workspaceId) {
        return workspaceRoot(workspaceId).resolve("uploads");
    }

    public Path resolveGeneratedPath(String workspaceId, String relativePath) {
        Path generated = generatedRoot(workspaceId).normalize();
        Path resolved = generated.resolve(relativePath == null ? "" : relativePath).normalize();
        if (!resolved.startsWith(generated)) {
            throw new IllegalArgumentException("Path escapes workspace.");
        }
        return resolved;
    }

    public Path projectRoot(String workspaceId) throws IOException {
        Path generated = generatedRoot(workspaceId);
        if (!Files.exists(generated)) return generated;
        if (Files.isRegularFile(generated.resolve("pom.xml"))) return generated;
        List<Path> directories = new ArrayList<>();
        try (var stream = Files.list(generated)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .forEach(directories::add);
        }
        if (directories.size() == 1) return directories.get(0);
        try (var stream = Files.walk(generated, 3)) {
            return stream
                    .filter(path -> Files.isRegularFile(path.resolve("pom.xml")))
                    .filter(path -> !generated.relativize(path).toString().contains("/."))
                    .min(Comparator.comparingInt(path -> generated.relativize(path).getNameCount()))
                    .orElse(generated);
        }
    }

    public Map<String, Object> tree(String workspaceId) throws IOException {
        return node(generatedRoot(workspaceId), generatedRoot(workspaceId));
    }

    public Resource zipWorkspace(String workspaceId) throws IOException {
        Path generated = generatedRoot(workspaceId);
        Path zip = workspaceRoot(workspaceId).resolve("generated-project.zip");
        try (OutputStream outputStream = Files.newOutputStream(zip);
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            if (Files.exists(generated)) {
                try (var stream = Files.walk(generated)) {
                    for (Path path : stream.filter(Files::isRegularFile).toList()) {
                        Path relative = generated.relativize(path);
                        zipOutputStream.putNextEntry(new ZipEntry(relative.toString()));
                        Files.copy(path, zipOutputStream);
                        zipOutputStream.closeEntry();
                    }
                }
            }
        }
        return new FileSystemResource(zip);
    }

    private Map<String, Object> node(Path rootPath, Path path) throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("name", path.equals(rootPath) ? "generated" : path.getFileName().toString());
        map.put("path", rootPath.relativize(path).toString());
        map.put("directory", Files.isDirectory(path));
        map.put("size", Files.isRegularFile(path) ? Files.size(path) : 0L);
        map.put("modified", Files.exists(path) ? Files.getLastModifiedTime(path).toInstant().toString() : null);
        if (Files.isDirectory(path)) {
            List<Map<String, Object>> children = new ArrayList<>();
            if (Files.exists(path)) {
                try (var stream = Files.list(path)) {
                    for (Path child : stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                        children.add(node(rootPath, child));
                    }
                }
            }
            map.put("children", children);
        }
        return map;
    }
}
