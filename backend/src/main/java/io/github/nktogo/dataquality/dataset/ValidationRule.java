package io.github.nktogo.dataquality.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "validation_rule")
class ValidationRule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "profile_id",
      nullable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_validation_rule_profile"))
  private ValidationProfile profile;

  @NotBlank
  @Size(max = 255)
  @Column(name = "field_name", nullable = false, length = 255)
  private String fieldName;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "rule_type", nullable = false, length = 32)
  private ValidationRuleType ruleType;

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parameters_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> parameters;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ValidationRuleSeverity severity;

  @Column(nullable = false)
  private boolean enabled;

  protected ValidationRule() {}

  ValidationRule(
      ValidationProfile profile,
      String fieldName,
      ValidationRuleType ruleType,
      Map<String, Object> parameters,
      ValidationRuleSeverity severity,
      boolean enabled) {
    this.profile = profile;
    this.fieldName = fieldName;
    this.ruleType = ruleType;
    this.parameters = parameters;
    this.severity = severity;
    this.enabled = enabled;
  }

  UUID getId() {
    return id;
  }

  ValidationProfile getProfile() {
    return profile;
  }

  String getFieldName() {
    return fieldName;
  }

  ValidationRuleType getRuleType() {
    return ruleType;
  }

  Map<String, Object> getParameters() {
    return parameters;
  }

  ValidationRuleSeverity getSeverity() {
    return severity;
  }

  boolean isEnabled() {
    return enabled;
  }
}
