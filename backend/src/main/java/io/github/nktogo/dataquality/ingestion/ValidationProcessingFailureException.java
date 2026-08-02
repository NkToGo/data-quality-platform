package io.github.nktogo.dataquality.ingestion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

final class ValidationProcessingFailureException extends RuntimeException {

  private final UUID runId;
  private final Instant startedAt;
  private final long totalRows;

  ValidationProcessingFailureException(
      UUID runId, Instant startedAt, long totalRows, Throwable cause) {
    super("Validation processing failed.", Objects.requireNonNull(cause, "cause must not be null"));
    this.runId = Objects.requireNonNull(runId, "runId must not be null");
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    if (totalRows < 0) {
      throw new IllegalArgumentException("totalRows must not be negative.");
    }
    this.totalRows = totalRows;
  }

  UUID runId() {
    return runId;
  }

  Instant startedAt() {
    return startedAt;
  }

  long totalRows() {
    return totalRows;
  }
}
