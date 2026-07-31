package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationRuleExecutorRegistryTests {

  @Test
  void selectsEveryExecutorByItsClaimedRuleTypeRegardlessOfInputOrder() {
    List<ValidationRuleExecutor> executors = completeExecutors();
    Collections.reverse(executors);

    ValidationRuleExecutorRegistry registry = new ValidationRuleExecutorRegistry(executors);

    for (ValidationRuleExecutor executor : executors) {
      assertThat(registry.executorFor(executor.ruleType())).isSameAs(executor);
    }
  }

  @Test
  void rejectsAMissingExecutor() {
    List<ValidationRuleExecutor> executors = completeExecutors();
    executors.removeIf(executor -> executor.ruleType() == ValidationRuleType.DATE_FORMAT);

    assertThatThrownBy(() -> new ValidationRuleExecutorRegistry(executors))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No Validation Rule executor is registered for DATE_FORMAT.");
  }

  @Test
  void rejectsDuplicateExecutorClaims() {
    List<ValidationRuleExecutor> executors = completeExecutors();
    executors.add(new StubExecutor(ValidationRuleType.REQUIRED_FIELD));

    assertThatThrownBy(() -> new ValidationRuleExecutorRegistry(executors))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Multiple Validation Rule executors are registered for REQUIRED_FIELD.");
  }

  @Test
  void rejectsNullExecutorTypesAndLookups() {
    List<ValidationRuleExecutor> executors = completeExecutors();
    executors.set(0, new StubExecutor(null));

    assertThatThrownBy(() -> new ValidationRuleExecutorRegistry(executors))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("executor ruleType must not be null");

    ValidationRuleExecutorRegistry registry =
        new ValidationRuleExecutorRegistry(completeExecutors());
    assertThatThrownBy(() -> registry.executorFor(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("ruleType must not be null");
  }

  private static List<ValidationRuleExecutor> completeExecutors() {
    return new ArrayList<>(
        List.of(
            new RequiredFieldRuleExecutor(),
            new DataTypeRuleExecutor(),
            new UniquenessRuleExecutor(),
            new NumericRangeRuleExecutor(),
            new DateFormatRuleExecutor()));
  }

  private record StubExecutor(ValidationRuleType ruleType) implements ValidationRuleExecutor {

    @Override
    public List<ValidationIssueDraft> execute(
        ExecutableValidationRule rule, int columnIndex, List<ValidationInputRow> rows) {
      return List.of();
    }
  }
}
