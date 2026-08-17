package io.github.nktogo.dataquality.ingestion;

import io.github.nktogo.dataquality.operations.OperationsMetrics;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ValidationProcessingOutcome;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ValidationProcessingSample;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRunService implements ValidationRunAccess, ValidationRunReportAccess {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValidationRunService.class);

  private final ValidationRunLifecycleService validationRunLifecycleService;
  private final ValidationRunRecoveryService validationRunRecoveryService;
  private final ValidationRunRepository validationRunRepository;
  private final OperationsMetrics operationsMetrics;

  ValidationRunService(
      ValidationRunLifecycleService validationRunLifecycleService,
      ValidationRunRecoveryService validationRunRecoveryService,
      ValidationRunRepository validationRunRepository,
      OperationsMetrics operationsMetrics) {
    this.validationRunLifecycleService = validationRunLifecycleService;
    this.validationRunRecoveryService = validationRunRecoveryService;
    this.validationRunRepository = validationRunRepository;
    this.operationsMetrics = operationsMetrics;
  }

  ValidationRunResponse create(UUID fileId, CreateValidationRunRequest request) {
    UUID runId = validationRunLifecycleService.createPending(fileId, request.profileId());
    operationsMetrics.incrementValidationRunsCreated();
    logCreated(runId, fileId, request.profileId());
    ValidationProcessingSample processingSample = operationsMetrics.startValidationProcessing();

    try {
      return recordProcessingResult(validationRunLifecycleService.process(runId), processingSample);
    } catch (ValidationProcessingFailureException failure) {
      logExecutionFailed(runId, fileId, request.profileId(), failure.getCause());
      try {
        return recordProcessingResult(
            validationRunRecoveryService.recover(failure), processingSample);
      } catch (RuntimeException recoveryFailure) {
        operationsMetrics.recordValidationProcessing(
            processingSample, ValidationProcessingOutcome.ERROR);
        logRecoveryFailed(runId, fileId, request.profileId(), recoveryFailure);
        recoveryFailure.addSuppressed(failure);
        throw recoveryFailure;
      }
    } catch (RuntimeException executionFailure) {
      operationsMetrics.recordValidationProcessing(
          processingSample, ValidationProcessingOutcome.ERROR);
      logExecutionFailed(runId, fileId, request.profileId(), executionFailure);
      throw executionFailure;
    }
  }

  @Transactional(readOnly = true)
  List<ValidationRunResponse> getAll() {
    return validationRunRepository.findAllByOrderByIdAsc().stream()
        .map(ValidationRunService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  ValidationRunResponse getById(UUID runId) {
    return getValidationRunForReport(runId);
  }

  @Override
  @Transactional(readOnly = true)
  public ValidationRunResponse getValidationRunForReport(UUID runId) {
    return toResponse(requireExisting(runId));
  }

  @Override
  @Transactional(readOnly = true)
  public void requireValidationRun(UUID runId) {
    requireExisting(runId);
  }

  private ValidationRun requireExisting(UUID runId) {
    return validationRunRepository
        .findById(runId)
        .orElseThrow(() -> new ValidationRunNotFoundException(runId));
  }

  private ValidationRunResponse recordProcessingResult(
      ValidationRunResponse response, ValidationProcessingSample processingSample) {
    switch (response.status()) {
      case COMPLETED -> {
        operationsMetrics.recordValidationProcessing(
            processingSample, ValidationProcessingOutcome.COMPLETED);
        logFinished(response);
      }
      case FAILED -> {
        operationsMetrics.recordValidationProcessing(
            processingSample, ValidationProcessingOutcome.FAILED);
        logProcessingFailed(response);
      }
      case PENDING, PROCESSING -> {
        operationsMetrics.recordValidationProcessing(
            processingSample, ValidationProcessingOutcome.ERROR);
        LOGGER
            .atError()
            .addKeyValue("event", "validation_run.execution_failed")
            .addKeyValue("runId", response.id())
            .addKeyValue("sourceFileId", response.sourceFileId())
            .addKeyValue("profileId", response.profileId())
            .addKeyValue("status", response.status())
            .log("Validation Run processing returned a nonterminal state.");
      }
    }
    return response;
  }

  private static void logCreated(UUID runId, UUID fileId, UUID profileId) {
    LOGGER
        .atInfo()
        .addKeyValue("event", "validation_run.created")
        .addKeyValue("runId", runId)
        .addKeyValue("sourceFileId", fileId)
        .addKeyValue("profileId", profileId)
        .log("Validation Run created.");
  }

  private static void logFinished(ValidationRunResponse response) {
    addPersistedRunFields(LOGGER.atInfo().addKeyValue("event", "validation_run.finished"), response)
        .log("Validation Run processing completed.");
  }

  private static void logProcessingFailed(ValidationRunResponse response) {
    addPersistedRunFields(
            LOGGER.atWarn().addKeyValue("event", "validation_run.processing_failed"), response)
        .log("Validation Run processing produced a persisted failure.");
  }

  private static void logExecutionFailed(
      UUID runId, UUID fileId, UUID profileId, Throwable failure) {
    LOGGER
        .atError()
        .addKeyValue("event", "validation_run.execution_failed")
        .addKeyValue("runId", runId)
        .addKeyValue("sourceFileId", fileId)
        .addKeyValue("profileId", profileId)
        .setCause(failure)
        .log("Validation Run execution failed unexpectedly.");
  }

  private static void logRecoveryFailed(
      UUID runId, UUID fileId, UUID profileId, Throwable failure) {
    LOGGER
        .atError()
        .addKeyValue("event", "validation_run.recovery_failed")
        .addKeyValue("runId", runId)
        .addKeyValue("sourceFileId", fileId)
        .addKeyValue("profileId", profileId)
        .setCause(failure)
        .log("Validation Run failure recovery failed unexpectedly.");
  }

  private static org.slf4j.spi.LoggingEventBuilder addPersistedRunFields(
      org.slf4j.spi.LoggingEventBuilder event, ValidationRunResponse response) {
    return event
        .addKeyValue("runId", response.id())
        .addKeyValue("datasetId", response.datasetId())
        .addKeyValue("sourceFileId", response.sourceFileId())
        .addKeyValue("profileId", response.profileId())
        .addKeyValue("status", response.status())
        .addKeyValue("totalRows", response.totalRows())
        .addKeyValue("validRows", response.validRows())
        .addKeyValue("invalidRows", response.invalidRows())
        .addKeyValue("issueCount", response.issueCount());
  }

  private static ValidationRunResponse toResponse(ValidationRun validationRun) {
    return new ValidationRunResponse(
        validationRun.getId(),
        validationRun.getDatasetId(),
        validationRun.getSourceFileId(),
        validationRun.getProfileId(),
        validationRun.getStatus(),
        validationRun.getTotalRows(),
        validationRun.getValidRows(),
        validationRun.getInvalidRows(),
        validationRun.getIssueCount(),
        validationRun.getStartedAt(),
        validationRun.getFinishedAt(),
        validationRun.getFailureReason());
  }
}
