package io.github.nktogo.dataquality.ingestion;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateValidationRunRequest(@NotNull UUID profileId) {}
