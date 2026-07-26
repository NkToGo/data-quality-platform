package io.github.nktogo.dataquality.ingestion;

import java.util.List;

record ParsedCsvRow(long recordNumber, List<String> values) {

  ParsedCsvRow {
    values = List.copyOf(values);
  }
}
