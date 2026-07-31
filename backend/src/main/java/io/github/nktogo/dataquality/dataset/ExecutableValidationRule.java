package io.github.nktogo.dataquality.dataset;

import java.util.Objects;
import java.util.UUID;

public record ExecutableValidationRule(
    UUID id,
    String fieldName,
    ValidationRuleType ruleType,
    ValidationRuleConfiguration configuration,
    ValidationRuleSeverity severity) {

  public ExecutableValidationRule {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(fieldName, "fieldName must not be null");
    Objects.requireNonNull(ruleType, "ruleType must not be null");
    Objects.requireNonNull(configuration, "configuration must not be null");
    Objects.requireNonNull(severity, "severity must not be null");

    Class<? extends ValidationRuleConfiguration> expectedConfigurationType =
        switch (ruleType) {
          case REQUIRED_FIELD, UNIQUENESS -> ValidationRuleConfiguration.Empty.class;
          case DATA_TYPE -> ValidationRuleConfiguration.DataType.class;
          case NUMERIC_RANGE -> ValidationRuleConfiguration.NumericRange.class;
          case DATE_FORMAT -> ValidationRuleConfiguration.DateFormat.class;
        };
    if (!expectedConfigurationType.isInstance(configuration)) {
      throw new IllegalArgumentException(
          ruleType
              + " requires configuration type "
              + expectedConfigurationType.getSimpleName()
              + ".");
    }
  }
}
