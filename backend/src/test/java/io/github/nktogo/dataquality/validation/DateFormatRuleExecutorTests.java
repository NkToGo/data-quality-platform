package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationDateFormat;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DateFormatRuleExecutorTests {

  private final DateFormatRuleExecutor executor = new DateFormatRuleExecutor();

  @ParameterizedTest
  @MethodSource("supportedFormats")
  void acceptsEveryControlledFormat(ValidationDateFormat format, String value) {
    ExecutableValidationRule rule = rule(format);

    assertThat(executor.execute(rule, 0, List.of(row(2, value)))).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidFormats")
  void rejectsWrongFormatsAndImpossibleDates(ValidationDateFormat format, String value) {
    ExecutableValidationRule rule = rule(format);

    assertThat(executor.execute(rule, 0, List.of(row(2, value))))
        .containsExactly(issue(rule, 2, value, format));
  }

  @Test
  void appliesStrictLeapYearRules() {
    ExecutableValidationRule rule = rule(ValidationDateFormat.ISO_DATE);

    assertThat(executor.execute(rule, 0, rows("2024-02-29", "2023-02-29")))
        .containsExactly(issue(rule, 3, "2023-02-29", ValidationDateFormat.ISO_DATE));
  }

  @Test
  void parsingUsesLocaleRootRatherThanTheJvmDefaultLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.JAPAN);
      ExecutableValidationRule rule = rule(ValidationDateFormat.DAY_MONTH_YEAR);

      assertThat(executor.execute(rule, 0, rows("31/12/2024", "12/31/2024")))
          .containsExactly(issue(rule, 3, "12/31/2024", ValidationDateFormat.DAY_MONTH_YEAR));
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void skipsEveryBlankValue() {
    ExecutableValidationRule rule = rule(ValidationDateFormat.ISO_DATE);

    assertThat(executor.execute(rule, 0, rows("", " ", "\t", "\n", "\u2003"))).isEmpty();
  }

  private static ExecutableValidationRule rule(ValidationDateFormat format) {
    return new ExecutableValidationRule(
        UUID.randomUUID(),
        "date",
        ValidationRuleType.DATE_FORMAT,
        new ValidationRuleConfiguration.DateFormat(format),
        ValidationRuleSeverity.ERROR);
  }

  private static ValidationInputRow row(long recordNumber, String value) {
    return new ValidationInputRow(recordNumber, List.of(value));
  }

  private static List<ValidationInputRow> rows(String... values) {
    java.util.ArrayList<ValidationInputRow> rows = new java.util.ArrayList<>();
    for (int index = 0; index < values.length; index++) {
      rows.add(row(index + 2L, values[index]));
    }
    return List.copyOf(rows);
  }

  private static ValidationIssueDraft issue(
      ExecutableValidationRule rule,
      long recordNumber,
      String observedValue,
      ValidationDateFormat format) {
    return new ValidationIssueDraft(
        recordNumber,
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        "Value must match date format " + format + ".",
        observedValue);
  }

  private static Stream<Arguments> supportedFormats() {
    return Stream.of(
        Arguments.of(ValidationDateFormat.ISO_DATE, "2024-12-31"),
        Arguments.of(ValidationDateFormat.DAY_MONTH_YEAR, "31/12/2024"),
        Arguments.of(ValidationDateFormat.MONTH_DAY_YEAR, "12/31/2024"));
  }

  private static Stream<Arguments> invalidFormats() {
    return Stream.of(
        Arguments.of(ValidationDateFormat.ISO_DATE, "2024-2-01"),
        Arguments.of(ValidationDateFormat.ISO_DATE, "2024/02/01"),
        Arguments.of(ValidationDateFormat.ISO_DATE, " 2024-02-01"),
        Arguments.of(ValidationDateFormat.ISO_DATE, "2024-02-01 "),
        Arguments.of(ValidationDateFormat.ISO_DATE, "2024-13-01"),
        Arguments.of(ValidationDateFormat.DAY_MONTH_YEAR, "31/04/2024"),
        Arguments.of(ValidationDateFormat.DAY_MONTH_YEAR, "01-02-2024"),
        Arguments.of(ValidationDateFormat.MONTH_DAY_YEAR, "02/30/2024"),
        Arguments.of(ValidationDateFormat.MONTH_DAY_YEAR, "31/12/2024"));
  }
}
