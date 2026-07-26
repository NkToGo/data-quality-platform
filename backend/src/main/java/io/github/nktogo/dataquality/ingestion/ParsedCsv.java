package io.github.nktogo.dataquality.ingestion;

import java.util.List;

record ParsedCsv(List<String> headers, List<ParsedCsvRow> rows) {

  ParsedCsv {
    headers = List.copyOf(headers);
    rows = List.copyOf(rows);
  }
}
