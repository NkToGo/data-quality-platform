package io.github.nktogo.dataquality.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import io.github.nktogo.dataquality.ingestion.ValidationRunReportAccess;
import io.github.nktogo.dataquality.ingestion.ValidationRunResponse;
import io.github.nktogo.dataquality.ingestion.ValidationRunStatus;
import io.github.nktogo.dataquality.validation.ValidationIssueReportAccess;
import io.github.nktogo.dataquality.validation.ValidationIssueResponse;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class ValidationReportServiceTests {

  private static final Instant STARTED_AT = Instant.parse("2026-08-11T12:00:00.123456Z");

  private final ValidationRunReportAccess validationRunReportAccess =
      mock(ValidationRunReportAccess.class);
  private final ValidationIssueReportAccess validationIssueReportAccess =
      mock(ValidationIssueReportAccess.class);
  private final ValidationReportService service =
      new ValidationReportService(validationRunReportAccess, validationIssueReportAccess);

  @Test
  void readsThePersistedRunBeforeItsOrderedIssuesAndReturnsAnImmutableSnapshot() {
    UUID runId = UUID.randomUUID();
    ValidationRunResponse run = run(runId, ValidationRunStatus.COMPLETED);
    List<ValidationIssueResponse> mutableIssues =
        new ArrayList<>(
            List.of(issue(runId, 2, "first", ""), issue(runId, 4, "second", "unchanged")));
    when(validationRunReportAccess.getValidationRunForReport(runId)).thenReturn(run);
    when(validationIssueReportAccess.getValidationIssuesForReport(runId)).thenReturn(mutableIssues);

    ValidationReport report = service.getReport(runId);
    mutableIssues.clear();

    InOrder calls = inOrder(validationRunReportAccess, validationIssueReportAccess);
    calls.verify(validationRunReportAccess).getValidationRunForReport(runId);
    calls.verify(validationIssueReportAccess).getValidationIssuesForReport(runId);
    assertThat(report.validationRun()).isSameAs(run);
    assertThat(report.issues())
        .extracting(ValidationIssueResponse::fieldName)
        .containsExactly("first", "second");
    assertThatThrownBy(() -> report.issues().add(report.issues().getFirst()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @EnumSource(ValidationRunStatus.class)
  void preservesEveryPersistedValidationRunStatus(ValidationRunStatus status) {
    UUID runId = UUID.randomUUID();
    ValidationRunResponse run = run(runId, status);
    when(validationRunReportAccess.getValidationRunForReport(runId)).thenReturn(run);
    when(validationIssueReportAccess.getValidationIssuesForReport(runId)).thenReturn(List.of());

    ValidationReport report = service.getReport(runId);

    assertThat(report.validationRun()).isSameAs(run);
    assertThat(report.issues()).isEmpty();
  }

  @Test
  void stopsBeforeReadingIssuesWhenTheRunDoesNotExist() {
    UUID runId = UUID.randomUUID();
    IllegalStateException notFound = new IllegalStateException("run not found");
    when(validationRunReportAccess.getValidationRunForReport(runId)).thenThrow(notFound);

    assertThatThrownBy(() -> service.getReport(runId)).isSameAs(notFound);

    verifyNoInteractions(validationIssueReportAccess);
  }

  @Test
  void rejectsNullBeforeCrossingAReadBoundary() {
    assertThatThrownBy(() -> service.getReport(null)).isInstanceOf(NullPointerException.class);

    verifyNoInteractions(validationRunReportAccess, validationIssueReportAccess);
  }

  @Test
  void declaresAReadOnlyRepeatableReadSnapshot() throws NoSuchMethodException {
    Method method = ValidationReportService.class.getDeclaredMethod("getReport", UUID.class);

    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.readOnly()).isTrue();
    assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
  }

  @Test
  void reportModelRejectsNullComponents() {
    ValidationRunResponse run = run(UUID.randomUUID(), ValidationRunStatus.PENDING);

    assertThatThrownBy(() -> new ValidationReport(null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ValidationReport(run, null))
        .isInstanceOf(NullPointerException.class);
  }

  private ValidationRunResponse run(UUID runId, ValidationRunStatus status) {
    boolean started = status != ValidationRunStatus.PENDING;
    boolean finished =
        status == ValidationRunStatus.COMPLETED || status == ValidationRunStatus.FAILED;
    return new ValidationRunResponse(
        runId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        status,
        4,
        status == ValidationRunStatus.COMPLETED ? 3 : 0,
        status == ValidationRunStatus.COMPLETED ? 1 : 0,
        status == ValidationRunStatus.COMPLETED ? 2 : 0,
        started ? STARTED_AT : null,
        finished ? STARTED_AT.plusSeconds(1) : null,
        status == ValidationRunStatus.FAILED ? "Validation failed." : null);
  }

  private ValidationIssueResponse issue(
      UUID runId, long rowNumber, String fieldName, String observedValue) {
    return new ValidationIssueResponse(
        UUID.randomUUID(),
        runId,
        rowNumber,
        fieldName,
        ValidationRuleType.REQUIRED_FIELD,
        ValidationRuleSeverity.ERROR,
        "A persisted message.",
        observedValue);
  }
}
