package io.github.nktogo.dataquality.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ValidationInput(List<String> headers, List<ValidationInputRow> rows) {

  public ValidationInput {
    headers = List.copyOf(Objects.requireNonNull(headers, "headers must not be null"));
    rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));

    if (headers.isEmpty()) {
      throw new IllegalArgumentException("headers must not be empty");
    }

    Set<String> uniqueHeaders = new HashSet<>();
    for (String header : headers) {
      if (header.isBlank()) {
        throw new IllegalArgumentException("headers must not contain blank values");
      }
      if (!uniqueHeaders.add(header)) {
        throw new IllegalArgumentException("headers must be unique");
      }
    }

    long previousRecordNumber = 1;
    for (ValidationInputRow row : rows) {
      if (row.values().size() != headers.size()) {
        throw new IllegalArgumentException("every row must contain one value for each header");
      }
      if (row.recordNumber() <= previousRecordNumber) {
        throw new IllegalArgumentException("row record numbers must be strictly increasing");
      }
      previousRecordNumber = row.recordNumber();
    }
  }
}
