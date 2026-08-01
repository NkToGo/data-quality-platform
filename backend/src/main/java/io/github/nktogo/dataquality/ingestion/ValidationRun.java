package io.github.nktogo.dataquality.ingestion;

import io.github.nktogo.dataquality.validation.ValidationSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "validation_run")
class ValidationRun {

  private static final int MAXIMUM_FAILURE_REASON_LENGTH = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Column(name = "dataset_id", nullable = false, updatable = false)
  private UUID datasetId;

  @NotNull
  @Column(name = "source_file_id", nullable = false, updatable = false)
  private UUID sourceFileId;

  @NotNull
  @Column(name = "profile_id", nullable = false, updatable = false)
  private UUID profileId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ValidationRunStatus status;

  @PositiveOrZero
  @Column(name = "total_rows", nullable = false)
  private long totalRows;

  @PositiveOrZero
  @Column(name = "valid_rows", nullable = false)
  private long validRows;

  @PositiveOrZero
  @Column(name = "invalid_rows", nullable = false)
  private long invalidRows;

  @PositiveOrZero
  @Column(name = "issue_count", nullable = false)
  private long issueCount;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Size(max = 255)
  @Column(name = "failure_reason", columnDefinition = "text")
  private String failureReason;

  protected ValidationRun() {}

  private ValidationRun(UUID datasetId, UUID sourceFileId, UUID profileId) {
    this.datasetId = datasetId;
    this.sourceFileId = sourceFileId;
    this.profileId = profileId;
    this.status = ValidationRunStatus.PENDING;
    this.totalRows = 0;
    this.validRows = 0;
    this.invalidRows = 0;
    this.issueCount = 0;
    this.startedAt = null;
    this.finishedAt = null;
    this.failureReason = null;
  }

  static ValidationRun pending(UUID datasetId, UUID sourceFileId, UUID profileId) {
    return new ValidationRun(datasetId, sourceFileId, profileId);
  }

  void start(Instant startedAt) {
    requireStatus(ValidationRunStatus.PENDING);
    requireMicrosecondPrecision(startedAt, "startedAt");

    this.status = ValidationRunStatus.PROCESSING;
    this.startedAt = startedAt;
  }

  void recordParsedRowCount(long totalRows) {
    requireStatus(ValidationRunStatus.PROCESSING);
    if (totalRows < 0) {
      throw new IllegalArgumentException("totalRows must not be negative.");
    }

    this.totalRows = totalRows;
  }

  void failParsing(Instant finishedAt, String failureReason) {
    requireStatus(ValidationRunStatus.PROCESSING);
    requireFinishedAt(finishedAt);
    requireFailureReason(failureReason);

    this.status = ValidationRunStatus.FAILED;
    this.totalRows = 0;
    this.validRows = 0;
    this.invalidRows = 0;
    this.issueCount = 0;
    this.finishedAt = finishedAt;
    this.failureReason = failureReason;
  }

  void complete(ValidationSummary summary, Instant finishedAt) {
    requireStatus(ValidationRunStatus.PROCESSING);
    Objects.requireNonNull(summary, "summary must not be null.");
    requireFinishedAt(finishedAt);
    if (summary.totalRows() != totalRows) {
      throw new IllegalArgumentException(
          "Validation summary totalRows must equal the parsed totalRows.");
    }
    if (summary.issueCount() < summary.invalidRows()) {
      throw new IllegalArgumentException(
          "Validation summary issueCount must not be less than invalidRows.");
    }

    this.status = ValidationRunStatus.COMPLETED;
    this.totalRows = summary.totalRows();
    this.validRows = summary.validRows();
    this.invalidRows = summary.invalidRows();
    this.issueCount = summary.issueCount();
    this.finishedAt = finishedAt;
    this.failureReason = null;
  }

  void failValidation(Instant finishedAt, String failureReason) {
    requireStatus(ValidationRunStatus.PROCESSING);
    requireFinishedAt(finishedAt);
    requireFailureReason(failureReason);

    this.status = ValidationRunStatus.FAILED;
    this.validRows = 0;
    this.invalidRows = 0;
    this.issueCount = 0;
    this.finishedAt = finishedAt;
    this.failureReason = failureReason;
  }

  UUID getId() {
    return id;
  }

  UUID getDatasetId() {
    return datasetId;
  }

  UUID getSourceFileId() {
    return sourceFileId;
  }

  UUID getProfileId() {
    return profileId;
  }

  ValidationRunStatus getStatus() {
    return status;
  }

  long getTotalRows() {
    return totalRows;
  }

  long getValidRows() {
    return validRows;
  }

  long getInvalidRows() {
    return invalidRows;
  }

  long getIssueCount() {
    return issueCount;
  }

  Instant getStartedAt() {
    return startedAt;
  }

  Instant getFinishedAt() {
    return finishedAt;
  }

  String getFailureReason() {
    return failureReason;
  }

  private void requireStatus(ValidationRunStatus requiredStatus) {
    if (status != requiredStatus) {
      throw new IllegalStateException(
          "Validation Run must be " + requiredStatus + " for this transition.");
    }
  }

  private void requireMicrosecondPrecision(Instant timestamp, String fieldName) {
    Objects.requireNonNull(timestamp, fieldName + " must not be null.");
    if (timestamp.getNano() % 1_000 != 0) {
      throw new IllegalArgumentException(fieldName + " must use microsecond precision.");
    }
  }

  private void requireFinishedAt(Instant finishedAt) {
    requireMicrosecondPrecision(finishedAt, "finishedAt");
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("finishedAt must not be before startedAt.");
    }
  }

  private void requireFailureReason(String failureReason) {
    if (failureReason == null
        || failureReason.isBlank()
        || failureReason.length() > MAXIMUM_FAILURE_REASON_LENGTH) {
      throw new IllegalArgumentException(
          "failureReason must contain a non-whitespace character and not exceed 255 characters.");
    }
  }
}
