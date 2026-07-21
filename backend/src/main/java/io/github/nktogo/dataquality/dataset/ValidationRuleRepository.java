package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ValidationRuleRepository extends JpaRepository<ValidationRule, UUID> {

  List<ValidationRule> findAllByProfileOrderByIdAsc(ValidationProfile profile);
}
