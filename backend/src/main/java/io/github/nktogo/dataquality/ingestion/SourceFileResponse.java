package io.github.nktogo.dataquality.ingestion;

import java.time.Instant;
import java.util.UUID;

public record SourceFileResponse(
    UUID id,
    UUID datasetId,
    String originalFilename,
    String contentType,
    long sizeBytes,
    String sha256,
    Instant uploadedAt) {}
