package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationInputTests {

  @Test
  void preservesExactHeaderAndRowOrder() {
    ValidationInput input =
        new ValidationInput(
            List.of(" Name ", "name"),
            List.of(
                new ValidationInputRow(2, List.of(" first ", "one")),
                new ValidationInputRow(4, List.of("second", "two"))));

    assertThat(input.headers()).containsExactly(" Name ", "name");
    assertThat(input.rows())
        .containsExactly(
            new ValidationInputRow(2, List.of(" first ", "one")),
            new ValidationInputRow(4, List.of("second", "two")));
  }

  @Test
  void defensivelyCopiesHeadersRowsAndValues() {
    List<String> headers = new ArrayList<>(List.of("name"));
    List<String> values = new ArrayList<>(List.of("Alice"));
    ValidationInputRow row = new ValidationInputRow(2, values);
    List<ValidationInputRow> rows = new ArrayList<>(List.of(row));

    ValidationInput input = new ValidationInput(headers, rows);
    headers.set(0, "changed");
    values.set(0, "changed");
    rows.clear();

    assertThat(input.headers()).containsExactly("name");
    assertThat(input.rows()).containsExactly(new ValidationInputRow(2, List.of("Alice")));
  }

  @Test
  void exposesImmutableCollections() {
    ValidationInputRow row = new ValidationInputRow(2, List.of("Alice"));
    ValidationInput input = new ValidationInput(List.of("name"), List.of(row));

    assertThatThrownBy(() -> input.headers().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> input.rows().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> row.values().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void acceptsAHeaderOnlyInput() {
    ValidationInput input = new ValidationInput(List.of("name"), List.of());

    assertThat(input.headers()).containsExactly("name");
    assertThat(input.rows()).isEmpty();
  }

  @Test
  void rejectsMissingOrEmptyHeaders() {
    assertThatThrownBy(() -> new ValidationInput(null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ValidationInput(List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullBlankAndExactDuplicateHeaders() {
    assertThatThrownBy(
            () ->
                new ValidationInput(
                    new ArrayList<>(java.util.Arrays.asList("name", null)), List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ValidationInput(List.of("name", "\t"), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ValidationInput(List.of("name", "name"), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsCaseAndWhitespaceDistinctHeaders() {
    ValidationInput input = new ValidationInput(List.of("name", "Name", " name "), List.of());

    assertThat(input.headers()).containsExactly("name", "Name", " name ");
  }

  @Test
  void rejectsNullRowsAndValues() {
    assertThatThrownBy(
            () ->
                new ValidationInput(
                    List.of("name"),
                    new ArrayList<>(java.util.Arrays.asList((ValidationInputRow) null))))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ValidationInputRow(2, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ValidationInputRow(2, new ArrayList<>(java.util.Arrays.asList((String) null))))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsRowsWhoseWidthDoesNotMatchTheHeader() {
    assertThatThrownBy(
            () ->
                new ValidationInput(
                    List.of("name", "age"), List.of(new ValidationInputRow(2, List.of("Alice")))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsRecordNumbersBelowTwo() {
    assertThatThrownBy(() -> new ValidationInputRow(1, List.of("Alice")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsDuplicateOrDescendingRecordNumbers() {
    assertThatThrownBy(
            () ->
                new ValidationInput(
                    List.of("name"),
                    List.of(
                        new ValidationInputRow(3, List.of("Alice")),
                        new ValidationInputRow(3, List.of("Bob")))))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                new ValidationInput(
                    List.of("name"),
                    List.of(
                        new ValidationInputRow(4, List.of("Alice")),
                        new ValidationInputRow(2, List.of("Bob")))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
