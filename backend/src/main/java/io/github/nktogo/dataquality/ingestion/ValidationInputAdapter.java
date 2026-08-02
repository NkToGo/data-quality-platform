package io.github.nktogo.dataquality.ingestion;

import io.github.nktogo.dataquality.validation.ValidationInput;
import io.github.nktogo.dataquality.validation.ValidationInputRow;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
final class ValidationInputAdapter {

  ValidationInput adapt(ParsedCsv parsedCsv) {
    Objects.requireNonNull(parsedCsv, "parsedCsv must not be null");

    return new ValidationInput(
        parsedCsv.headers(),
        parsedCsv.rows().stream()
            .map(row -> new ValidationInputRow(row.recordNumber(), row.values()))
            .toList());
  }
}
