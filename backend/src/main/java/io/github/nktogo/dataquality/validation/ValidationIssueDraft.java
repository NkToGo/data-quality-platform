package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.Objects;

public record ValidationIssueDraft(
    long rowNumber,
    String fieldName,
    ValidationRuleType ruleType,
    ValidationRuleSeverity severity,
    String message,
    String observedValue) {

  public ValidationIssueDraft {
    if (rowNumber < 2) {
      throw new IllegalArgumentException("rowNumber must be at least 2");
    }
    Objects.requireNonNull(fieldName, "fieldName must not be null");
    Objects.requireNonNull(ruleType, "ruleType must not be null");
    Objects.requireNonNull(severity, "severity must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(observedValue, "observedValue must not be null");
  }
}
