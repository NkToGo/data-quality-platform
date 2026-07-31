package io.github.nktogo.dataquality.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class ValidationRuleParameterCodecTests {

  private final ValidationRuleParameterCodec codec = new ValidationRuleParameterCodec();

  @Nested
  class EmptyParameters {

    @ParameterizedTest
    @EnumSource(
        value = ValidationRuleType.class,
        names = {"REQUIRED_FIELD", "UNIQUENESS"})
    void decodesAndEncodesEmptyConfigurations(ValidationRuleType ruleType) {
      ValidationRuleConfiguration configuration = codec.decode(ruleType, Map.of());

      assertThat(configuration).isEqualTo(new ValidationRuleConfiguration.Empty());
      assertThat(codec.encode(ruleType, configuration)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
        value = ValidationRuleType.class,
        names = {"REQUIRED_FIELD", "UNIQUENESS"})
    void rejectsEveryParameter(ValidationRuleType ruleType) {
      assertInvalid(
          ruleType, Map.of("trim", true), ruleType + " contains unknown parameters: 'trim'.");
    }
  }

  @Nested
  class DataTypeParameters {

    @ParameterizedTest
    @EnumSource(ValidationDataType.class)
    void decodesEverySupportedType(ValidationDataType dataType) {
      ValidationRuleConfiguration configuration =
          codec.decode(ValidationRuleType.DATA_TYPE, Map.of("type", dataType.name()));

      assertThat(configuration).isEqualTo(new ValidationRuleConfiguration.DataType(dataType));
      assertThat(codec.encode(ValidationRuleType.DATA_TYPE, configuration))
          .containsExactly(Map.entry("type", dataType.name()));
    }

    @Test
    void rejectsMissingType() {
      assertInvalid(ValidationRuleType.DATA_TYPE, Map.of(), "DATA_TYPE requires parameter 'type'.");
    }

    @ParameterizedTest
    @MethodSource(
        "io.github.nktogo.dataquality.dataset.ValidationRuleParameterCodecTests#nonStrings")
    void rejectsNonStringType(Object value) {
      assertInvalid(
          ValidationRuleType.DATA_TYPE,
          Map.of("type", value),
          "DATA_TYPE parameter 'type' must be a JSON string.");
    }

    @Test
    void rejectsNullType() {
      assertInvalid(
          ValidationRuleType.DATA_TYPE,
          nullableMap("type"),
          "DATA_TYPE parameter 'type' must be a JSON string.");
    }

    @ParameterizedTest
    @MethodSource(
        "io.github.nktogo.dataquality.dataset.ValidationRuleParameterCodecTests#unsupportedDataTypes")
    void rejectsUnsupportedAndIncorrectlyCasedTypes(String value) {
      assertInvalid(
          ValidationRuleType.DATA_TYPE,
          Map.of("type", value),
          "DATA_TYPE parameter 'type' must be one of: INTEGER, DECIMAL, BOOLEAN, STRING.");
    }
  }

  @Nested
  class NumericRangeParameters {

    @Test
    void decodesMinimumOnly() {
      ValidationRuleConfiguration.NumericRange configuration =
          (ValidationRuleConfiguration.NumericRange)
              codec.decode(ValidationRuleType.NUMERIC_RANGE, Map.of("minimum", 10));

      assertThat(configuration.minimum()).isEqualByComparingTo("10");
      assertThat(configuration.maximum()).isNull();
      assertThat(codec.encode(ValidationRuleType.NUMERIC_RANGE, configuration))
          .containsExactly(Map.entry("minimum", new BigDecimal("10")));
    }

    @Test
    void decodesMaximumOnly() {
      ValidationRuleConfiguration.NumericRange configuration =
          (ValidationRuleConfiguration.NumericRange)
              codec.decode(ValidationRuleType.NUMERIC_RANGE, Map.of("maximum", 10L));

      assertThat(configuration.minimum()).isNull();
      assertThat(configuration.maximum()).isEqualByComparingTo("10");
      assertThat(codec.encode(ValidationRuleType.NUMERIC_RANGE, configuration))
          .containsExactly(Map.entry("maximum", new BigDecimal("10")));
    }

    @Test
    void decodesBothBoundsInCanonicalOrder() {
      Map<String, Object> source = new LinkedHashMap<>();
      source.put("maximum", 10);
      source.put("minimum", 1);

      ValidationRuleConfiguration.NumericRange configuration =
          (ValidationRuleConfiguration.NumericRange)
              codec.decode(ValidationRuleType.NUMERIC_RANGE, source);
      Map<String, Object> encoded = codec.encode(ValidationRuleType.NUMERIC_RANGE, configuration);

      assertThat(configuration.minimum()).isEqualByComparingTo("1");
      assertThat(configuration.maximum()).isEqualByComparingTo("10");
      assertThat(new ArrayList<>(encoded.keySet())).containsExactly("minimum", "maximum");
    }

    @Test
    void acceptsEqualBounds() {
      ValidationRuleConfiguration.NumericRange configuration =
          (ValidationRuleConfiguration.NumericRange)
              codec.decode(
                  ValidationRuleType.NUMERIC_RANGE,
                  Map.of("minimum", new BigDecimal("1.00"), "maximum", 1));

      assertThat(configuration.minimum()).isEqualByComparingTo(configuration.maximum());
    }

    @Test
    void decodesPersistedJsonNumbersWithoutLosingPrecision() {
      String minimum = "0.123456789012345678901234567890123456789";
      ValidationRuleConfiguration.NumericRange configuration =
          (ValidationRuleConfiguration.NumericRange)
              codec.decodePersisted(
                  ValidationRuleType.NUMERIC_RANGE, "{\"minimum\":" + minimum + "}");

      assertThat(configuration.minimum()).isEqualByComparingTo(minimum);
    }

    @ParameterizedTest
    @MethodSource(
        "io.github.nktogo.dataquality.dataset.ValidationRuleParameterCodecTests#jsonNumbers")
    void convertsJsonNumberRepresentationsToBigDecimal(Number value, String expected) {
      ValidationRuleConfiguration.NumericRange configuration =
          (ValidationRuleConfiguration.NumericRange)
              codec.decode(ValidationRuleType.NUMERIC_RANGE, Map.of("minimum", value));

      assertThat(configuration.minimum()).isInstanceOf(BigDecimal.class);
      assertThat(configuration.minimum()).isEqualByComparingTo(expected);
    }

    @Test
    void rejectsMissingBounds() {
      assertInvalid(
          ValidationRuleType.NUMERIC_RANGE,
          Map.of(),
          "NUMERIC_RANGE requires at least one of parameters 'minimum' or 'maximum'.");
    }

    @ParameterizedTest
    @MethodSource(
        "io.github.nktogo.dataquality.dataset.ValidationRuleParameterCodecTests#nonNumbers")
    void rejectsNonNumericBounds(Object value) {
      assertInvalid(
          ValidationRuleType.NUMERIC_RANGE,
          Map.of("minimum", value),
          "NUMERIC_RANGE parameter 'minimum' must be a JSON number.");
    }

    @Test
    void rejectsNullBound() {
      assertInvalid(
          ValidationRuleType.NUMERIC_RANGE,
          nullableMap("minimum"),
          "NUMERIC_RANGE parameter 'minimum' must be a JSON number.");
    }

    @ParameterizedTest
    @MethodSource(
        "io.github.nktogo.dataquality.dataset.ValidationRuleParameterCodecTests#nonFiniteNumbers")
    void rejectsNonFiniteBounds(Number value) {
      assertInvalid(
          ValidationRuleType.NUMERIC_RANGE,
          Map.of("minimum", value),
          "NUMERIC_RANGE parameter 'minimum' must be a finite JSON number.");
    }

    @Test
    void rejectsReversedBounds() {
      assertInvalid(
          ValidationRuleType.NUMERIC_RANGE,
          Map.of("minimum", 11, "maximum", 10),
          "NUMERIC_RANGE parameter 'minimum' must be less than or equal to parameter 'maximum'.");
    }
  }

  @Nested
  class DateFormatParameters {

    @ParameterizedTest
    @EnumSource(ValidationDateFormat.class)
    void decodesEveryControlledFormat(ValidationDateFormat dateFormat) {
      ValidationRuleConfiguration configuration =
          codec.decode(ValidationRuleType.DATE_FORMAT, Map.of("format", dateFormat.name()));

      assertThat(configuration).isEqualTo(new ValidationRuleConfiguration.DateFormat(dateFormat));
      assertThat(codec.encode(ValidationRuleType.DATE_FORMAT, configuration))
          .containsExactly(Map.entry("format", dateFormat.name()));
    }

    @Test
    void rejectsMissingFormat() {
      assertInvalid(
          ValidationRuleType.DATE_FORMAT, Map.of(), "DATE_FORMAT requires parameter 'format'.");
    }

    @ParameterizedTest
    @MethodSource(
        "io.github.nktogo.dataquality.dataset.ValidationRuleParameterCodecTests#nonStrings")
    void rejectsNonStringFormat(Object value) {
      assertInvalid(
          ValidationRuleType.DATE_FORMAT,
          Map.of("format", value),
          "DATE_FORMAT parameter 'format' must be a JSON string.");
    }

    @Test
    void rejectsNullFormat() {
      assertInvalid(
          ValidationRuleType.DATE_FORMAT,
          nullableMap("format"),
          "DATE_FORMAT parameter 'format' must be a JSON string.");
    }

    @ParameterizedTest
    @MethodSource(
        "io.github.nktogo.dataquality.dataset.ValidationRuleParameterCodecTests#unsupportedDateFormats")
    void rejectsArbitraryAndIncorrectlyCasedFormats(String value) {
      assertInvalid(
          ValidationRuleType.DATE_FORMAT,
          Map.of("format", value),
          "DATE_FORMAT parameter 'format' must be one of: "
              + "ISO_DATE, DAY_MONTH_YEAR, MONTH_DAY_YEAR.");
    }
  }

  @Nested
  class BoundaryBehavior {

    @Test
    void rejectsUnknownParametersInDeterministicOrder() {
      Map<String, Object> parameters = new LinkedHashMap<>();
      parameters.put("zeta", true);
      parameters.put("alpha", true);

      assertInvalid(
          ValidationRuleType.DATA_TYPE,
          parameters,
          "DATA_TYPE contains unknown parameters: 'alpha', 'zeta'.");
    }

    @Test
    void rendersSafeUnknownParameterNames() {
      assertInvalid(
          ValidationRuleType.REQUIRED_FIELD,
          Map.of("safe_name1", true),
          "REQUIRED_FIELD contains unknown parameters: 'safe_name1'.");
    }

    @Test
    void doesNotEchoUnsafeUnknownParameterNames() {
      assertInvalid(
          ValidationRuleType.REQUIRED_FIELD,
          Map.of("line\nbreak", true),
          "REQUIRED_FIELD contains unknown parameters: '<unsupported>'.");
    }

    @Test
    void doesNotEchoOversizedUnknownParameterNames() {
      assertInvalid(
          ValidationRuleType.REQUIRED_FIELD,
          Map.of("a".repeat(65), true),
          "REQUIRED_FIELD contains unknown parameters: '<unsupported>'.");
    }

    @Test
    void rejectsNullParameterMap() {
      assertInvalid(
          ValidationRuleType.REQUIRED_FIELD, null, "Validation Rule parameters are required.");
    }

    @Test
    void canonicalMapIsImmutableAndIndependentFromSourceMap() {
      Map<String, Object> source = new HashMap<>();
      source.put("minimum", 1);

      Map<String, Object> canonical = codec.canonicalize(ValidationRuleType.NUMERIC_RANGE, source);
      source.put("minimum", 2);

      assertThat(canonical).containsEntry("minimum", new BigDecimal("1"));
      assertThatThrownBy(() -> canonical.put("maximum", BigDecimal.TEN))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requestDeserializationPreservesHighPrecisionDecimal() throws Exception {
      String exactDecimal = "0.123456789012345678901234567890123456789";
      CreateValidationRuleRequest request =
          JsonMapper.builder()
              .build()
              .readValue(
                  """
                  {
                    "fieldName": "amount",
                    "ruleType": "NUMERIC_RANGE",
                    "parameters": {"minimum": %s},
                    "severity": "ERROR",
                    "enabled": true
                  }
                  """
                      .formatted(exactDecimal),
                  CreateValidationRuleRequest.class);

      assertThat(request.parameters().get("minimum"))
          .isEqualTo(new BigDecimal(exactDecimal))
          .isInstanceOf(BigDecimal.class);
    }

    @Test
    void responseParametersAreImmutableAndIndependentFromSourceMap() {
      Map<String, Object> source = new HashMap<>();
      source.put("type", "INTEGER");
      ValidationRuleResponse response =
          new ValidationRuleResponse(
              UUID.randomUUID(),
              UUID.randomUUID(),
              "amount",
              ValidationRuleType.DATA_TYPE,
              source,
              ValidationRuleSeverity.ERROR,
              true);

      source.put("type", "STRING");

      assertThat(response.parameters()).containsEntry("type", "INTEGER");
      assertThatThrownBy(() -> response.parameters().put("type", "DECIMAL"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void typedConfigurationsEnforceTheirInvariants() {
      assertThatThrownBy(() -> new ValidationRuleConfiguration.DataType(null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new ValidationRuleConfiguration.DateFormat(null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new ValidationRuleConfiguration.NumericRange(null, null))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () ->
                  new ValidationRuleConfiguration.NumericRange(
                      new BigDecimal("2"), new BigDecimal("1")))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsConfigurationTypeThatDoesNotMatchRuleType() {
      assertThatThrownBy(
              () ->
                  codec.encode(
                      ValidationRuleType.DATE_FORMAT,
                      new ValidationRuleConfiguration.DataType(ValidationDataType.STRING)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("DATE_FORMAT cannot be encoded from DataType.");
    }
  }

  private void assertInvalid(
      ValidationRuleType ruleType, Map<String, Object> parameters, String message) {
    assertThatThrownBy(() -> codec.decode(ruleType, parameters))
        .isInstanceOf(InvalidValidationRuleParametersException.class)
        .hasMessage(message);
  }

  private static Map<String, Object> nullableMap(String key) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put(key, null);
    return parameters;
  }

  private static Stream<Object> nonStrings() {
    return Stream.of(1, true, Map.of("nested", true), List.of("nested"));
  }

  private static Stream<String> unsupportedDataTypes() {
    return Stream.of("integer", "DATE", "");
  }

  private static Stream<Arguments> jsonNumbers() {
    return Stream.of(
        Arguments.of(1, "1"),
        Arguments.of(2L, "2"),
        Arguments.of(new BigInteger("3"), "3"),
        Arguments.of(new BigDecimal("4.25"), "4.25"),
        Arguments.of(5.5d, "5.5"),
        Arguments.of(new BigDecimal("1E+3"), "1000"));
  }

  private static Stream<Object> nonNumbers() {
    return Stream.of("10", true, Map.of("nested", 10), List.of(10));
  }

  private static Stream<Number> nonFiniteNumbers() {
    return Stream.of(Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
  }

  private static Stream<String> unsupportedDateFormats() {
    return Stream.of("iso_date", "yyyy-MM-dd", "");
  }
}
