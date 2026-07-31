package io.github.nktogo.dataquality.validation;

import java.util.List;
import java.util.Objects;

public record ValidationInputRow(long recordNumber, List<String> values) {

  public ValidationInputRow {
    if (recordNumber < 2) {
      throw new IllegalArgumentException("recordNumber must be at least 2");
    }
    values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
  }
}
