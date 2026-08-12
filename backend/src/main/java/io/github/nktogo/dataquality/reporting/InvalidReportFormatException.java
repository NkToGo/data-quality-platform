package io.github.nktogo.dataquality.reporting;

final class InvalidReportFormatException extends RuntimeException {

  InvalidReportFormatException() {
    super("Query parameter 'format' must be exactly one of: json, csv.");
  }
}
