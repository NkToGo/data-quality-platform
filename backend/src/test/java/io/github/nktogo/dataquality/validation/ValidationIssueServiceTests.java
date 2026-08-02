package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import io.github.nktogo.dataquality.ingestion.ValidationRunAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ValidationIssueServiceTests {

  private final ValidationRunAccess validationRunAccess = mock(ValidationRunAccess.class);
  private final ValidationIssueRepository validationIssueRepository =
      mock(ValidationIssueRepository.class);
  private final ValidationIssueWriter validationIssueWriter = mock(ValidationIssueWriter.class);
  private final ValidationIssueService validationIssueService =
      new ValidationIssueService(
          validationRunAccess, validationIssueRepository, validationIssueWriter);

  @Test
  void requiresTheRunBeforeDelegatingDraftsInSuppliedOrder() {
    UUID runId = UUID.randomUUID();
    List<ValidationIssueDraft> drafts =
        List.of(
            draft(
                4,
                "second",
                ValidationRuleType.DATA_TYPE,
                ValidationRuleSeverity.WARNING,
                "Value must match type INTEGER.",
                "not-an-integer"),
            draft(
                2,
                "first",
                ValidationRuleType.REQUIRED_FIELD,
                ValidationRuleSeverity.ERROR,
                "Value is required.",
                ""));

    validationIssueService.persistAll(runId, drafts);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ValidationIssueDraft>> draftsCaptor =
        ArgumentCaptor.forClass((Class<List<ValidationIssueDraft>>) (Class<?>) List.class);
    InOrder calls = inOrder(validationRunAccess, validationIssueWriter);
    calls.verify(validationRunAccess).requireValidationRun(runId);
    calls.verify(validationIssueWriter).persistForExistingRun(eq(runId), draftsCaptor.capture());
    assertThat(draftsCaptor.getValue()).containsExactlyElementsOf(drafts);
  }

  @Test
  void preservesEmptyWhitespaceAndUnicodeObservedValues() {
    UUID runId = UUID.randomUUID();
    List<ValidationIssueDraft> drafts =
        List.of(
            draft(2, "empty", ValidationRuleType.REQUIRED_FIELD, ValidationRuleSeverity.ERROR, ""),
            draft(
                3, "spaces", ValidationRuleType.UNIQUENESS, ValidationRuleSeverity.WARNING, " \t "),
            draft(
                4,
                "unicode",
                ValidationRuleType.DATE_FORMAT,
                ValidationRuleSeverity.ERROR,
                "Grüße 東京"));

    validationIssueService.persistAll(runId, drafts);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ValidationIssueDraft>> draftsCaptor =
        ArgumentCaptor.forClass((Class<List<ValidationIssueDraft>>) (Class<?>) List.class);
    verify(validationIssueWriter).persistForExistingRun(eq(runId), draftsCaptor.capture());
    assertThat(draftsCaptor.getValue())
        .extracting(ValidationIssueDraft::observedValue)
        .containsExactly("", " \t ", "Grüße 東京");
  }

  @Test
  void defensivelyCopiesTheDraftListBeforeCheckingTheRun() {
    UUID runId = UUID.randomUUID();
    List<ValidationIssueDraft> drafts =
        new ArrayList<>(
            List.of(
                draft(
                    2,
                    "name",
                    ValidationRuleType.REQUIRED_FIELD,
                    ValidationRuleSeverity.ERROR,
                    "")));

    doAnswer(
            invocation -> {
              drafts.clear();
              return null;
            })
        .when(validationRunAccess)
        .requireValidationRun(runId);

    validationIssueService.persistAll(runId, drafts);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ValidationIssueDraft>> draftsCaptor =
        ArgumentCaptor.forClass((Class<List<ValidationIssueDraft>>) (Class<?>) List.class);
    verify(validationIssueWriter).persistForExistingRun(eq(runId), draftsCaptor.capture());
    assertThat(draftsCaptor.getValue()).hasSize(1);
  }

  @Test
  void doesNotWriteWhenTheRunDoesNotExist() {
    UUID runId = UUID.randomUUID();
    IllegalStateException notFound = new IllegalStateException("run not found");
    doThrow(notFound).when(validationRunAccess).requireValidationRun(runId);

    assertThatThrownBy(
            () ->
                validationIssueService.persistAll(
                    runId,
                    List.of(
                        draft(
                            2,
                            "name",
                            ValidationRuleType.REQUIRED_FIELD,
                            ValidationRuleSeverity.ERROR,
                            ""))))
        .isSameAs(notFound);

    verifyNoInteractions(validationIssueRepository, validationIssueWriter);
  }

  @Test
  void anEmptyBatchStillRequiresTheRunAndDelegatesNoIssues() {
    UUID runId = UUID.randomUUID();

    validationIssueService.persistAll(runId, List.of());

    InOrder calls = inOrder(validationRunAccess, validationIssueWriter);
    calls.verify(validationRunAccess).requireValidationRun(runId);
    calls.verify(validationIssueWriter).persistForExistingRun(runId, List.of());
    verifyNoInteractions(validationIssueRepository);
  }

  @Test
  void rejectsInvalidArgumentsBeforeCrossingAnyBoundary() {
    UUID runId = UUID.randomUUID();
    List<ValidationIssueDraft> withNull = new ArrayList<>();
    withNull.add(null);

    assertThatThrownBy(() -> validationIssueService.persistAll(null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> validationIssueService.persistAll(runId, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> validationIssueService.persistAll(runId, withNull))
        .isInstanceOf(NullPointerException.class);

    verifyNoInteractions(validationRunAccess, validationIssueRepository, validationIssueWriter);
  }

  @Test
  void requiresTheRunBeforeReadingAndReturnsAnImmutableMappedList() {
    UUID runId = UUID.randomUUID();
    ValidationIssue laterIssue =
        new ValidationIssue(
            runId,
            draft(
                4,
                "second",
                ValidationRuleType.DATA_TYPE,
                ValidationRuleSeverity.WARNING,
                "Value must match type INTEGER.",
                "not-an-integer"));
    ValidationIssue earlierIssue =
        new ValidationIssue(
            runId,
            draft(
                2,
                "first",
                ValidationRuleType.REQUIRED_FIELD,
                ValidationRuleSeverity.ERROR,
                "Value is required.",
                ""));
    when(validationIssueRepository.findAllByRunIdOrderByRowNumberAscFieldNameAscRuleTypeAscIdAsc(
            runId))
        .thenReturn(List.of(earlierIssue, laterIssue));

    List<ValidationIssueResponse> responses = validationIssueService.getAll(runId);

    InOrder calls = inOrder(validationRunAccess, validationIssueRepository);
    calls.verify(validationRunAccess).requireValidationRun(runId);
    calls
        .verify(validationIssueRepository)
        .findAllByRunIdOrderByRowNumberAscFieldNameAscRuleTypeAscIdAsc(runId);
    assertThat(responses)
        .extracting(
            ValidationIssueResponse::rowNumber,
            ValidationIssueResponse::fieldName,
            ValidationIssueResponse::observedValue)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(2L, "first", ""),
            org.assertj.core.groups.Tuple.tuple(4L, "second", "not-an-integer"));
    assertThatThrownBy(() -> responses.add(responses.getFirst()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private ValidationIssueDraft draft(
      long rowNumber,
      String fieldName,
      ValidationRuleType ruleType,
      ValidationRuleSeverity severity,
      String observedValue) {
    return draft(
        rowNumber,
        fieldName,
        ruleType,
        severity,
        "A deterministic validation message.",
        observedValue);
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
