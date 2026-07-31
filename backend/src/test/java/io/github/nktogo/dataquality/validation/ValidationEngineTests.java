package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidationEngineTests {

  private final ValidationEngine engine = engine();

  @Test
  void resolvesHeadersExactlyAndCaseSensitively() {
    ValidationInput input =
        new ValidationInput(
            List.of(" Name ", "name"), List.of(new ValidationInputRow(2, List.of("", "present"))));

    ValidationResult result =
        engine.validate(input, List.of(rule(1, " Name ", ValidationRuleSeverity.ERROR)));

    assertThat(result).isInstanceOf(ValidationResult.Success.class);
    ValidationResult.Success success = (ValidationResult.Success) result;
    assertThat(success.issues())
        .containsExactly(
            new ValidationIssueDraft(
                2,
                " Name ",
                ValidationRuleType.REQUIRED_FIELD,
                ValidationRuleSeverity.ERROR,
                "Value is required.",
                ""));
  }

  @Test
  void returnsStableMissingHeaderFailureWithoutPartialIssues() {
    ValidationInput input =
        new ValidationInput(List.of("name"), List.of(new ValidationInputRow(2, List.of(""))));

    ValidationResult result =
        engine.validate(
            input,
            List.of(
                rule(1, "name", ValidationRuleSeverity.ERROR),
                rule(2, "Name", ValidationRuleSeverity.ERROR)));

    assertThat(result).isEqualTo(new ValidationResult.MissingHeader());
    assertThat(((ValidationResult.MissingHeader) result).reason())
        .isEqualTo("CSV header does not contain a field required by the Validation Profile.");
  }

  @Test
  void ordersIssuesBySuppliedRuleThenLogicalRecord() {
    ValidationInput input =
        new ValidationInput(
            List.of("value"),
            List.of(
                new ValidationInputRow(2, List.of("duplicate")),
                new ValidationInputRow(3, List.of("")),
                new ValidationInputRow(4, List.of("duplicate"))));
    ExecutableValidationRule uniqueness =
        new ExecutableValidationRule(
            uuid(1),
            "value",
            ValidationRuleType.UNIQUENESS,
            new ValidationRuleConfiguration.Empty(),
            ValidationRuleSeverity.WARNING);
    ExecutableValidationRule required = rule(2, "value", ValidationRuleSeverity.ERROR);

    ValidationResult.Success result =
        (ValidationResult.Success) engine.validate(input, List.of(uniqueness, required));

    assertThat(result.issues())
        .extracting(ValidationIssueDraft::ruleType, ValidationIssueDraft::rowNumber)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(ValidationRuleType.UNIQUENESS, 2L),
            org.assertj.core.groups.Tuple.tuple(ValidationRuleType.UNIQUENESS, 4L),
            org.assertj.core.groups.Tuple.tuple(ValidationRuleType.REQUIRED_FIELD, 3L));
  }

  @Test
  void calculatesSummaryFromErrorAndWarningIssues() {
    ValidationInput input =
        new ValidationInput(
            List.of("required", "advisory"),
            List.of(
                new ValidationInputRow(2, List.of("", "")),
                new ValidationInputRow(3, List.of("present", "")),
                new ValidationInputRow(4, List.of("", "present"))));

    ValidationResult.Success result =
        (ValidationResult.Success)
            engine.validate(
                input,
                List.of(
                    rule(1, "required", ValidationRuleSeverity.ERROR),
                    rule(2, "advisory", ValidationRuleSeverity.WARNING)));

    assertThat(result.summary()).isEqualTo(new ValidationSummary(3, 1, 2, 4));
    assertThat(result.issues())
        .extracting(ValidationIssueDraft::fieldName, ValidationIssueDraft::rowNumber)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("required", 2L),
            org.assertj.core.groups.Tuple.tuple("required", 4L),
            org.assertj.core.groups.Tuple.tuple("advisory", 2L),
            org.assertj.core.groups.Tuple.tuple("advisory", 3L));
  }

  @Test
  void countsMultipleErrorsOnOneRowOnlyOnce() {
    ValidationInput input =
        new ValidationInput(
            List.of("first", "second"), List.of(new ValidationInputRow(2, List.of("", ""))));

    ValidationResult.Success result =
        (ValidationResult.Success)
            engine.validate(
                input,
                List.of(
                    rule(1, "first", ValidationRuleSeverity.ERROR),
                    rule(2, "second", ValidationRuleSeverity.ERROR)));

    assertThat(result.summary()).isEqualTo(new ValidationSummary(1, 0, 1, 2));
  }

  @Test
  void treatsEveryRowAsValidWhenThereAreNoRules() {
    ValidationInput input =
        new ValidationInput(
            List.of("name"),
            List.of(
                new ValidationInputRow(2, List.of("Alice")),
                new ValidationInputRow(3, List.of("Bob"))));

    ValidationResult.Success result = (ValidationResult.Success) engine.validate(input, List.of());

    assertThat(result.summary()).isEqualTo(new ValidationSummary(2, 2, 0, 0));
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void returnsAnAllZeroSummaryForHeaderOnlyInput() {
    ValidationResult.Success result =
        (ValidationResult.Success)
            engine.validate(
                new ValidationInput(List.of("name"), List.of()),
                List.of(rule(1, "name", ValidationRuleSeverity.ERROR)));

    assertThat(result.summary()).isEqualTo(new ValidationSummary(0, 0, 0, 0));
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void keepsDuplicateRulesSeparatelyObservable() {
    ValidationInput input =
        new ValidationInput(List.of("name"), List.of(new ValidationInputRow(2, List.of(""))));

    ValidationResult.Success result =
        (ValidationResult.Success)
            engine.validate(
                input,
                List.of(
                    rule(1, "name", ValidationRuleSeverity.ERROR),
                    rule(2, "name", ValidationRuleSeverity.ERROR)));

    assertThat(result.issues()).hasSize(2);
    assertThat(result.summary()).isEqualTo(new ValidationSummary(1, 0, 1, 2));
  }

  @Test
  void returnsImmutableIssuesAndStableResultsAcrossCalls() {
    ValidationInput input =
        new ValidationInput(List.of("name"), List.of(new ValidationInputRow(2, List.of(""))));
    List<ExecutableValidationRule> rules = List.of(rule(1, "name", ValidationRuleSeverity.ERROR));

    ValidationResult.Success first = (ValidationResult.Success) engine.validate(input, rules);
    ValidationResult.Success second = (ValidationResult.Success) engine.validate(input, rules);

    assertThat(first).isEqualTo(second);
    assertThatThrownBy(() -> first.issues().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsInconsistentSummaryAndSuccessIssueCount() {
    assertThatThrownBy(() -> new ValidationSummary(2, 2, 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ValidationResult.Success(new ValidationSummary(1, 1, 0, 1), new ArrayList<>()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ValidationEngine engine() {
    List<ValidationRuleExecutor> executors =
        List.of(
            new UniquenessRuleExecutor(),
            new RequiredFieldRuleExecutor(),
            new DateFormatRuleExecutor(),
            new NumericRangeRuleExecutor(),
            new DataTypeRuleExecutor());
    return new ValidationEngine(
        new ValidationRuleExecutorRegistry(executors), new ValidationColumnResolver());
  }

  private ExecutableValidationRule rule(
      int idSuffix, String fieldName, ValidationRuleSeverity severity) {
    return new ExecutableValidationRule(
        uuid(idSuffix),
        fieldName,
        ValidationRuleType.REQUIRED_FIELD,
        new ValidationRuleConfiguration.Empty(),
        severity);
  }

  private UUID uuid(int suffix) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
  }
}
