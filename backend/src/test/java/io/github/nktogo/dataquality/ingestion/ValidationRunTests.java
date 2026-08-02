package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nktogo.dataquality.validation.ValidationSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidationRunTests {

  private static final Instant STARTED_AT = Instant.parse("2026-07-31T12:00:00.123456Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-07-31T12:01:00.654321Z");

  @Test
  void completesProcessingRunWithExactSummaryAndTimestamps() {
    ValidationRun run = processingRun(3);
    ValidationSummary summary = new ValidationSummary(3, 2, 1, 2);

    run.complete(summary, FINISHED_AT);

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.COMPLETED);
    assertThat(run.getTotalRows()).isEqualTo(3);
    assertThat(run.getValidRows()).isEqualTo(2);
    assertThat(run.getInvalidRows()).isEqualTo(1);
    assertThat(run.getIssueCount()).isEqualTo(2);
    assertThat(run.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(run.getFinishedAt()).isEqualTo(FINISHED_AT);
    assertThat(run.getFailureReason()).isNull();
  }

  @Test
  void completesHeaderOnlyRunWithZeroSummary() {
    ValidationRun run = processingRun(0);

    run.complete(new ValidationSummary(0, 0, 0, 0), FINISHED_AT);

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.COMPLETED);
    assertThat(run.getTotalRows()).isZero();
    assertThat(run.getValidRows()).isZero();
    assertThat(run.getInvalidRows()).isZero();
    assertThat(run.getIssueCount()).isZero();
  }

  @Test
  void rejectsCompletionWhoseTotalDoesNotMatchParsedRowsWithoutMutatingRun() {
    ValidationRun run = processingRun(3);

    assertThatThrownBy(() -> run.complete(new ValidationSummary(2, 2, 0, 0), FINISHED_AT))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.PROCESSING);
    assertThat(run.getTotalRows()).isEqualTo(3);
    assertThat(run.getValidRows()).isZero();
    assertThat(run.getInvalidRows()).isZero();
    assertThat(run.getIssueCount()).isZero();
    assertThat(run.getFinishedAt()).isNull();
  }

  @Test
  void rejectsCompletionWithFewerIssuesThanInvalidRows() {
    ValidationRun run = processingRun(2);

    assertThatThrownBy(() -> run.complete(new ValidationSummary(2, 1, 1, 0), FINISHED_AT))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.PROCESSING);
    assertThat(run.getFinishedAt()).isNull();
  }

  @Test
  void rejectsCompletionFromWrongStateOrWithInvalidTimestamp() {
    ValidationRun pending = pendingRun();
    ValidationSummary zeroSummary = new ValidationSummary(0, 0, 0, 0);

    assertThatThrownBy(() -> pending.complete(zeroSummary, FINISHED_AT))
        .isInstanceOf(IllegalStateException.class);

    ValidationRun processing = processingRun(0);
    assertThatThrownBy(() -> processing.complete(zeroSummary, STARTED_AT.minusNanos(1_000)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> processing.complete(zeroSummary, FINISHED_AT.plusNanos(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> processing.complete(zeroSummary, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void expectedValidationFailurePreservesParsedTotalAndClearsSummary() {
    ValidationRun run = processingRun(4);

    run.failValidation(
        FINISHED_AT, "CSV header does not contain a field required by the Validation Profile.");

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
    assertThat(run.getTotalRows()).isEqualTo(4);
    assertThat(run.getValidRows()).isZero();
    assertThat(run.getInvalidRows()).isZero();
    assertThat(run.getIssueCount()).isZero();
    assertThat(run.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(run.getFinishedAt()).isEqualTo(FINISHED_AT);
    assertThat(run.getFailureReason())
        .isEqualTo("CSV header does not contain a field required by the Validation Profile.");
  }

  @Test
  void recoveryCanRestoreOriginalProcessingContextBeforeFailingValidation() {
    ValidationRun run = pendingRun();

    run.start(STARTED_AT);
    run.recordParsedRowCount(7);
    run.failValidation(FINISHED_AT, "Validation processing failed.");

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
    assertThat(run.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(run.getFinishedAt()).isEqualTo(FINISHED_AT);
    assertThat(run.getTotalRows()).isEqualTo(7);
    assertThat(run.getValidRows()).isZero();
    assertThat(run.getInvalidRows()).isZero();
    assertThat(run.getIssueCount()).isZero();
    assertThat(run.getFailureReason()).isEqualTo("Validation processing failed.");
  }

  @Test
  void rejectsInvalidValidationFailureWithoutMutatingRun() {
    ValidationRun run = processingRun(2);

    assertThatThrownBy(() -> run.failValidation(STARTED_AT.minusNanos(1_000), "Failure"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> run.failValidation(FINISHED_AT, "\t\n"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> run.failValidation(FINISHED_AT, "x".repeat(256)))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.PROCESSING);
    assertThat(run.getTotalRows()).isEqualTo(2);
    assertThat(run.getFinishedAt()).isNull();
  }

  @Test
  void parserFailureStillClearsParsedTotal() {
    ValidationRun run = processingRun(5);

    run.failParsing(FINISHED_AT, "CSV content is malformed.");

    assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
    assertThat(run.getTotalRows()).isZero();
    assertThat(run.getValidRows()).isZero();
    assertThat(run.getInvalidRows()).isZero();
    assertThat(run.getIssueCount()).isZero();
    assertThat(run.getFailureReason()).isEqualTo("CSV content is malformed.");
  }

  private ValidationRun processingRun(long totalRows) {
    ValidationRun run = pendingRun();
    run.start(STARTED_AT);
    run.recordParsedRowCount(totalRows);
    return run;
  }

  private ValidationRun pendingRun() {
    return ValidationRun.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  }
}
