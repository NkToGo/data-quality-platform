package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ExecutableValidationRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
final class ValidationColumnResolver {

  Optional<List<Integer>> resolve(
      List<String> headers, List<ExecutableValidationRule> enabledRules) {
    Map<String, Integer> columnIndexes = new HashMap<>();
    for (int index = 0; index < headers.size(); index++) {
      columnIndexes.put(headers.get(index), index);
    }

    List<Integer> resolvedIndexes = new ArrayList<>(enabledRules.size());
    for (ExecutableValidationRule rule : enabledRules) {
      Integer columnIndex = columnIndexes.get(rule.fieldName());
      if (columnIndex == null) {
        return Optional.empty();
      }
      resolvedIndexes.add(columnIndex);
    }

    return Optional.of(List.copyOf(resolvedIndexes));
  }
}
