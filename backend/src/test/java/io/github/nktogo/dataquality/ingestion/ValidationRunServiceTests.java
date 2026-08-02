package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ValidationRunServiceTests {

  private static final Instant STARTED_AT = Instant.parse("2026-07-31T12:00:00.123456Z");

  private final ValidationRunLifecycleService lifecycleService =
      mock(ValidationRunLifecycleService.class);
  private final ValidationRunRecoveryService recoveryService =
      mock(ValidationRunRecoveryService.class);
  private final ValidationRunRepository validationRunRepository =
      mock(ValidationRunRepository.class);
  private final ValidationRunService service =
      new ValidationRunService(lifecycleService, recoveryService, validationRunRepository);

  @Test
  void createsPendingRunThenProcessesIt() {
    UUID fileId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    CreateValidationRunRequest request = new CreateValidationRunRequest(profileId);
    ValidationRunResponse response =
        response(runId, fileId, profileId, ValidationRunStatus.COMPLETED);
    when(lifecycleService.createPending(fileId, profileId)).thenReturn(runId);
    when(lifecycleService.process(runId)).thenReturn(response);

    ValidationRunResponse result = service.create(fileId, request);

    assertThat(result).isSameAs(response);
    InOrder calls = inOrder(lifecycleService, recoveryService);
    calls.verify(lifecycleService).createPending(fileId, profileId);
    calls.verify(lifecycleService).process(runId);
    verifyNoInteractions(recoveryService);
  }

  @Test
  void recoversOnlyValidationProcessingFailuresAfterProcessingTransactionReturns() {
    UUID fileId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    RuntimeException cause = new IllegalStateException("Validation engine failed.");
    ValidationProcessingFailureException failure =
        new ValidationProcessingFailureException(runId, STARTED_AT, 3, cause);
    ValidationRunResponse recovered =
        response(runId, fileId, profileId, ValidationRunStatus.FAILED);
    when(lifecycleService.createPending(fileId, profileId)).thenReturn(runId);
    when(lifecycleService.process(runId)).thenThrow(failure);
    when(recoveryService.recover(failure)).thenReturn(recovered);

    ValidationRunResponse result =
        service.create(fileId, new CreateValidationRunRequest(profileId));

    assertThat(result).isSameAs(recovered);
    InOrder calls = inOrder(lifecycleService, recoveryService);
    calls.verify(lifecycleService).createPending(fileId, profileId);
    calls.verify(lifecycleService).process(runId);
    calls.verify(recoveryService).recover(failure);
  }

  @Test
  void doesNotRecoverUnexpectedParserRuntimeFailure() {
    UUID fileId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    IllegalStateException failure = new IllegalStateException("Unexpected parser failure.");
    when(lifecycleService.createPending(fileId, profileId)).thenReturn(runId);
    when(lifecycleService.process(runId)).thenThrow(failure);

    assertThatThrownBy(() -> service.create(fileId, new CreateValidationRunRequest(profileId)))
        .isSameAs(failure);

    verifyNoInteractions(recoveryService);
  }

  @Test
  void propagatesRecoveryFailure() {
    UUID fileId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    ValidationProcessingFailureException processingFailure =
        new ValidationProcessingFailureException(
            runId, STARTED_AT, 2, new IllegalStateException("Validation failed."));
    IllegalStateException recoveryFailure = new IllegalStateException("Database unavailable.");
    when(lifecycleService.createPending(fileId, profileId)).thenReturn(runId);
    when(lifecycleService.process(runId)).thenThrow(processingFailure);
    when(recoveryService.recover(processingFailure)).thenThrow(recoveryFailure);

    assertThatThrownBy(() -> service.create(fileId, new CreateValidationRunRequest(profileId)))
        .isSameAs(recoveryFailure);
    assertThat(recoveryFailure.getSuppressed()).containsExactly(processingFailure);
  }

  private ValidationRunResponse response(
      UUID runId, UUID fileId, UUID profileId, ValidationRunStatus status) {
    String failureReason =
        status == ValidationRunStatus.FAILED ? "Validation processing failed." : null;
    return new ValidationRunResponse(
        runId,
        UUID.randomUUID(),
        fileId,
        profileId,
        status,
        3,
        status == ValidationRunStatus.COMPLETED ? 3 : 0,
        0,
        0,
        STARTED_AT,
        STARTED_AT,
        failureReason);
  }
}
