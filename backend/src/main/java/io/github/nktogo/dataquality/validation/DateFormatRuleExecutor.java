package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationDateFormat;
import io.github.nktogo.dataquality.dataset.ValidationRuleConfiguration;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
final class DateFormatRuleExecutor implements ValidationRuleExecutor {

  private static final DateTimeFormatter ISO_DATE_FORMATTER = strictFormatter("uuuu-MM-dd");
  private static final DateTimeFormatter DAY_MONTH_YEAR_FORMATTER = strictFormatter("dd/MM/uuuu");
  private static final DateTimeFormatter MONTH_DAY_YEAR_FORMATTER = strictFormatter("MM/dd/uuuu");

  @Override
  public ValidationRuleType ruleType() {
    return ValidationRuleType.DATE_FORMAT;
  }

  @Override
  public List<ValidationIssueDraft> execute(
      ExecutableValidationRule rule, int columnIndex, List<ValidationInputRow> rows) {
    ValidationDateFormat dateFormat =
        ((ValidationRuleConfiguration.DateFormat) rule.configuration()).format();
    DateTimeFormatter formatter = formatterFor(dateFormat);
    List<ValidationIssueDraft> issues = new ArrayList<>();

    for (ValidationInputRow row : rows) {
      String value = row.values().get(columnIndex);
      if (!value.isBlank() && !matches(value, formatter)) {
        issues.add(issue(rule, row, value, dateFormat));
      }
    }
    return List.copyOf(issues);
  }

  private boolean matches(String value, DateTimeFormatter formatter) {
    try {
      LocalDate.parse(value, formatter);
      return true;
    } catch (DateTimeParseException exception) {
      return false;
    }
  }

  private DateTimeFormatter formatterFor(ValidationDateFormat dateFormat) {
    return switch (dateFormat) {
      case ISO_DATE -> ISO_DATE_FORMATTER;
      case DAY_MONTH_YEAR -> DAY_MONTH_YEAR_FORMATTER;
      case MONTH_DAY_YEAR -> MONTH_DAY_YEAR_FORMATTER;
    };
  }

  private static DateTimeFormatter strictFormatter(String pattern) {
    return DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);
  }

  private ValidationIssueDraft issue(
      ExecutableValidationRule rule,
      ValidationInputRow row,
      String observedValue,
      ValidationDateFormat dateFormat) {
    return new ValidationIssueDraft(
        row.recordNumber(),
        rule.fieldName(),
        rule.ruleType(),
        rule.severity(),
        "Value must match date format " + dateFormat + ".",
        observedValue);
  }
}
