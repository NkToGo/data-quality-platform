package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.nktogo.dataquality.operations.OperationsMetrics;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ValidationProcessingOutcome;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ValidationProcessingSample;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

class ValidationRunServiceTests {

  private static final Instant STARTED_AT = Instant.parse("2026-07-31T12:00:00.123456Z");

  private final ValidationRunLifecycleService lifecycleService =
      mock(ValidationRunLifecycleService.class);
  private final ValidationRunRecoveryService recoveryService =
      mock(ValidationRunRecoveryService.class);
  private final ValidationRunRepository validationRunRepository =
      mock(ValidationRunRepository.class);
  private final OperationsMetrics operationsMetrics = mock(OperationsMetrics.class);
  private final ValidationProcessingSample processingSample =
      mock(ValidationProcessingSample.class);
  private final ValidationRunService service =
      new ValidationRunService(
          lifecycleService, recoveryService, validationRunRepository, operationsMetrics);
  private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(ValidationRunService.class);
  private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
  private boolean originalAdditive;

  @BeforeEach
  void setUp() {
    when(operationsMetrics.startValidationProcessing()).thenReturn(processingSample);
    originalAdditive = serviceLogger.isAdditive();
    serviceLogger.setAdditive(false);
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    serviceLogger.detachAppender(logAppender);
    serviceLogger.setAdditive(originalAdditive);
    logAppender.stop();
  }

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
    verify(operationsMetrics).incrementValidationRunsCreated();
    verify(operationsMetrics)
        .recordValidationProcessing(processingSample, ValidationProcessingOutcome.COMPLETED);
    assertEvent("validation_run.created", Level.INFO, runId, null);
    assertEvent("validation_run.finished", Level.INFO, runId, null);
  }

  @Test
  void recordsAHandledPersistedProcessingFailure() {
    UUID fileId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    ValidationRunResponse failed = response(runId, fileId, profileId, ValidationRunStatus.FAILED);
    when(lifecycleService.createPending(fileId, profileId)).thenReturn(runId);
    when(lifecycleService.process(runId)).thenReturn(failed);

    ValidationRunResponse result =
        service.create(fileId, new CreateValidationRunRequest(profileId));

    assertThat(result).isSameAs(failed);
    verify(operationsMetrics)
        .recordValidationProcessing(processingSample, ValidationProcessingOutcome.FAILED);
    assertEvent("validation_run.processing_failed", Level.WARN, runId, null);
  }

  @Test
  void mapsAnExistingRunThroughTheReportingReadBoundary() {
    UUID runId = UUID.randomUUID();
    UUID datasetId = UUID.randomUUID();
    UUID sourceFileId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    ValidationRun validationRun = mock(ValidationRun.class);
    when(validationRunRepository.findById(runId)).thenReturn(Optional.of(validationRun));
    when(validationRun.getId()).thenReturn(runId);
    when(validationRun.getDatasetId()).thenReturn(datasetId);
    when(validationRun.getSourceFileId()).thenReturn(sourceFileId);
    when(validationRun.getProfileId()).thenReturn(profileId);
    when(validationRun.getStatus()).thenReturn(ValidationRunStatus.PENDING);

    ValidationRunResponse response = service.getValidationRunForReport(runId);

    assertThat(response)
        .isEqualTo(
            new ValidationRunResponse(
                runId,
                datasetId,
                sourceFileId,
                profileId,
                ValidationRunStatus.PENDING,
                0,
                0,
                0,
                0,
                null,
                null,
                null));
  }

  @Test
  void preservesNotFoundBehaviorAtTheReportingReadBoundary() {
    UUID runId = UUID.randomUUID();
    when(validationRunRepository.findById(runId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getValidationRunForReport(runId))
        .isInstanceOf(ValidationRunNotFoundException.class)
        .hasMessage("Validation Run '" + runId + "' was not found.");
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
    verify(operationsMetrics)
        .recordValidationProcessing(processingSample, ValidationProcessingOutcome.FAILED);
    assertEvent("validation_run.execution_failed", Level.ERROR, runId, cause);
    assertEvent("validation_run.processing_failed", Level.WARN, runId, null);
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
    verify(operationsMetrics)
        .recordValidationProcessing(processingSample, ValidationProcessingOutcome.ERROR);
    assertEvent("validation_run.execution_failed", Level.ERROR, runId, failure);
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
    verify(operationsMetrics)
        .recordValidationProcessing(processingSample, ValidationProcessingOutcome.ERROR);
    assertEvent(
        "validation_run.execution_failed", Level.ERROR, runId, processingFailure.getCause());
    assertEvent("validation_run.recovery_failed", Level.ERROR, runId, recoveryFailure);
  }

  private void assertEvent(String eventName, Level level, UUID runId, Throwable expectedThrowable) {
    List<ILoggingEvent> matchingEvents =
        logAppender.list.stream()
            .filter(
                event ->
                    event.getKeyValuePairs().stream()
                        .anyMatch(pair -> pair.key.equals("event") && pair.value.equals(eventName)))
            .toList();

    assertThat(matchingEvents).hasSize(1);
    ILoggingEvent event = matchingEvents.getFirst();
    assertThat(event.getLevel()).isEqualTo(level);
    assertThat(event.getKeyValuePairs())
        .anySatisfy(
            pair -> {
              assertThat(pair.key).isEqualTo("runId");
              assertThat(pair.value).isEqualTo(runId);
            });
    if (expectedThrowable == null) {
      assertThat(event.getThrowableProxy()).isNull();
    } else {
      assertThat(event.getThrowableProxy()).isNotNull();
      assertThat(event.getThrowableProxy().getClassName())
          .isEqualTo(expectedThrowable.getClass().getName());
    }
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
