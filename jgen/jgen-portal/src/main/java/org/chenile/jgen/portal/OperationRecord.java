package org.chenile.jgen.portal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;
import java.time.Instant;

public class OperationRecord {
    public String id;
    public String type;
    public OperationStatus status;
    public Instant startedAt;
    public Instant finishedAt;
    public String message;
    @JsonIgnore
    public Path logFile;
    public String workspaceId;

    public OperationRecord(String id, String type, String workspaceId, Path logFile) {
        this.id = id;
        this.type = type;
        this.workspaceId = workspaceId;
        this.logFile = logFile;
        this.status = OperationStatus.QUEUED;
        this.startedAt = Instant.now();
    }
}
