package io.github.nktogo.dataquality.dataset;

import java.time.Instant;
import java.util.UUID;

public record DatasetResponse(UUID id, String name, String description, Instant createdAt) {}
