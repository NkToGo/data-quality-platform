package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
final class ValidationRuleExecutorRegistry {

  private final Map<ValidationRuleType, ValidationRuleExecutor> executors;

  ValidationRuleExecutorRegistry(List<ValidationRuleExecutor> executors) {
    Objects.requireNonNull(executors, "executors must not be null");

    EnumMap<ValidationRuleType, ValidationRuleExecutor> executorsByType =
        new EnumMap<>(ValidationRuleType.class);
    for (ValidationRuleExecutor executor : executors) {
      Objects.requireNonNull(executor, "executor must not be null");
      ValidationRuleType ruleType =
          Objects.requireNonNull(executor.ruleType(), "executor ruleType must not be null");
      ValidationRuleExecutor existing = executorsByType.putIfAbsent(ruleType, executor);
      if (existing != null) {
        throw new IllegalStateException(
            "Multiple Validation Rule executors are registered for " + ruleType + ".");
      }
    }

    for (ValidationRuleType ruleType : ValidationRuleType.values()) {
      if (!executorsByType.containsKey(ruleType)) {
        throw new IllegalStateException(
            "No Validation Rule executor is registered for " + ruleType + ".");
      }
    }

    this.executors = Map.copyOf(executorsByType);
  }

  ValidationRuleExecutor executorFor(ValidationRuleType ruleType) {
    Objects.requireNonNull(ruleType, "ruleType must not be null");
    ValidationRuleExecutor executor = executors.get(ruleType);
    if (executor == null) {
      throw new IllegalStateException(
          "No Validation Rule executor is registered for " + ruleType + ".");
    }
    return executor;
  }
}
