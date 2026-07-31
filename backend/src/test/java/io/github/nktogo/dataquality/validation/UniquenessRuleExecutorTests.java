package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UniquenessRuleExecutorTests {

  private final UniquenessRuleExecutor executor = new UniquenessRuleExecutor();

  @Test
  void reportsEveryOccurrenceInEveryDuplicatedGroup() {
    ExecutableValidationRule rule = rule();
    List<ValidationInputRow> rows = rows("alpha", "beta", "alpha", "alpha", "beta", "unique");

    assertThat(executor.execute(rule, 0, rows))
        .containsExactly(
            issue(rule, 2, "alpha"),
            issue(rule, 3, "beta"),
            issue(rule, 4, "alpha"),
            issue(rule, 5, "alpha"),
            issue(rule, 6, "beta"));
  }

  @Test
  void ignoresBlanksAndComparesCaseAndWhitespaceExactly() {
    ExecutableValidationRule rule = rule();

    assertThat(executor.execute(rule, 0, rows("", " ", "\t", "value", "Value", "value ", "value")))
        .containsExactly(issue(rule, 5, "value"), issue(rule, 8, "value"));
  }

  @Test
  void keepsInvocationStateLocalToEachCall() {
    ExecutableValidationRule rule = rule();

    assertThat(executor.execute(rule, 0, rows("same", "same")))
        .containsExactly(issue(rule, 2, "same"), issue(rule, 3, "same"));
    assertThat(executor.execute(rule, 0, rows("same", "different"))).isEmpty();
  }

  @Test
  void evaluatesOnlyTheSelectedColumn() {
    ExecutableValidationRule rule = rule();
    List<ValidationInputRow> rows =
        List.of(
            new ValidationInputRow(2, List.of("same", "first")),
            new ValidationInputRow(3, List.of("same", "second")));

    assertThat(executor.execute(rule, 1, rows)).isEmpty();
  }

  private static ExecutableValidationRule rule() {
    return new ExecutableValidationRule(
        UUID.randomUUID(),
        "code",
        ValidationRuleType.UNIQUENESS,
        new ValidationRuleConfiguration.Empty(),
        ValidationRuleSeverity.WARNING);
  }

  private static List<ValidationInputRow> rows(String... values) {
    List<ValidationInputRow> rows = new ArrayList<>();
    for (int index = 0; index < values.length; index++) {
      rows.add(new ValidationInputRow(index + 2L, List.of(values[index])));
    }
    return List.copyOf(rows);
  }

  private static ValidationIssueDraft issue(
      ExecutableValidationRule rule, long recordNumber, String observedValue) {
    return new ValidationIssueDraft(
        recordNumber,
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        "Value must be unique.",
        observedValue);
  }
}
