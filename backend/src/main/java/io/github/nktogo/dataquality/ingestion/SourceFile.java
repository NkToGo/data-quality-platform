package io.github.nktogo.dataquality.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_file")
class SourceFile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Column(name = "dataset_id", nullable = false, updatable = false)
  private UUID datasetId;

  @NotBlank
  @Size(max = 255)
  @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
  private String originalFilename;

  @NotBlank
  @Size(max = 255)
  @Column(name = "content_type", nullable = false, length = 255, updatable = false)
  private String contentType;

  @Positive
  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @NotBlank
  @Size(min = 64, max = 64)
  @Pattern(regexp = "^[0-9a-f]{64}$")
  @Column(nullable = false, length = 64, updatable = false)
  private String sha256;

  @NotNull
  @Size(min = 1)
  @Column(name = "content_bytes", nullable = false, updatable = false, columnDefinition = "bytea")
  private byte[] contentBytes;

  @NotNull
  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  protected SourceFile() {}

  SourceFile(
      UUID datasetId,
      String originalFilename,
      String contentType,
      long sizeBytes,
      String sha256,
      byte[] contentBytes,
      Instant uploadedAt) {
    this.datasetId = datasetId;
    this.originalFilename = originalFilename;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.contentBytes = contentBytes.clone();
    this.uploadedAt = uploadedAt;
  }

  UUID getId() {
    return id;
  }

  UUID getDatasetId() {
    return datasetId;
  }

  String getOriginalFilename() {
    return originalFilename;
  }

  String getContentType() {
    return contentType;
  }

  long getSizeBytes() {
    return sizeBytes;
  }

  String getSha256() {
    return sha256;
  }

  byte[] getContentBytes() {
    return contentBytes.clone();
  }

  Instant getUploadedAt() {
    return uploadedAt;
  }
}
