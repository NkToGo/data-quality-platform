package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;

final class SourceFileNotFoundException extends RuntimeException {

  SourceFileNotFoundException(UUID fileId) {
    super("Source file '" + fileId + "' was not found.");
  }
}
