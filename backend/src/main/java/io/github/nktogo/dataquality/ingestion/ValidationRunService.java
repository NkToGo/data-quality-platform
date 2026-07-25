package io.github.nktogo.dataquality.ingestion;

import io.github.nktogo.dataquality.dataset.ValidationProfileAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRunService {

  private final ValidationRunRepository validationRunRepository;
  private final SourceFileService sourceFileService;
  private final ValidationProfileAccess validationProfileAccess;

  ValidationRunService(
      ValidationRunRepository validationRunRepository,
      SourceFileService sourceFileService,
      ValidationProfileAccess validationProfileAccess) {
    this.validationRunRepository = validationRunRepository;
    this.sourceFileService = sourceFileService;
    this.validationProfileAccess = validationProfileAccess;
  }

  @Transactional
  ValidationRunResponse create(UUID fileId, CreateValidationRunRequest request) {
    UUID sourceFileDatasetId = sourceFileService.requireDatasetId(fileId);
    UUID profileDatasetId =
        validationProfileAccess.requireValidationProfileDatasetId(request.profileId());

    if (!sourceFileDatasetId.equals(profileDatasetId)) {
      throw new ValidationRunParentMismatchException(fileId, request.profileId());
    }

    ValidationRun validationRun =
        ValidationRun.pending(sourceFileDatasetId, fileId, request.profileId());

    return toResponse(validationRunRepository.save(validationRun));
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
