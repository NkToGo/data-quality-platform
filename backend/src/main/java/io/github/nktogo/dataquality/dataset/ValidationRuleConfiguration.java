package io.github.nktogo.dataquality.dataset;

import java.math.BigDecimal;
import java.util.Objects;

public sealed interface ValidationRuleConfiguration {

  record Empty() implements ValidationRuleConfiguration {}

  record DataType(ValidationDataType type) implements ValidationRuleConfiguration {

    public DataType {
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  record NumericRange(BigDecimal minimum, BigDecimal maximum)
      implements ValidationRuleConfiguration {

    public NumericRange {
      if (minimum == null && maximum == null) {
        throw new IllegalArgumentException("At least one numeric range bound is required.");
      }
      if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
        throw new IllegalArgumentException(
            "The minimum numeric range bound must not exceed the maximum.");
      }
    }
  }

  record DateFormat(ValidationDateFormat format) implements ValidationRuleConfiguration {

    public DateFormat {
      Objects.requireNonNull(format, "format must not be null");
    }
  }
}
