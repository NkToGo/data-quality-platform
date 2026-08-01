package io.github.nktogo.dataquality.validation;

import java.util.UUID;

public interface ValidationProcessingAccess {

  ValidationResult validateAndPersist(UUID runId, UUID profileId, ValidationInput input);
}
