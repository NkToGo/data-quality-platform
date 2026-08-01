package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nktogo.dataquality.validation.ValidationInput;
import io.github.nktogo.dataquality.validation.ValidationInputRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationInputAdapterTests {

  private final ValidationInputAdapter adapter = new ValidationInputAdapter();

  @Test
  void preservesExactHeadersRowsRecordNumbersAndValues() {
    ParsedCsv parsedCsv =
        new ParsedCsv(
            List.of(" Name ", "name", "city"),
            List.of(
                new ParsedCsvRow(2, List.of("", " \t ", "München")),
                new ParsedCsvRow(4, List.of("Alice", "ALICE", "東京"))));

    ValidationInput input = adapter.adapt(parsedCsv);

    assertThat(input.headers()).containsExactly(" Name ", "name", "city");
    assertThat(input.rows())
        .containsExactly(
            new ValidationInputRow(2, List.of("", " \t ", "München")),
            new ValidationInputRow(4, List.of("Alice", "ALICE", "東京")));
  }

  @Test
  void preservesAHeaderOnlyCsv() {
    ParsedCsv parsedCsv = new ParsedCsv(List.of("id", "value"), List.of());

    ValidationInput input = adapter.adapt(parsedCsv);

    assertThat(input.headers()).containsExactly("id", "value");
    assertThat(input.rows()).isEmpty();
  }

  @Test
  void rejectsNullParsedCsv() {
    assertThatThrownBy(() -> adapter.adapt(null)).isInstanceOf(NullPointerException.class);
  }
}
