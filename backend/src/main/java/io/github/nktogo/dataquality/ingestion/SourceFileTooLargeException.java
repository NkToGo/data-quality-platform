package io.github.nktogo.dataquality.ingestion;

final class SourceFileTooLargeException extends RuntimeException {

  SourceFileTooLargeException(long maximumSizeBytes) {
    super(
        "The uploaded file exceeds the configured maximum size of " + maximumSizeBytes + " bytes.");
  }
}
