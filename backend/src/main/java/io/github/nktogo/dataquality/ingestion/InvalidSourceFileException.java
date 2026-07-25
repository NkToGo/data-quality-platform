package io.github.nktogo.dataquality.ingestion;

final class InvalidSourceFileException extends RuntimeException {

  InvalidSourceFileException(String message) {
    super(message);
  }
}
