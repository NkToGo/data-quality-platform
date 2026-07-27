package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ValidationRunService {

  private final ValidationRunLifecycleService validationRunLifecycleService;

  ValidationRunService(ValidationRunLifecycleService validationRunLifecycleService) {
    this.validationRunLifecycleService = validationRunLifecycleService;
  }

  ValidationRunResponse create(UUID fileId, CreateValidationRunRequest request) {
    UUID runId = validationRunLifecycleService.createPending(fileId, request.profileId());
    return validationRunLifecycleService.process(runId);
  }
}
