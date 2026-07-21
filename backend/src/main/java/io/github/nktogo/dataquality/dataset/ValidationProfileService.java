package io.github.nktogo.dataquality.dataset;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationProfileService {

  private final ValidationProfileRepository validationProfileRepository;
  private final DatasetService datasetService;

  ValidationProfileService(
      ValidationProfileRepository validationProfileRepository, DatasetService datasetService) {
    this.validationProfileRepository = validationProfileRepository;
    this.datasetService = datasetService;
  }

  @Transactional
  ValidationProfileResponse create(UUID datasetId, CreateValidationProfileRequest request) {
    Dataset dataset = datasetService.requireExisting(datasetId);
    Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    ValidationProfile validationProfile = new ValidationProfile(dataset, request.name(), createdAt);

    return toResponse(validationProfileRepository.save(validationProfile));
  }

  @Transactional(readOnly = true)
  List<ValidationProfileResponse> getAll(UUID datasetId) {
    Dataset dataset = datasetService.requireExisting(datasetId);

    return validationProfileRepository.findAllByDatasetOrderByCreatedAtAscIdAsc(dataset).stream()
        .map(ValidationProfileService::toResponse)
        .toList();
  }

  ValidationProfile requireExisting(UUID profileId) {
    return validationProfileRepository
        .findById(profileId)
        .orElseThrow(() -> new ValidationProfileNotFoundException(profileId));
  }

  private static ValidationProfileResponse toResponse(ValidationProfile validationProfile) {
    return new ValidationProfileResponse(
        validationProfile.getId(),
        validationProfile.getDataset().getId(),
        validationProfile.getName(),
        validationProfile.getCreatedAt());
  }
}
