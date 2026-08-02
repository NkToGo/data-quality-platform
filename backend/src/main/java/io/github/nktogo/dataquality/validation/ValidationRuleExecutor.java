package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import java.util.List;

interface ValidationRuleExecutor {

  ValidationRuleType ruleType();

  List<ValidationIssueDraft> execute(
      ExecutableValidationRule rule, int columnIndex, List<ValidationInputRow> rows);
}
