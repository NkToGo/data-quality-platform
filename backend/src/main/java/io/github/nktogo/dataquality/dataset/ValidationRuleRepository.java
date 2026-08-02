package io.github.nktogo.dataquality.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ValidationRuleRepository extends JpaRepository<ValidationRule, UUID> {

  List<ValidationRule> findAllByProfileOrderByIdAsc(ValidationProfile profile);

  @Query(
      value =
          """
          select
              validation_rule.id as "id",
              validation_rule.field_name as "fieldName",
              validation_rule.rule_type as "ruleType",
              validation_rule.parameters_json::text as "parametersJson",
              validation_rule.severity as "severity"
          from validation_rule
          where validation_rule.profile_id = :profileId
            and validation_rule.enabled = true
          order by validation_rule.id asc
          """,
      nativeQuery = true)
  List<ExecutableValidationRuleRow> findEnabledExecutableRulesByProfileId(
      @Param("profileId") UUID profileId);

  interface ExecutableValidationRuleRow {

    UUID getId();

    String getFieldName();

    String getRuleType();

    String getParametersJson();

    String getSeverity();
  }
}
