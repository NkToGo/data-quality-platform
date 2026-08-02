package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationDataType;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class DataTypeRuleExecutorTests {

  private final DataTypeRuleExecutor executor = new DataTypeRuleExecutor();

  @Test
  void integerAcceptsSignsAndArbitraryPrecisionValues() {
    ExecutableValidationRule rule = rule(ValidationDataType.INTEGER);
    List<ValidationInputRow> rows =
        rows("0", "+1", "-2", "9223372036854775808", "-999999999999999999999999999999999999999");

    assertThat(executor.execute(rule, 0, rows)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidIntegers")
  void integerRejectsNonIntegerSyntaxWithoutTrimming(String value) {
    assertInvalid(ValidationDataType.INTEGER, value);
  }

  @ParameterizedTest
  @MethodSource("validDecimals")
  void decimalAcceptsLocaleIndependentBigDecimalSyntax(String value) {
    ExecutableValidationRule rule = rule(ValidationDataType.DECIMAL);

    assertThat(executor.execute(rule, 0, List.of(row(2, value)))).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidDecimals")
  void decimalRejectsGroupingDecimalCommasAndWhitespace(String value) {
    assertInvalid(ValidationDataType.DECIMAL, value);
  }

  @Test
  void decimalParsingDoesNotDependOnTheJvmDefaultLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      ExecutableValidationRule rule = rule(ValidationDataType.DECIMAL);

      assertThat(executor.execute(rule, 0, rows("1.25", "1,25")))
          .containsExactly(issue(rule, 3, "1,25"));
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void booleanAcceptsOnlyExactLowercaseValues() {
    ExecutableValidationRule rule = rule(ValidationDataType.BOOLEAN);

    assertThat(executor.execute(rule, 0, rows("true", "false", "TRUE", "False", "1")))
        .containsExactly(issue(rule, 4, "TRUE"), issue(rule, 5, "False"), issue(rule, 6, "1"));
  }

  @Test
  void stringAcceptsEveryNonblankValue() {
    ExecutableValidationRule rule = rule(ValidationDataType.STRING);

    assertThat(executor.execute(rule, 0, rows("text", " text ", "123", "true"))).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(ValidationDataType.class)
  void everyDataTypeSkipsBlankValues(ValidationDataType dataType) {
    ExecutableValidationRule rule = rule(dataType);

    assertThat(executor.execute(rule, 0, rows("", " ", "\t", "\n", "\u2003"))).isEmpty();
  }

  private void assertInvalid(ValidationDataType dataType, String value) {
    ExecutableValidationRule rule = rule(dataType);

    assertThat(executor.execute(rule, 0, List.of(row(2, value))))
        .containsExactly(issue(rule, 2, value));
  }

  private static ExecutableValidationRule rule(ValidationDataType dataType) {
    return new ExecutableValidationRule(
        UUID.randomUUID(),
        "value",
        ValidationRuleType.DATA_TYPE,
        new ValidationRuleConfiguration.DataType(dataType),
        ValidationRuleSeverity.ERROR);
  }

  private static ValidationInputRow row(long recordNumber, String value) {
    return new ValidationInputRow(recordNumber, List.of(value));
  }

  private static List<ValidationInputRow> rows(String... values) {
    long recordNumber = 2;
    Stream.Builder<ValidationInputRow> rows = Stream.builder();
    for (String value : values) {
      rows.add(row(recordNumber++, value));
    }
    return rows.build().toList();
  }

  private static ValidationIssueDraft issue(
      ExecutableValidationRule rule, long recordNumber, String observedValue) {
    ValidationDataType dataType =
        ((ValidationRuleConfiguration.DataType) rule.configuration()).type();
    return new ValidationIssueDraft(
        recordNumber,
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        "Value must match type " + dataType + ".",
        observedValue);
  }

  private static Stream<String> invalidIntegers() {
    return Stream.of("1.0", "1e3", "1E+3", "1,000", " 1", "1 ");
  }

  private static Stream<String> validDecimals() {
    return Stream.of("0", "+1", "-1.25", ".5", "5.", "1e3", "-1.2E-3");
  }

  private static Stream<String> invalidDecimals() {
    return Stream.of("1,000", "1,25", " 1.25", "1.25 ", "not-a-number");
  }
}
