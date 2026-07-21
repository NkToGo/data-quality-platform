package io.github.nktogo.dataquality.dataset;

import java.util.Map;
import java.util.UUID;

public record ValidationRuleResponse(
    UUID id,
    UUID profileId,
    String fieldName,
    ValidationRuleType ruleType,
    Map<String, Object> parameters,
    ValidationRuleSeverity severity,
    boolean enabled) {}
