package org.chenile.jgen.portal;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.config.Config;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class OperationService {
    private static final Object SYSTEM_STREAM_LOCK = new Object();

    private final WorkspaceService workspaceService;
    private final BlueprintInputService inputService = new BlueprintInputService();
    private final BlueprintExecutor blueprintExecutor = new BlueprintExecutor();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, OperationRecord> operations = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public OperationService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public OperationRecord startGeneration(String workspaceId, BlueprintConfig blueprintConfig, Config config,
                                           Map<String, Object> submitted) throws IOException {
        Files.createDirectories(workspaceService.generatedRoot(workspaceId));
        submitted.put("destFolder", workspaceService.generatedRoot(workspaceId).toString());
        OperationRecord record = createOperation(workspaceId, "generation");
        executorService.submit(() -> runOperation(record, log -> {
            Map<String, Object> inputMap = inputService.buildInputMap(blueprintConfig, config, submitted);
            log.line("INFO", "Generating blueprint " + blueprintConfig.name);
            captureSystemStreams(log, () -> blueprintExecutor.execute(blueprintConfig, config, inputMap));
            log.line("INFO", "Generation completed.");
        }));
        return record;
    }

    public OperationRecord startMavenCommand(String workspaceId, String type, List<String> arguments) throws IOException {
        OperationRecord record = createOperation(workspaceId, type);
        Path projectRoot = workspaceService.projectRoot(workspaceId);
        executorService.submit(() -> runOperation(record, log -> {
            List<String> command = new ArrayList<>();
            command.add("mvn");
            command.addAll(arguments);
            log.line("INFO", "Running " + String.join(" ", command) + " in " + projectRoot);
            Process process = new ProcessBuilder(command)
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().transferTo(log.asOutputStream("INFO"));
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Command failed with exit code " + exitCode);
            }
            log.line("INFO", "Command completed.");
        }));
        return record;
    }

    public OperationRecord get(String id) {
        OperationRecord record = operations.get(id);
        if (record == null) throw new IllegalArgumentException("Unknown operation " + id);
        return record;
    }

    public SseEmitter stream(String id) {
        OperationRecord record = get(id);
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(id, emitter));
        emitter.onTimeout(() -> removeEmitter(id, emitter));

        executorService.submit(() -> replay(record, emitter));
        return emitter;
    }

    private void replay(OperationRecord record, SseEmitter emitter) {
        try {
            if (Files.exists(record.logFile)) {
                for (String line : Files.readAllLines(record.logFile)) {
                    if (!safeSend(record.id, emitter, "log", line)) return;
                }
            }
            if (record.status == OperationStatus.COMPLETED || record.status == OperationStatus.FAILED) {
                if (safeSend(record.id, emitter, "status", record.status.name())) {
                    safeComplete(emitter);
                }
                removeEmitter(record.id, emitter);
            }
        } catch (IOException e) {
            removeEmitter(record.id, emitter);
            safeComplete(emitter);
        }
    }

    private OperationRecord createOperation(String workspaceId, String type) throws IOException {
        String id = UUID.randomUUID().toString();
        Path logFile = workspaceService.logsRoot(workspaceId).resolve(id + "-" + type + ".log");
        OperationRecord record = new OperationRecord(id, type, workspaceId, logFile);
        operations.put(id, record);
        return record;
    }

    private void runOperation(OperationRecord record, OperationRunner runner) {
        record.status = OperationStatus.RUNNING;
        try (OperationLog log = new OperationLog(record.logFile, line -> broadcast(record.id, "log", line))) {
            runner.run(log);
            record.status = OperationStatus.COMPLETED;
            record.message = "Completed";
        } catch (Exception e) {
            record.status = OperationStatus.FAILED;
            record.message = e.getMessage();
            try (OperationLog log = new OperationLog(record.logFile, line -> broadcast(record.id, "log", line))) {
                log.line("ERROR", e.getMessage());
            } catch (IOException ignored) {
            }
        } finally {
            record.finishedAt = Instant.now();
            broadcast(record.id, "status", record.status.name());
            complete(record.id);
        }
    }

    private void captureSystemStreams(OperationLog log, ThrowingRunnable runnable) throws Exception {
        synchronized (SYSTEM_STREAM_LOCK) {
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            try (PrintStream out = new PrintStream(log.asOutputStream("INFO"), true);
                 PrintStream err = new PrintStream(log.asOutputStream("ERROR"), true)) {
                System.setOut(out);
                System.setErr(err);
                runnable.run();
            } finally {
                System.setOut(oldOut);
                System.setErr(oldErr);
            }
        }
    }

    private void broadcast(String id, String event, String data) {
        List<SseEmitter> list = emitters.getOrDefault(id, List.of());
        for (SseEmitter emitter : List.copyOf(list)) {
            safeSend(id, emitter, event, data);
        }
    }

    private void complete(String id) {
        for (SseEmitter emitter : List.copyOf(emitters.getOrDefault(id, List.of()))) {
            safeComplete(emitter);
        }
        emitters.remove(id);
    }

    private boolean safeSend(String id, SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException | IllegalStateException e) {
            removeEmitter(id, emitter);
            return false;
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
        }
    }

    private void removeEmitter(String id, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(id);
        if (list != null) list.remove(emitter);
    }

    private interface OperationRunner {
        void run(OperationLog log) throws Exception;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
