package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.ingestion.ValidationRunAccess;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationIssueService {

  private final ValidationIssueRepository validationIssueRepository;
  private final ValidationRunAccess validationRunAccess;
  private final ValidationIssueWriter validationIssueWriter;

  ValidationIssueService(
      ValidationRunAccess validationRunAccess,
      ValidationIssueRepository validationIssueRepository,
      ValidationIssueWriter validationIssueWriter) {
    this.validationRunAccess = validationRunAccess;
    this.validationIssueRepository = validationIssueRepository;
    this.validationIssueWriter = validationIssueWriter;
  }

  @Transactional
  void persistAll(UUID runId, List<ValidationIssueDraft> drafts) {
    Objects.requireNonNull(runId, "runId must not be null");
    List<ValidationIssueDraft> copiedDrafts = List.copyOf(drafts);

    validationRunAccess.requireValidationRun(runId);
    validationIssueWriter.persistForExistingRun(runId, copiedDrafts);
  }

  @Transactional(readOnly = true)
  List<ValidationIssueResponse> getAll(UUID runId) {
    Objects.requireNonNull(runId, "runId must not be null");
    validationRunAccess.requireValidationRun(runId);

    return validationIssueRepository
        .findAllByRunIdOrderByRowNumberAscFieldNameAscRuleTypeAscIdAsc(runId)
        .stream()
        .map(ValidationIssueService::toResponse)
        .toList();
  }

  private static ValidationIssueResponse toResponse(ValidationIssue validationIssue) {
    return new ValidationIssueResponse(
        validationIssue.getId(),
        validationIssue.getRunId(),
        validationIssue.getRowNumber(),
        validationIssue.getFieldName(),
        validationIssue.getRuleType(),
        validationIssue.getSeverity(),
        validationIssue.getMessage(),
        validationIssue.getObservedValue());
  }
}
