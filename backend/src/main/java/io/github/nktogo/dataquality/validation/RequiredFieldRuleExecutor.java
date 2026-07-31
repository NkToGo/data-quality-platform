package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class RequiredFieldRuleExecutor implements ValidationRuleExecutor {

  private static final String MESSAGE = "Value is required.";

  @Override
  public ValidationRuleType ruleType() {
    return ValidationRuleType.REQUIRED_FIELD;
  }

  @Override
  public List<ValidationIssueDraft> execute(
      ExecutableValidationRule rule, int columnIndex, List<ValidationInputRow> rows) {
    List<ValidationIssueDraft> issues = new ArrayList<>();
    for (ValidationInputRow row : rows) {
      String value = row.values().get(columnIndex);
      if (value.isBlank()) {
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
