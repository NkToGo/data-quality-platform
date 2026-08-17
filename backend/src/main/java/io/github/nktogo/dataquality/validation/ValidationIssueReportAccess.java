package io.github.nktogo.dataquality.validation;

import java.util.List;
import java.util.UUID;

public interface ValidationIssueReportAccess {

  List<ValidationIssueResponse> getValidationIssuesForReport(UUID runId);
}
