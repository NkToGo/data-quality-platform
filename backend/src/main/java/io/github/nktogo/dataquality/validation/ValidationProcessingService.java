package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleAccess;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationProcessingService implements ValidationProcessingAccess {

  private final ValidationRuleAccess validationRuleAccess;
  private final ValidationEngine validationEngine;
  private final ValidationIssueWriter validationIssueWriter;

  ValidationProcessingService(
      ValidationRuleAccess validationRuleAccess,
      ValidationEngine validationEngine,
      ValidationIssueWriter validationIssueWriter) {
    this.validationRuleAccess = validationRuleAccess;
    this.validationEngine = validationEngine;
    this.validationIssueWriter = validationIssueWriter;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ValidationResult validateAndPersist(UUID runId, UUID profileId, ValidationInput input) {
    Objects.requireNonNull(runId, "runId must not be null");
    Objects.requireNonNull(profileId, "profileId must not be null");
    Objects.requireNonNull(input, "input must not be null");

    List<ExecutableValidationRule> enabledRules = validationRuleAccess.getEnabledRules(profileId);
    ValidationResult result = validationEngine.validate(input, enabledRules);
    if (result instanceof ValidationResult.Success success) {
      validationIssueWriter.persistForExistingRun(runId, success.issues());
    }

    return result;
  }
}
