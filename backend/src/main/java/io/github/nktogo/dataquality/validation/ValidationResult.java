package io.github.nktogo.dataquality.validation;

import java.util.List;
import java.util.Objects;

public sealed interface ValidationResult {

  record Success(ValidationSummary summary, List<ValidationIssueDraft> issues)
      implements ValidationResult {

    public Success {
      Objects.requireNonNull(summary, "summary must not be null");
      issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
      if (summary.issueCount() != issues.size()) {
        throw new IllegalArgumentException("summary issueCount must equal the number of issues");
      }
    }
  }

  record MissingHeader() implements ValidationResult {

    public String reason() {
      return "CSV header does not contain a field required by the Validation Profile.";
    }
  }
}
