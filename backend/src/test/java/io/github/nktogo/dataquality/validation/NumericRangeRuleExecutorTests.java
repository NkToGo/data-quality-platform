package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NumericRangeRuleExecutorTests {

  private final NumericRangeRuleExecutor executor = new NumericRangeRuleExecutor();

  @Test
  void minimumBoundIsInclusiveAndUsesCanonicalMessage() {
    ExecutableValidationRule rule = rule(new BigDecimal("1.000"), null);

    assertThat(executor.execute(rule, 0, rows("1", "1.0", "0.999")))
        .containsExactly(issue(rule, 4, "0.999", "Value must be greater than or equal to 1."));
  }

  @Test
  void maximumBoundIsInclusiveAndUsesCanonicalMessage() {
    ExecutableValidationRule rule = rule(null, new BigDecimal("1E+3"));

    assertThat(executor.execute(rule, 0, rows("999", "1000", "1.001E+3")))
        .containsExactly(issue(rule, 4, "1.001E+3", "Value must be less than or equal to 1000."));
  }

  @Test
  void bothBoundsAreInclusiveAndUseCanonicalMessage() {
    ExecutableValidationRule rule = rule(new BigDecimal("1.2500"), new BigDecimal("10.000"));

    assertThat(executor.execute(rule, 0, rows("1.25", "10", "1.249", "10.001")))
        .containsExactly(
            issue(rule, 4, "1.249", "Value must be between 1.25 and 10, inclusive."),
            issue(rule, 5, "10.001", "Value must be between 1.25 and 10, inclusive."));
  }

  @Test
  void reportsNonnumericValuesWithTheDedicatedMessage() {
    ExecutableValidationRule rule = rule(BigDecimal.ZERO, null);

    assertThat(executor.execute(rule, 0, rows("not numeric", "1,25", " 1")))
        .containsExactly(
            issue(
                rule, 2, "not numeric", "Value must be numeric to evaluate the configured range."),
            issue(rule, 3, "1,25", "Value must be numeric to evaluate the configured range."),
            issue(rule, 4, " 1", "Value must be numeric to evaluate the configured range."));
  }

  @Test
  void supportsDecimalAndExponentNotationIndependentlyOfLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.FRANCE);
      ExecutableValidationRule rule = rule(new BigDecimal("-1E+3"), new BigDecimal("1E+3"));

      assertThat(executor.execute(rule, 0, rows("-1e3", "1E+3", "1.1E+3")))
          .containsExactly(
              issue(rule, 4, "1.1E+3", "Value must be between -1000 and 1000, inclusive."));
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void skipsEveryBlankValue() {
    ExecutableValidationRule rule = rule(BigDecimal.ZERO, null);

    assertThat(executor.execute(rule, 0, rows("", " ", "\t", "\n", "\u2003"))).isEmpty();
  }

  private static ExecutableValidationRule rule(BigDecimal minimum, BigDecimal maximum) {
    return new ExecutableValidationRule(
        UUID.randomUUID(),
        "amount",
        ValidationRuleType.NUMERIC_RANGE,
        new ValidationRuleConfiguration.NumericRange(minimum, maximum),
        ValidationRuleSeverity.WARNING);
  }

  private static List<ValidationInputRow> rows(String... values) {
    java.util.ArrayList<ValidationInputRow> rows = new java.util.ArrayList<>();
    for (int index = 0; index < values.length; index++) {
      rows.add(new ValidationInputRow(index + 2L, List.of(values[index])));
    }
    return List.copyOf(rows);
  }

  private static ValidationIssueDraft issue(
      ExecutableValidationRule rule, long recordNumber, String observedValue, String message) {
    return new ValidationIssueDraft(
        recordNumber, rule.fieldName(), rule.ruleType(), rule.severity(), message, observedValue);
  }
}
