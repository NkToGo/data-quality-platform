package io.github.nktogo.dataquality.ingestion;

final class CsvParsingException extends RuntimeException {

  CsvParsingException(String message) {
    super(message);
  }

  CsvParsingException(String message, Throwable cause) {
    super(message, cause);
  }
}
