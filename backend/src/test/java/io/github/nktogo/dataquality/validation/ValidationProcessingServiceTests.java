package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleAccess;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ValidationProcessingServiceTests {

  private final ValidationRuleAccess validationRuleAccess = mock(ValidationRuleAccess.class);
  private final ValidationEngine validationEngine = mock(ValidationEngine.class);
  private final ValidationIssueWriter validationIssueWriter = mock(ValidationIssueWriter.class);
  private final ValidationProcessingService service =
      new ValidationProcessingService(
          validationRuleAccess, validationEngine, validationIssueWriter);

  @Test
  void loadsRulesValidatesAndPersistsSuccessfulIssuesInOrder() {
    UUID runId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    ValidationInput input = input();
    List<ExecutableValidationRule> rules = List.of(requiredRule());
    List<ValidationIssueDraft> issues = List.of(requiredIssue());
    ValidationResult.Success success =
        new ValidationResult.Success(new ValidationSummary(1, 0, 1, 1), issues);
    when(validationRuleAccess.getEnabledRules(profileId)).thenReturn(rules);
    when(validationEngine.validate(input, rules)).thenReturn(success);

    ValidationResult result = service.validateAndPersist(runId, profileId, input);

    assertThat(result).isSameAs(success);
    InOrder calls = inOrder(validationRuleAccess, validationEngine, validationIssueWriter);
    calls.verify(validationRuleAccess).getEnabledRules(profileId);
    calls.verify(validationEngine).validate(input, rules);
    calls.verify(validationIssueWriter).persistForExistingRun(runId, issues);
  }

  @Test
  void returnsMissingHeaderWithoutPersistingIssues() {
    UUID runId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    ValidationInput input = input();
    List<ExecutableValidationRule> rules = List.of(requiredRule());
    ValidationResult.MissingHeader missingHeader = new ValidationResult.MissingHeader();
    when(validationRuleAccess.getEnabledRules(profileId)).thenReturn(rules);
    when(validationEngine.validate(input, rules)).thenReturn(missingHeader);

    ValidationResult result = service.validateAndPersist(runId, profileId, input);

    assertThat(result).isSameAs(missingHeader);
    verify(validationIssueWriter, never()).persistForExistingRun(runId, List.of());
    verifyNoInteractions(validationIssueWriter);
  }

  @Test
  void passesAnEmptySuccessfulIssueListToTheWriter() {
    UUID runId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    ValidationInput input = input();
    List<ExecutableValidationRule> rules = List.of();
    ValidationResult.Success success =
        new ValidationResult.Success(new ValidationSummary(1, 1, 0, 0), List.of());
    when(validationRuleAccess.getEnabledRules(profileId)).thenReturn(rules);
    when(validationEngine.validate(input, rules)).thenReturn(success);

    assertThat(service.validateAndPersist(runId, profileId, input)).isSameAs(success);

    verify(validationIssueWriter).persistForExistingRun(runId, List.of());
  }

  @Test
  void propagatesRuleLoadingFailureWithoutInvokingEngineOrWriter() {
    UUID profileId = UUID.randomUUID();
    IllegalStateException failure = new IllegalStateException("Rule loading failed.");
    when(validationRuleAccess.getEnabledRules(profileId)).thenThrow(failure);

    assertThatThrownBy(() -> service.validateAndPersist(UUID.randomUUID(), profileId, input()))
        .isSameAs(failure);

    verifyNoInteractions(validationEngine, validationIssueWriter);
  }

  @Test
  void propagatesEngineFailureWithoutInvokingWriter() {
    UUID profileId = UUID.randomUUID();
    ValidationInput input = input();
    List<ExecutableValidationRule> rules = List.of(requiredRule());
    IllegalStateException failure = new IllegalStateException("Validation failed.");
    when(validationRuleAccess.getEnabledRules(profileId)).thenReturn(rules);
    when(validationEngine.validate(input, rules)).thenThrow(failure);

    assertThatThrownBy(() -> service.validateAndPersist(UUID.randomUUID(), profileId, input))
        .isSameAs(failure);

    verifyNoInteractions(validationIssueWriter);
  }

  @Test
  void propagatesIssuePersistenceFailure() {
    UUID runId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    ValidationInput input = input();
    List<ExecutableValidationRule> rules = List.of(requiredRule());
    List<ValidationIssueDraft> issues = List.of(requiredIssue());
    ValidationResult.Success success =
        new ValidationResult.Success(new ValidationSummary(1, 0, 1, 1), issues);
    IllegalStateException failure = new IllegalStateException("Issue persistence failed.");
    when(validationRuleAccess.getEnabledRules(profileId)).thenReturn(rules);
    when(validationEngine.validate(input, rules)).thenReturn(success);
    org.mockito.Mockito.doThrow(failure)
        .when(validationIssueWriter)
        .persistForExistingRun(runId, issues);

    assertThatThrownBy(() -> service.validateAndPersist(runId, profileId, input)).isSameAs(failure);
  }

  @Test
  void rejectsNullArgumentsBeforeCrossingAnyBoundary() {
    UUID runId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    ValidationInput input = input();

    assertThatThrownBy(() -> service.validateAndPersist(null, profileId, input))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.validateAndPersist(runId, null, input))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.validateAndPersist(runId, profileId, null))
        .isInstanceOf(NullPointerException.class);

    verifyNoInteractions(validationRuleAccess, validationEngine, validationIssueWriter);
  }

  private ValidationInput input() {
    return new ValidationInput(List.of("name"), List.of(new ValidationInputRow(2, List.of(""))));
  }

  private ExecutableValidationRule requiredRule() {
    return new ExecutableValidationRule(
        UUID.randomUUID(),
        "name",
        ValidationRuleType.REQUIRED_FIELD,
        new ValidationRuleConfiguration.Empty(),
        ValidationRuleSeverity.ERROR);
  }

  private ValidationIssueDraft requiredIssue() {
    return new ValidationIssueDraft(
        2,
        "name",
        ValidationRuleType.REQUIRED_FIELD,
        ValidationRuleSeverity.ERROR,
        "Value is required.",
        "");
  }
}
