package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.UUID;

public record ValidationIssueResponse(
    UUID id,
    UUID runId,
    long rowNumber,
    String fieldName,
    ValidationRuleType ruleType,
    ValidationRuleSeverity severity,
    String message,
    String observedValue) {}
