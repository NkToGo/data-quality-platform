package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationDataType;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class DataTypeRuleExecutor implements ValidationRuleExecutor {

  @Override
  public ValidationRuleType ruleType() {
    return ValidationRuleType.DATA_TYPE;
  }

  @Override
  public List<ValidationIssueDraft> execute(
      ExecutableValidationRule rule, int columnIndex, List<ValidationInputRow> rows) {
    ValidationDataType dataType =
        ((ValidationRuleConfiguration.DataType) rule.configuration()).type();
    List<ValidationIssueDraft> issues = new ArrayList<>();

    for (ValidationInputRow row : rows) {
      String value = row.values().get(columnIndex);
      if (!value.isBlank() && !matches(value, dataType)) {
        issues.add(issue(rule, row, value, dataType));
      }
    }
    return List.copyOf(issues);
  }

  private boolean matches(String value, ValidationDataType dataType) {
    return switch (dataType) {
      case INTEGER -> parsesInteger(value);
      case DECIMAL -> parsesDecimal(value);
      case BOOLEAN -> value.equals("true") || value.equals("false");
      case STRING -> true;
    };
  }

  private boolean parsesInteger(String value) {
    try {
      new BigInteger(value);
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private boolean parsesDecimal(String value) {
    try {
      new BigDecimal(value);
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private ValidationIssueDraft issue(
      ExecutableValidationRule rule,
      ValidationInputRow row,
      String observedValue,
      ValidationDataType dataType) {
    return new ValidationIssueDraft(
        row.recordNumber(),
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        "Value must match type " + dataType + ".",
        observedValue);
  }
}
