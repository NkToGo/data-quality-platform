package io.github.nktogo.dataquality.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRunService {

  private final ValidationRunLifecycleService validationRunLifecycleService;
  private final ValidationRunRepository validationRunRepository;

  ValidationRunService(
      ValidationRunLifecycleService validationRunLifecycleService,
      ValidationRunRepository validationRunRepository) {
    this.validationRunLifecycleService = validationRunLifecycleService;
    this.validationRunRepository = validationRunRepository;
  }

  ValidationRunResponse create(UUID fileId, CreateValidationRunRequest request) {
    UUID runId = validationRunLifecycleService.createPending(fileId, request.profileId());
    return validationRunLifecycleService.process(runId);
  }

  @Transactional(readOnly = true)
  List<ValidationRunResponse> getAll() {
    return validationRunRepository.findAllByOrderByIdAsc().stream()
        .map(ValidationRunService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  ValidationRunResponse getById(UUID runId) {
    return validationRunRepository
        .findById(runId)
        .map(ValidationRunService::toResponse)
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
