package io.github.nktogo.dataquality.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReportFormatTests {

  @Test
  void acceptsExactlyOneLowercaseSupportedValue() {
    assertThat(ReportFormat.parse(List.of("json"))).isEqualTo(ReportFormat.JSON);
    assertThat(ReportFormat.parse(List.of("csv"))).isEqualTo(ReportFormat.CSV);
  }

  @ParameterizedTest
  @MethodSource("invalidValues")
  void rejectsEveryOtherQueryValueShape(List<String> values) {
    assertThatThrownBy(() -> ReportFormat.parse(values))
        .isInstanceOf(InvalidReportFormatException.class)
        .hasMessage("Query parameter 'format' must be exactly one of: json, csv.");
  }

  private static Stream<Arguments> invalidValues() {
    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(List.of()),
        Arguments.of(List.of("")),
        Arguments.of(List.of(" ")),
        Arguments.of(List.of("JSON")),
        Arguments.of(List.of("Csv")),
        Arguments.of(List.of("xml")),
        Arguments.of(List.of("json ")),
        Arguments.of(List.of("json", "csv")),
        Arguments.of(List.of("json", "json")));
  }
}
