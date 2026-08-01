package io.github.nktogo.dataquality.validation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class ValidationIssueWriter {

  private final ValidationIssueRepository validationIssueRepository;

  ValidationIssueWriter(ValidationIssueRepository validationIssueRepository) {
    this.validationIssueRepository = validationIssueRepository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  void persistForExistingRun(UUID runId, List<ValidationIssueDraft> drafts) {
    Objects.requireNonNull(runId, "runId must not be null");
    List<ValidationIssueDraft> copiedDrafts = List.copyOf(drafts);
    if (copiedDrafts.isEmpty()) {
      return;
    }

    validationIssueRepository.saveAllAndFlush(
        copiedDrafts.stream().map(draft -> new ValidationIssue(runId, draft)).toList());
  }
}
