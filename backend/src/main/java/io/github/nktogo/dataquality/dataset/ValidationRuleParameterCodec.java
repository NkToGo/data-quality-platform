package io.github.nktogo.dataquality.dataset;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
final class ValidationRuleParameterCodec {

  private static final String TYPE_PARAMETER = "type";
  private static final String MINIMUM_PARAMETER = "minimum";
  private static final String MAXIMUM_PARAMETER = "maximum";
  private static final String FORMAT_PARAMETER = "format";
  private static final int MAX_PUBLIC_PARAMETER_NAME_LENGTH = 64;

  private static final Set<String> NO_PARAMETERS = Set.of();
  private static final Set<String> DATA_TYPE_PARAMETERS = Set.of(TYPE_PARAMETER);
  private static final Set<String> NUMERIC_RANGE_PARAMETERS =
      Set.of(MINIMUM_PARAMETER, MAXIMUM_PARAMETER);
  private static final Set<String> DATE_FORMAT_PARAMETERS = Set.of(FORMAT_PARAMETER);

  ValidationRuleConfiguration decode(ValidationRuleType ruleType, Map<String, Object> parameters) {
    Objects.requireNonNull(ruleType, "ruleType must not be null");
    if (parameters == null) {
      throw invalid("Validation Rule parameters are required.");
    }

    return switch (ruleType) {
      case REQUIRED_FIELD, UNIQUENESS -> decodeEmpty(ruleType, parameters);
      case DATA_TYPE -> decodeDataType(parameters);
      case NUMERIC_RANGE -> decodeNumericRange(parameters);
      case DATE_FORMAT -> decodeDateFormat(parameters);
    };
  }

  Map<String, Object> encode(
      ValidationRuleType ruleType, ValidationRuleConfiguration configuration) {
    Objects.requireNonNull(ruleType, "ruleType must not be null");
    Objects.requireNonNull(configuration, "configuration must not be null");

    return switch (ruleType) {
      case REQUIRED_FIELD, UNIQUENESS -> {
        requireConfigurationType(ruleType, configuration, ValidationRuleConfiguration.Empty.class);
        yield Map.of();
      }
      case DATA_TYPE -> {
        ValidationRuleConfiguration.DataType dataType =
            requireConfigurationType(
                ruleType, configuration, ValidationRuleConfiguration.DataType.class);
        yield Map.of(TYPE_PARAMETER, dataType.type().name());
      }
      case NUMERIC_RANGE -> {
        ValidationRuleConfiguration.NumericRange numericRange =
            requireConfigurationType(
                ruleType, configuration, ValidationRuleConfiguration.NumericRange.class);
        LinkedHashMap<String, Object> encoded = new LinkedHashMap<>();
        if (numericRange.minimum() != null) {
          encoded.put(MINIMUM_PARAMETER, numericRange.minimum());
        }
        if (numericRange.maximum() != null) {
          encoded.put(MAXIMUM_PARAMETER, numericRange.maximum());
        }
        yield Collections.unmodifiableMap(encoded);
      }
      case DATE_FORMAT -> {
        ValidationRuleConfiguration.DateFormat dateFormat =
            requireConfigurationType(
                ruleType, configuration, ValidationRuleConfiguration.DateFormat.class);
        yield Map.of(FORMAT_PARAMETER, dateFormat.format().name());
      }
    };
  }

  Map<String, Object> canonicalize(ValidationRuleType ruleType, Map<String, Object> parameters) {
    return encode(ruleType, decode(ruleType, parameters));
  }

  private ValidationRuleConfiguration decodeEmpty(
      ValidationRuleType ruleType, Map<String, Object> parameters) {
    rejectUnknownParameters(ruleType, parameters, NO_PARAMETERS);
    return new ValidationRuleConfiguration.Empty();
  }

  private ValidationRuleConfiguration decodeDataType(Map<String, Object> parameters) {
    rejectUnknownParameters(ValidationRuleType.DATA_TYPE, parameters, DATA_TYPE_PARAMETERS);
    requireParameter(ValidationRuleType.DATA_TYPE, parameters, TYPE_PARAMETER);
    String value = requireStringParameter(ValidationRuleType.DATA_TYPE, parameters, TYPE_PARAMETER);

    try {
      return new ValidationRuleConfiguration.DataType(ValidationDataType.valueOf(value));
    } catch (IllegalArgumentException exception) {
      throw invalid(
          "DATA_TYPE parameter 'type' must be one of: INTEGER, DECIMAL, BOOLEAN, STRING.");
    }
  }

