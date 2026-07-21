package io.github.nktogo.dataquality.dataset;

import java.util.UUID;

final class ValidationProfileNotFoundException extends RuntimeException {

  ValidationProfileNotFoundException(UUID profileId) {
    super("Validation Profile '" + profileId + "' was not found.");
  }
}
