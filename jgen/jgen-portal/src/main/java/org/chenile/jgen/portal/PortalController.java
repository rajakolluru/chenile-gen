package org.chenile.jgen.portal;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PortalController {
    private final WorkspaceService workspaceService;
    private final OperationService operationService;
    private final ConfigProvider configProvider = new ConfigProvider();
    private final BlueprintInputService inputService = new BlueprintInputService();

    public PortalController(WorkspaceService workspaceService, OperationService operationService) {
        this.workspaceService = workspaceService;
        this.operationService = operationService;
    }

    @GetMapping("/api/generators")
    public List<Map<String, Object>> generators() {
        List<BlueprintConfig> blueprints = new ArrayList<>(Registry.blueprints.values());
        blueprints.sort(Comparator.comparing(bp -> bp.name));
        return blueprints.stream().map(bp -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", bp.name);
            map.put("description", bp.description);
            map.put("category", bp.category == null ? "JGen" : bp.category);
            map.put("version", bp.version == null ? "2.1.5" : bp.version);
            return map;
        }).toList();
    }

    @GetMapping("/api/generators/{name}")
    public Map<String, Object> generator(@PathVariable String name) {
        BlueprintConfig blueprint = blueprint(name);
        Config config = configProvider.obtainDefaultConfig();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", blueprint.name);
        map.put("description", blueprint.description);
        map.put("category", blueprint.category == null ? "JGen" : blueprint.category);
        map.put("version", blueprint.version == null ? "2.1.5" : blueprint.version);
        map.put("fields", inputService.describeFields(blueprint, config));
        return map;
    }

    @PostMapping("/api/sessions")
    public Map<String, Object> session() throws IOException {
        return workspaceService.createSession();
    }

    @GetMapping("/api/sessions/{id}")
    public Map<String, Object> session(@PathVariable String id) {
        return workspaceService.session(id);
    }

    @PostMapping("/api/uploads")
    public Map<String, Object> upload(@RequestParam String sessionId, @RequestParam String fieldName,
                                      @RequestParam(required = false) String generator,
                                      @RequestParam MultipartFile file) throws IOException {
        validateUpload(generator, fieldName, file);
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        if (originalName.contains("..")) throw new IllegalArgumentException("Invalid file name.");
        Path target = workspaceService.uploadsRoot(sessionId).resolve(fieldName + "-" + originalName).normalize();
        if (!target.startsWith(workspaceService.uploadsRoot(sessionId))) throw new IllegalArgumentException("Invalid file path.");
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldName", fieldName);
        map.put("fileName", originalName);
        map.put("path", target.toString());
        map.put("size", Files.size(target));
        return map;
    }

    @PostMapping("/api/generations")
    public OperationRecord generate(@RequestBody Map<String, Object> request) throws IOException {
        String sessionId = stringValue(request.get("sessionId"));
        String generatorName = stringValue(request.get("generator"));
        Map<String, Object> answers = mapValue(request.get("answers"));
        BlueprintConfig blueprint = blueprint(generatorName);
        return operationService.startGeneration(sessionId, blueprint, configProvider.obtainDefaultConfig(), new HashMap<>(answers));
    }

    @GetMapping("/api/operations/{id}")
    public OperationRecord operation(@PathVariable String id) {
        return operationService.get(id);
    }

    @GetMapping("/api/operations/{id}/logs/stream")
    public SseEmitter operationStream(@PathVariable String id) throws IOException {
        return operationService.stream(id);
    }

    @GetMapping("/api/operations/{id}/logs/download")
    public ResponseEntity<Resource> operationLog(@PathVariable String id) {
        OperationRecord record = operationService.get(id);
        Resource resource = new FileSystemResource(record.logFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.id + ".log\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @GetMapping("/api/workspaces/{id}/tree")
    public Map<String, Object> tree(@PathVariable String id) throws IOException {
        return workspaceService.tree(id);
    }

    @GetMapping("/api/workspaces/{id}/files")
    public Map<String, Object> file(@PathVariable String id, @RequestParam String path) throws IOException {
        Path file = workspaceService.resolveGeneratedPath(id, path);
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Not a file.");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", path);
        map.put("name", file.getFileName().toString());
        map.put("size", Files.size(file));
        map.put("modified", Files.getLastModifiedTime(file).toInstant().toString());
        map.put("content", Files.readString(file, StandardCharsets.UTF_8));
        return map;
    }

    @PostMapping("/api/workspaces/{id}/tests")
    public OperationRecord tests(@PathVariable String id, @RequestBody(required = false) Map<String, Object> request) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("test");
        List<String> selected = listValue(request == null ? null : request.get("tests"));
        if (!selected.isEmpty()) args.add("-Dtest=" + String.join(",", selected));
        return operationService.startMavenCommand(id, "tests", args);
    }

    @PostMapping("/api/workspaces/{id}/builds")
    public OperationRecord build(@PathVariable String id) throws IOException {
        return operationService.startMavenCommand(id, "build", List.of("package"));
    }

    @GetMapping("/api/workspaces/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
        Resource resource = workspaceService.zipWorkspace(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"generated-project.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> errors(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private BlueprintConfig blueprint(String name) {
        BlueprintConfig blueprint = Registry.blueprints.get(name);
        if (blueprint == null) throw new IllegalArgumentException("Unknown generator " + name);
        return blueprint;
    }

    private void validateUpload(String generator, String fieldName, MultipartFile file) {
        if (generator == null || generator.isBlank()) return;
        InputField field = blueprint(generator).inputFields.stream()
                .filter(candidate -> candidate.name.equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown upload field " + fieldName));
        if (field.type != FieldType.FILE) throw new IllegalArgumentException("Field " + fieldName + " is not a file field.");
        if (field.maxSizeBytes != null && file.getSize() > field.maxSizeBytes) {
            throw new IllegalArgumentException("File exceeds maximum size.");
        }
        if (field.accept != null && !field.accept.isBlank()) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            boolean matched = false;
            for (String accept : field.accept.split(",")) {
                String trimmed = accept.trim().toLowerCase();
                if (trimmed.startsWith(".") && name.endsWith(trimmed)) matched = true;
                if (!trimmed.startsWith(".") && file.getContentType() != null && file.getContentType().equalsIgnoreCase(trimmed)) matched = true;
            }
            if (!matched) throw new IllegalArgumentException("File type is not accepted for " + fieldName + ".");
        }
    }

    private String stringValue(Object value) {
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("Missing required value.");
        return value.toString();
    }

    private Map<String, Object> mapValue(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected object.");
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }

    private List<String> listValue(Object value) {
        if (!(value instanceof List<?> raw)) return List.of();
        return raw.stream().map(Object::toString).toList();
    }
}
