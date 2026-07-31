package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequiredFieldRuleExecutorTests {

  private final RequiredFieldRuleExecutor executor = new RequiredFieldRuleExecutor();

  @Test
  void reportsEveryBlankValueInLogicalRecordOrder() {
    ExecutableValidationRule rule = rule(ValidationRuleSeverity.WARNING);
    List<ValidationInputRow> rows =
        List.of(
            row(2, ""),
            row(3, " "),
            row(4, "\t"),
            row(5, "\n"),
            row(6, "\u2003"),
            row(7, "present"));

    List<ValidationIssueDraft> issues = executor.execute(rule, 0, rows);

    assertThat(issues)
        .containsExactly(
            issue(rule, 2, ""),
            issue(rule, 3, " "),
            issue(rule, 4, "\t"),
            issue(rule, 5, "\n"),
            issue(rule, 6, "\u2003"));
  }

  @Test
  void preservesTheSelectedColumnAndObservedValue() {
    ExecutableValidationRule rule = rule(ValidationRuleSeverity.ERROR);
    ValidationInputRow row = new ValidationInputRow(12, List.of("ignored", "  "));

    assertThat(executor.execute(rule, 1, List.of(row))).containsExactly(issue(rule, 12, "  "));
  }

  @Test
  void acceptsEveryNonblankValueWithoutTrimming() {
    ExecutableValidationRule rule = rule(ValidationRuleSeverity.ERROR);

    assertThat(executor.execute(rule, 0, List.of(row(2, " value ")))).isEmpty();
  }

  private static ExecutableValidationRule rule(ValidationRuleSeverity severity) {
    return new ExecutableValidationRule(
        UUID.randomUUID(),
        "email",
        ValidationRuleType.REQUIRED_FIELD,
        new ValidationRuleConfiguration.Empty(),
        severity);
  }

  private static ValidationInputRow row(long recordNumber, String value) {
    return new ValidationInputRow(recordNumber, List.of(value));
  }

  private static ValidationIssueDraft issue(
      ExecutableValidationRule rule, long recordNumber, String observedValue) {
    return new ValidationIssueDraft(
        recordNumber,
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        "Value is required.",
        observedValue);
  }
}
