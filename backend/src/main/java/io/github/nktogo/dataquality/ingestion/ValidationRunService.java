package io.github.nktogo.dataquality.ingestion;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRunService implements ValidationRunAccess {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValidationRunService.class);

  private final ValidationRunLifecycleService validationRunLifecycleService;
  private final ValidationRunRecoveryService validationRunRecoveryService;
  private final ValidationRunRepository validationRunRepository;

  ValidationRunService(
      ValidationRunLifecycleService validationRunLifecycleService,
      ValidationRunRecoveryService validationRunRecoveryService,
      ValidationRunRepository validationRunRepository) {
    this.validationRunLifecycleService = validationRunLifecycleService;
    this.validationRunRecoveryService = validationRunRecoveryService;
    this.validationRunRepository = validationRunRepository;
  }

  ValidationRunResponse create(UUID fileId, CreateValidationRunRequest request) {
    UUID runId = validationRunLifecycleService.createPending(fileId, request.profileId());
    try {
      return validationRunLifecycleService.process(runId);
    } catch (ValidationProcessingFailureException failure) {
      LOGGER.error(
          "Validation processing failed for Validation Run '{}'; attempting recovery.",
          failure.runId(),
          failure.getCause());
      try {
        return validationRunRecoveryService.recover(failure);
      } catch (RuntimeException recoveryFailure) {
        recoveryFailure.addSuppressed(failure);
        throw recoveryFailure;
      }
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
