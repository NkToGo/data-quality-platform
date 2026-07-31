package io.github.nktogo.dataquality.validation;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Entity
@Table(name = "validation_issue")
class ValidationIssue {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Column(name = "run_id", nullable = false, updatable = false)
  private UUID runId;

  @Min(2)
  @Column(name = "row_number", nullable = false, updatable = false)
  private long rowNumber;

  @NotBlank
  @Size(max = 255)
  @Column(name = "field_name", nullable = false, updatable = false, length = 255)
  private String fieldName;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "rule_type", nullable = false, updatable = false, length = 32)
  private ValidationRuleType ruleType;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 16)
  private ValidationRuleSeverity severity;

  @NotBlank
  @Size(max = 500)
  @Column(nullable = false, updatable = false, length = 500)
  private String message;

  @Column(name = "observed_value", updatable = false, columnDefinition = "text")
  private String observedValue;

  protected ValidationIssue() {}

  ValidationIssue(UUID runId, ValidationIssueDraft draft) {
    this.runId = runId;
    this.rowNumber = draft.rowNumber();
    this.fieldName = draft.fieldName();
    this.ruleType = draft.ruleType();
    this.severity = draft.severity();
    this.message = draft.message();
    this.observedValue = draft.observedValue();
  }

  UUID getId() {
    return id;
  }

  UUID getRunId() {
    return runId;
  }

  long getRowNumber() {
    return rowNumber;
  }

  String getFieldName() {
    return fieldName;
  }

  ValidationRuleType getRuleType() {
    return ruleType;
  }

  ValidationRuleSeverity getSeverity() {
    return severity;
  }

  String getMessage() {
    return message;
  }

  String getObservedValue() {
    return observedValue;
  }
}
