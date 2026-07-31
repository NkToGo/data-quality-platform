package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class NumericRangeRuleExecutor implements ValidationRuleExecutor {

  private static final String NONNUMERIC_MESSAGE =
      "Value must be numeric to evaluate the configured range.";

  @Override
  public ValidationRuleType ruleType() {
    return ValidationRuleType.NUMERIC_RANGE;
  }

  @Override
  public List<ValidationIssueDraft> execute(
      ExecutableValidationRule rule, int columnIndex, List<ValidationInputRow> rows) {
    ValidationRuleConfiguration.NumericRange configuration =
        (ValidationRuleConfiguration.NumericRange) rule.configuration();
    List<ValidationIssueDraft> issues = new ArrayList<>();

    for (ValidationInputRow row : rows) {
      String value = row.values().get(columnIndex);
      if (value.isBlank()) {
        continue;
      }

      BigDecimal numericValue;
      try {
        numericValue = new BigDecimal(value);
      } catch (NumberFormatException exception) {
        issues.add(issue(rule, row, value, NONNUMERIC_MESSAGE));
        continue;
      }

      if (isOutsideRange(numericValue, configuration)) {
        issues.add(issue(rule, row, value, rangeMessage(configuration)));
      }
    }
    return List.copyOf(issues);
  }

  private boolean isOutsideRange(
      BigDecimal value, ValidationRuleConfiguration.NumericRange configuration) {
    return configuration.minimum() != null && value.compareTo(configuration.minimum()) < 0
        || configuration.maximum() != null && value.compareTo(configuration.maximum()) > 0;
  }

  private String rangeMessage(ValidationRuleConfiguration.NumericRange configuration) {
    if (configuration.minimum() != null && configuration.maximum() != null) {
      return "Value must be between "
          + canonical(configuration.minimum())
          + " and "
          + canonical(configuration.maximum())
          + ", inclusive.";
    }
    if (configuration.minimum() != null) {
      return "Value must be greater than or equal to " + canonical(configuration.minimum()) + ".";
    }
    return "Value must be less than or equal to " + canonical(configuration.maximum()) + ".";
  }

  private String canonical(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private ValidationIssueDraft issue(
      ExecutableValidationRule rule, ValidationInputRow row, String observedValue, String message) {
    return new ValidationIssueDraft(
        row.recordNumber(),
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        message,
        observedValue);
  }
}
