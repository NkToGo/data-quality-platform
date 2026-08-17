package io.github.nktogo.dataquality.reporting;

import java.util.List;

enum ReportFormat {
  JSON("json"),
  CSV("csv");

  private final String queryValue;

  ReportFormat(String queryValue) {
    this.queryValue = queryValue;
  }

  String queryValue() {
    return queryValue;
  }

  static ReportFormat parse(List<String> values) {
    if (values == null || values.size() != 1) {
      throw new InvalidReportFormatException();
    }

    return switch (values.getFirst()) {
      case "json" -> JSON;
      case "csv" -> CSV;
      default -> throw new InvalidReportFormatException();
    };
  }
}
