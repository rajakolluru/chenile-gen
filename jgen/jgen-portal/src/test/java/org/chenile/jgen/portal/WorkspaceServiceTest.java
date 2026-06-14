package org.chenile.jgen.portal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceServiceTest {
    @Test
    void createsSessionAndRejectsPathTraversal() throws IOException {
        WorkspaceService service = new WorkspaceService();
        Map<String, Object> session = service.createSession();
        String id = session.get("sessionId").toString();

        assertTrue(Files.exists(service.generatedRoot(id)));
        assertEquals(true, service.session(id).get("exists"));
        assertThrows(IllegalArgumentException.class, () -> service.resolveGeneratedPath(id, "../outside.txt"));
    }

    @Test
    void returnsSingleGeneratedDirectoryAsProjectRoot() throws IOException {
        WorkspaceService service = new WorkspaceService();
        String id = service.createSession().get("sessionId").toString();
        Files.createDirectories(service.generatedRoot(id).resolve(".meta"));
        Path project = service.generatedRoot(id).resolve("orders");
        Files.createDirectories(project);

        assertEquals(project, service.projectRoot(id));
    }

    @Test
    void prefersNearestPomWhenMultipleVisibleDirectoriesExist() throws IOException {
        WorkspaceService service = new WorkspaceService();
        String id = service.createSession().get("sessionId").toString();
        Path project = service.generatedRoot(id).resolve("orders");
        Files.createDirectories(project.resolve("orders-service"));
        Files.createDirectories(service.generatedRoot(id).resolve("other"));
        Files.writeString(project.resolve("pom.xml"), "<project />");
        Files.writeString(project.resolve("orders-service").resolve("pom.xml"), "<project />");

        assertEquals(project, service.projectRoot(id));
    }
}
