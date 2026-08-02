package io.github.nktogo.dataquality.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExecutableValidationRuleTests {

  @ParameterizedTest
  @MethodSource("matchingConfigurations")
  void acceptsTheConfigurationTypeRequiredByEachRule(
      ValidationRuleType ruleType, ValidationRuleConfiguration configuration) {
    ExecutableValidationRule rule =
        new ExecutableValidationRule(
            UUID.randomUUID(), "field", ruleType, configuration, ValidationRuleSeverity.ERROR);

    assertThat(rule.ruleType()).isEqualTo(ruleType);
    assertThat(rule.configuration()).isSameAs(configuration);
  }

  @ParameterizedTest
  @MethodSource("mismatchedConfigurations")
  void rejectsMismatchedRuleAndConfigurationTypes(
      ValidationRuleType ruleType,
      ValidationRuleConfiguration configuration,
      String expectedConfigurationType) {
    assertThatThrownBy(
            () ->
                new ExecutableValidationRule(
                    UUID.randomUUID(),
                    "field",
                    ruleType,
                    configuration,
                    ValidationRuleSeverity.ERROR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ruleType + " requires configuration type " + expectedConfigurationType + ".");
  }

  @Test
  void rejectsNullRequiredValues() {
    UUID id = UUID.randomUUID();
    ValidationRuleConfiguration configuration = new ValidationRuleConfiguration.Empty();

    assertThatThrownBy(
            () ->
                new ExecutableValidationRule(
                    null,
                    "field",
                    ValidationRuleType.REQUIRED_FIELD,
                    configuration,
                    ValidationRuleSeverity.ERROR))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ExecutableValidationRule(
                    id,
                    null,
                    ValidationRuleType.REQUIRED_FIELD,
                    configuration,
                    ValidationRuleSeverity.ERROR))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ExecutableValidationRule(
                    id, "field", null, configuration, ValidationRuleSeverity.ERROR))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ExecutableValidationRule(
                    id,
                    "field",
                    ValidationRuleType.REQUIRED_FIELD,
                    null,
                    ValidationRuleSeverity.ERROR))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ExecutableValidationRule(
                    id, "field", ValidationRuleType.REQUIRED_FIELD, configuration, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static Stream<Arguments> matchingConfigurations() {
    return Stream.of(
        Arguments.of(ValidationRuleType.REQUIRED_FIELD, new ValidationRuleConfiguration.Empty()),
        Arguments.of(ValidationRuleType.UNIQUENESS, new ValidationRuleConfiguration.Empty()),
        Arguments.of(
            ValidationRuleType.DATA_TYPE,
            new ValidationRuleConfiguration.DataType(ValidationDataType.INTEGER)),
        Arguments.of(
            ValidationRuleType.NUMERIC_RANGE,
            new ValidationRuleConfiguration.NumericRange(BigDecimal.ZERO, null)),
        Arguments.of(
            ValidationRuleType.DATE_FORMAT,
            new ValidationRuleConfiguration.DateFormat(ValidationDateFormat.ISO_DATE)));
  }

  private static Stream<Arguments> mismatchedConfigurations() {
    return Stream.of(
        Arguments.of(
            ValidationRuleType.DATA_TYPE, new ValidationRuleConfiguration.Empty(), "DataType"),
        Arguments.of(
            ValidationRuleType.NUMERIC_RANGE,
            new ValidationRuleConfiguration.DataType(ValidationDataType.DECIMAL),
            "NumericRange"),
        Arguments.of(
            ValidationRuleType.DATE_FORMAT, new ValidationRuleConfiguration.Empty(), "DateFormat"));
  }
}
