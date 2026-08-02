package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.UUID;

public interface ValidationRuleAccess {

  List<ExecutableValidationRule> getEnabledRules(UUID profileId);
}
