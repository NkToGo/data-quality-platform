package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRuleService {

  private final ValidationRuleRepository validationRuleRepository;
  private final ValidationProfileService validationProfileService;

  ValidationRuleService(
      ValidationRuleRepository validationRuleRepository,
      ValidationProfileService validationProfileService) {
    this.validationRuleRepository = validationRuleRepository;
    this.validationProfileService = validationProfileService;
  }

  @Transactional
  ValidationRuleResponse create(UUID profileId, CreateValidationRuleRequest request) {
    ValidationProfile profile = validationProfileService.requireExisting(profileId);
    ValidationRule validationRule =
        new ValidationRule(
            profile,
            request.fieldName(),
            request.ruleType(),
            request.parameters(),
            request.severity(),
            request.enabled());

    return toResponse(validationRuleRepository.save(validationRule));
  }

  @Transactional(readOnly = true)
  List<ValidationRuleResponse> getAll(UUID profileId) {
    ValidationProfile profile = validationProfileService.requireExisting(profileId);

    return validationRuleRepository.findAllByProfileOrderByIdAsc(profile).stream()
        .map(ValidationRuleService::toResponse)
        .toList();
  }

  private static ValidationRuleResponse toResponse(ValidationRule validationRule) {
    return new ValidationRuleResponse(
        validationRule.getId(),
        validationRule.getProfile().getId(),
        validationRule.getFieldName(),
        validationRule.getRuleType(),
        validationRule.getParameters(),
        validationRule.getSeverity(),
        validationRule.isEnabled());
  }
}
