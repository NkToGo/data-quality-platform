package io.github.nktogo.dataquality.ingestion;

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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_run")
class ValidationRun {

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
}
