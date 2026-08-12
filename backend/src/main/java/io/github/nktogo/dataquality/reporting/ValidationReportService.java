package io.github.nktogo.dataquality.reporting;

import io.github.nktogo.dataquality.ingestion.ValidationRunReportAccess;
import io.github.nktogo.dataquality.ingestion.ValidationRunResponse;
import io.github.nktogo.dataquality.validation.ValidationIssueReportAccess;
import io.github.nktogo.dataquality.validation.ValidationIssueResponse;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationReportService {

  private final ValidationRunReportAccess validationRunReportAccess;
  private final ValidationIssueReportAccess validationIssueReportAccess;

  ValidationReportService(
      ValidationRunReportAccess validationRunReportAccess,
      ValidationIssueReportAccess validationIssueReportAccess) {
    this.validationRunReportAccess = validationRunReportAccess;
    this.validationIssueReportAccess = validationIssueReportAccess;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  ValidationReport getReport(UUID runId) {
    Objects.requireNonNull(runId, "runId must not be null");
    ValidationRunResponse validationRun =
        validationRunReportAccess.getValidationRunForReport(runId);
    List<ValidationIssueResponse> issues =
        validationIssueReportAccess.getValidationIssuesForReport(runId);

    return new ValidationReport(validationRun, issues);
  }
}
