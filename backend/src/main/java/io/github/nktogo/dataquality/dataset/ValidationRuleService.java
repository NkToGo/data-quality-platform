package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRuleService implements ValidationRuleAccess {

  private final ValidationRuleRepository validationRuleRepository;
  private final ValidationProfileService validationProfileService;
  private final ValidationRuleParameterCodec validationRuleParameterCodec;

  ValidationRuleService(
      ValidationRuleRepository validationRuleRepository,
      ValidationProfileService validationProfileService,
      ValidationRuleParameterCodec validationRuleParameterCodec) {
    this.validationRuleRepository = validationRuleRepository;
    this.validationProfileService = validationProfileService;
    this.validationRuleParameterCodec = validationRuleParameterCodec;
  }

  @Transactional
  ValidationRuleResponse create(UUID profileId, CreateValidationRuleRequest request) {
    ValidationProfile profile = validationProfileService.requireExisting(profileId);
    Map<String, Object> parameters =
        validationRuleParameterCodec.canonicalize(request.ruleType(), request.parameters());
    ValidationRule validationRule =
        new ValidationRule(
            profile,
            request.fieldName(),
            request.ruleType(),
            parameters,
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

  @Override
  @Transactional(readOnly = true)
  public List<ExecutableValidationRule> getEnabledRules(UUID profileId) {
    ValidationProfile profile = validationProfileService.requireExisting(profileId);

    return validationRuleRepository.findEnabledExecutableRulesByProfileId(profile.getId()).stream()
        .map(this::toExecutableRule)
        .toList();
  }

  private ExecutableValidationRule toExecutableRule(
      ValidationRuleRepository.ExecutableValidationRuleRow validationRule) {
    ValidationRuleType ruleType = ValidationRuleType.valueOf(validationRule.getRuleType());
    return new ExecutableValidationRule(
        validationRule.getId(),
        validationRule.getFieldName(),
        ruleType,
        validationRuleParameterCodec.decodePersisted(ruleType, validationRule.getParametersJson()),
        ValidationRuleSeverity.valueOf(validationRule.getSeverity()));
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