  private ValidationRuleConfiguration decodeNumericRange(Map<String, Object> parameters) {
    rejectUnknownParameters(ValidationRuleType.NUMERIC_RANGE, parameters, NUMERIC_RANGE_PARAMETERS);
    if (!parameters.containsKey(MINIMUM_PARAMETER) && !parameters.containsKey(MAXIMUM_PARAMETER)) {
      throw invalid("NUMERIC_RANGE requires at least one of parameters 'minimum' or 'maximum'.");
    }

    BigDecimal minimum =
        parameters.containsKey(MINIMUM_PARAMETER)
            ? requireNumberParameter(
                ValidationRuleType.NUMERIC_RANGE, parameters, MINIMUM_PARAMETER)
            : null;
    BigDecimal maximum =
        parameters.containsKey(MAXIMUM_PARAMETER)
            ? requireNumberParameter(
                ValidationRuleType.NUMERIC_RANGE, parameters, MAXIMUM_PARAMETER)
            : null;

    if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
      throw invalid(
          "NUMERIC_RANGE parameter 'minimum' must be less than or equal to parameter 'maximum'.");
    }

    return new ValidationRuleConfiguration.NumericRange(minimum, maximum);
  }

  private ValidationRuleConfiguration decodeDateFormat(Map<String, Object> parameters) {
    rejectUnknownParameters(ValidationRuleType.DATE_FORMAT, parameters, DATE_FORMAT_PARAMETERS);
    requireParameter(ValidationRuleType.DATE_FORMAT, parameters, FORMAT_PARAMETER);
    String value =
        requireStringParameter(ValidationRuleType.DATE_FORMAT, parameters, FORMAT_PARAMETER);

    try {
      return new ValidationRuleConfiguration.DateFormat(ValidationDateFormat.valueOf(value));
    } catch (IllegalArgumentException exception) {
      throw invalid(
          "DATE_FORMAT parameter 'format' must be one of: "
              + "ISO_DATE, DAY_MONTH_YEAR, MONTH_DAY_YEAR.");
    }
  }

  private void rejectUnknownParameters(
      ValidationRuleType ruleType, Map<String, Object> parameters, Set<String> allowedParameters) {
    List<String> unknownParameters =
        parameters.keySet().stream()
            .filter(parameter -> !allowedParameters.contains(parameter))
            .sorted()
            .toList();
    if (unknownParameters.isEmpty()) {
      return;
    }

    String renderedParameters =
        unknownParameters.stream().map(this::renderParameterName).collect(Collectors.joining(", "));
    throw invalid(ruleType + " contains unknown parameters: " + renderedParameters + ".");
  }

  private String renderParameterName(String parameter) {
    if (parameter.length() <= MAX_PUBLIC_PARAMETER_NAME_LENGTH
        && parameter.matches("[A-Za-z][A-Za-z0-9_]*")) {
      return "'" + parameter + "'";
    }
    return "'<unsupported>'";
  }

  private void requireParameter(
      ValidationRuleType ruleType, Map<String, Object> parameters, String parameter) {
    if (!parameters.containsKey(parameter)) {
      throw invalid(ruleType + " requires parameter '" + parameter + "'.");
    }
  }

  private String requireStringParameter(
      ValidationRuleType ruleType, Map<String, Object> parameters, String parameter) {
    Object value = parameters.get(parameter);
    if (!(value instanceof String stringValue)) {
      throw invalid(ruleType + " parameter '" + parameter + "' must be a JSON string.");
    }
    return stringValue;
  }

  private BigDecimal requireNumberParameter(
      ValidationRuleType ruleType, Map<String, Object> parameters, String parameter) {
    Object value = parameters.get(parameter);
    if (!(value instanceof Number number)) {
      throw invalid(ruleType + " parameter '" + parameter + "' must be a JSON number.");
    }

    try {
      return toBigDecimal(number);
    } catch (NumberFormatException exception) {
      throw invalid(ruleType + " parameter '" + parameter + "' must be a finite JSON number.");
    }
  }

  private BigDecimal toBigDecimal(Number number) {
    return switch (number) {
      case BigDecimal value -> value;
      case BigInteger value -> new BigDecimal(value);
      case Byte value -> BigDecimal.valueOf(value.longValue());
      case Short value -> BigDecimal.valueOf(value.longValue());
      case Integer value -> BigDecimal.valueOf(value.longValue());
      case Long value -> BigDecimal.valueOf(value);
      case Float value -> BigDecimal.valueOf(value.doubleValue());
      case Double value -> BigDecimal.valueOf(value);
      default -> new BigDecimal(number.toString());
    };
  }

  private <T extends ValidationRuleConfiguration> T requireConfigurationType(
      ValidationRuleType ruleType,
      ValidationRuleConfiguration configuration,
      Class<T> expectedType) {
    if (!expectedType.isInstance(configuration)) {
      throw new IllegalArgumentException(
          ruleType + " cannot be encoded from " + configuration.getClass().getSimpleName() + ".");
    }
    return expectedType.cast(configuration);
  }

  private InvalidValidationRuleParametersException invalid(String message) {
    return new InvalidValidationRuleParametersException(message);
  }
}
