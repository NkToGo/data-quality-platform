package io.github.nktogo.dataquality.dataset;

import java.time.Instant;
import java.util.UUID;

public record ValidationProfileResponse(UUID id, UUID datasetId, String name, Instant createdAt) {}
