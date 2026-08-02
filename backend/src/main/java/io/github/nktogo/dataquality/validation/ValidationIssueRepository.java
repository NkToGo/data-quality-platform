package io.github.nktogo.dataquality.validation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ValidationIssueRepository extends JpaRepository<ValidationIssue, UUID> {

  List<ValidationIssue> findAllByRunIdOrderByRowNumberAscFieldNameAscRuleTypeAscIdAsc(UUID runId);
}
