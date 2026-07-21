package io.github.nktogo.dataquality.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dataset")
class Dataset {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  private String name;

  @Size(max = 2000)
  @Column(length = 2000)
  private String description;

  @NotNull
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Dataset() {}

  Dataset(String name, String description, Instant createdAt) {
    this.name = name;
    this.description = description;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getName() {
    return name;
  }

  String getDescription() {
    return description;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
