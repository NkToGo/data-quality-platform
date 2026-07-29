package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;

final class ValidationRunParentMismatchException extends RuntimeException {

  ValidationRunParentMismatchException(UUID fileId, UUID profileId) {
    super(
        "Source file '"
            + fileId
            + "' and Validation Profile '"
            + profileId
            + "' belong to different Datasets.");
  }
}
