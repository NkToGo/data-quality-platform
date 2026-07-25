package io.github.nktogo.dataquality.ingestion;

import java.time.Instant;
import java.util.UUID;

public record ValidationRunResponse(
    UUID id,
    UUID datasetId,
    UUID sourceFileId,
    UUID profileId,
    ValidationRunStatus status,
    long totalRows,
    long validRows,
    long invalidRows,
    long issueCount,
    Instant startedAt,
    Instant finishedAt,
    String failureReason) {}
