package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ValidationEngine {

  private final ValidationRuleExecutorRegistry executorRegistry;
  private final ValidationColumnResolver columnResolver;

  ValidationEngine(
      ValidationRuleExecutorRegistry executorRegistry, ValidationColumnResolver columnResolver) {
    this.executorRegistry = executorRegistry;
    this.columnResolver = columnResolver;
  }

  public ValidationResult validate(
      ValidationInput input, List<ExecutableValidationRule> enabledRules) {
    Objects.requireNonNull(input, "input must not be null");
    List<ExecutableValidationRule> rules =
        List.copyOf(Objects.requireNonNull(enabledRules, "enabledRules must not be null"));

    var resolvedIndexes = columnResolver.resolve(input.headers(), rules);
    if (resolvedIndexes.isEmpty()) {
      return new ValidationResult.MissingHeader();
    }

    List<ValidationIssueDraft> issues = new ArrayList<>();
    List<Integer> columnIndexes = resolvedIndexes.orElseThrow();
    for (int index = 0; index < rules.size(); index++) {
      ExecutableValidationRule rule = rules.get(index);
      issues.addAll(
          executorRegistry
              .executorFor(rule.ruleType())
              .execute(rule, columnIndexes.get(index), input.rows()));
    }

    Set<Long> invalidRowNumbers = new HashSet<>();
    for (ValidationIssueDraft issue : issues) {
      if (issue.severity() == ValidationRuleSeverity.ERROR) {
        invalidRowNumbers.add(issue.rowNumber());
      }
    }

    long totalRows = input.rows().size();
    long invalidRows = invalidRowNumbers.size();
    ValidationSummary summary =
        new ValidationSummary(totalRows, totalRows - invalidRows, invalidRows, issues.size());
    return new ValidationResult.Success(summary, issues);
  }
}
