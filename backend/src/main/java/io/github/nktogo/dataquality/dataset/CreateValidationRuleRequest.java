package io.github.nktogo.dataquality.dataset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateValidationRuleRequest(
    @NotBlank @Size(max = 255) String fieldName,
    @NotNull ValidationRuleType ruleType,
    @NotNull @JsonDeserialize(using = ValidationRuleParametersDeserializer.class)
        Map<String, Object> parameters,
    @NotNull ValidationRuleSeverity severity,
    @NotNull Boolean enabled) {}
