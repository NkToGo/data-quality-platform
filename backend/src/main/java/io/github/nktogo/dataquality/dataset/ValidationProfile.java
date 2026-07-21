package io.github.nktogo.dataquality.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_profile")
class ValidationProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "dataset_id",
      nullable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_validation_profile_dataset"))
  private Dataset dataset;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  private String name;

  @NotNull
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ValidationProfile() {}

  ValidationProfile(Dataset dataset, String name, Instant createdAt) {
    this.dataset = dataset;
    this.name = name;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  Dataset getDataset() {
    return dataset;
  }

  String getName() {
    return name;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
