package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class UniquenessRuleExecutor implements ValidationRuleExecutor {

  private static final String MESSAGE = "Value must be unique.";

  @Override
  public ValidationRuleType ruleType() {
    return ValidationRuleType.UNIQUENESS;
  }

  @Override
  public List<ValidationIssueDraft> execute(
      ExecutableValidationRule rule, int columnIndex, List<ValidationInputRow> rows) {
    Map<String, Long> occurrences = new HashMap<>();
    for (ValidationInputRow row : rows) {
      String value = row.values().get(columnIndex);
      if (!value.isBlank()) {
        occurrences.merge(value, 1L, Long::sum);
      }
    }

    List<ValidationIssueDraft> issues = new ArrayList<>();
    for (ValidationInputRow row : rows) {
      String value = row.values().get(columnIndex);
      if (!value.isBlank() && occurrences.get(value) > 1) {
        issues.add(issue(rule, row, value));
      }
    }
    return List.copyOf(issues);
  }

  private ValidationIssueDraft issue(
      ExecutableValidationRule rule, ValidationInputRow row, String observedValue) {
    return new ValidationIssueDraft(
        row.recordNumber(),
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        MESSAGE,
        observedValue);
  }
}
