package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ValidationIssueWriterTests {

  private final ValidationIssueRepository validationIssueRepository =
      mock(ValidationIssueRepository.class);
  private final ValidationIssueWriter writer = new ValidationIssueWriter(validationIssueRepository);

  @Test
  void mapsEveryDraftInSuppliedOrderAndFlushesTheBatch() {
    UUID runId = UUID.randomUUID();
    List<ValidationIssueDraft> drafts =
        List.of(
            draft(
                4,
                " amount ",
                ValidationRuleType.NUMERIC_RANGE,
                ValidationRuleSeverity.WARNING,
                "Value must be between 1 and 10, inclusive.",
                " 11 "),
            draft(
                2,
                "name",
                ValidationRuleType.REQUIRED_FIELD,
                ValidationRuleSeverity.ERROR,
                "Value is required.",
                ""));

    writer.persistForExistingRun(runId, drafts);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ValidationIssue>> issuesCaptor =
        ArgumentCaptor.forClass((Class<List<ValidationIssue>>) (Class<?>) List.class);
    verify(validationIssueRepository).saveAllAndFlush(issuesCaptor.capture());
    assertThat(issuesCaptor.getValue())
        .extracting(
            ValidationIssue::getRunId,
            ValidationIssue::getRowNumber,
            ValidationIssue::getFieldName,
            ValidationIssue::getRuleType,
            ValidationIssue::getSeverity,
            ValidationIssue::getMessage,
            ValidationIssue::getObservedValue)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                runId,
                4L,
                " amount ",
                ValidationRuleType.NUMERIC_RANGE,
                ValidationRuleSeverity.WARNING,
                "Value must be between 1 and 10, inclusive.",
                " 11 "),
            org.assertj.core.groups.Tuple.tuple(
                runId,
                2L,
                "name",
                ValidationRuleType.REQUIRED_FIELD,
                ValidationRuleSeverity.ERROR,
                "Value is required.",
                ""));
  }

  @Test
  void propagatesFlushFailure() {
    UUID runId = UUID.randomUUID();
    List<ValidationIssueDraft> drafts =
        List.of(
            draft(
                2,
                "name",
                ValidationRuleType.REQUIRED_FIELD,
                ValidationRuleSeverity.ERROR,
                "Value is required.",
                ""));
    IllegalStateException failure = new IllegalStateException("Flush failed.");
    org.mockito.Mockito.when(
            validationIssueRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.anyList()))
        .thenThrow(failure);

    assertThatThrownBy(() -> writer.persistForExistingRun(runId, drafts)).isSameAs(failure);
  }

  @Test
  void skipsRepositoryForAnEmptyBatch() {
    writer.persistForExistingRun(UUID.randomUUID(), List.of());

    verifyNoInteractions(validationIssueRepository);
  }

  @Test
  void rejectsNullArgumentsAndElementsBeforeRepositoryInteraction() {
    UUID runId = UUID.randomUUID();
    java.util.ArrayList<ValidationIssueDraft> withNull = new java.util.ArrayList<>();
    withNull.add(null);

    assertThatThrownBy(() -> writer.persistForExistingRun(null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> writer.persistForExistingRun(runId, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> writer.persistForExistingRun(runId, withNull))
        .isInstanceOf(NullPointerException.class);

    verifyNoInteractions(validationIssueRepository);
  }

  private ValidationIssueDraft draft(
      long rowNumber,
      String fieldName,
      ValidationRuleType ruleType,
      ValidationRuleSeverity severity,
      String message,
      String observedValue) {
    return new ValidationIssueDraft(
        rowNumber, fieldName, ruleType, severity, message, observedValue);
  }
}
