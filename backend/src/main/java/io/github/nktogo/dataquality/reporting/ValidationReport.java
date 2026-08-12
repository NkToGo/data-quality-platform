package io.github.nktogo.dataquality.reporting;

import io.github.nktogo.dataquality.ingestion.ValidationRunResponse;
import io.github.nktogo.dataquality.validation.ValidationIssueResponse;
import java.util.List;
import java.util.Objects;

public record ValidationReport(
    ValidationRunResponse validationRun, List<ValidationIssueResponse> issues) {

  public ValidationReport {
    Objects.requireNonNull(validationRun, "validationRun must not be null");
    issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
  }
}
