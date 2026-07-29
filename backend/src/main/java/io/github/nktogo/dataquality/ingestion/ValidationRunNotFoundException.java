package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;

final class ValidationRunNotFoundException extends RuntimeException {

  ValidationRunNotFoundException(UUID runId) {
    super("Validation Run '" + runId + "' was not found.");
  }
}
