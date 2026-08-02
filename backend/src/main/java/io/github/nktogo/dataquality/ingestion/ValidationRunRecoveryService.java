package io.github.nktogo.dataquality.ingestion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRunRecoveryService {

  private static final String FAILURE_REASON = "Validation processing failed.";

  private final ValidationRunRepository validationRunRepository;

  ValidationRunRecoveryService(ValidationRunRepository validationRunRepository) {
    this.validationRunRepository = validationRunRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  ValidationRunResponse recover(ValidationProcessingFailureException failure) {
    ValidationRun validationRun =
        validationRunRepository
            .findById(failure.runId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Validation Run '" + failure.runId() + "' was not found during recovery."));

    validationRun.start(failure.startedAt());
    validationRun.recordParsedRowCount(failure.totalRows());
    validationRun.failValidation(finishedAt(failure.startedAt()), FAILURE_REASON);
    validationRunRepository.flush();

    return toResponse(validationRun);
  }

  private static Instant currentTime() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static Instant finishedAt(Instant startedAt) {
    Instant finishedAt = currentTime();
    return finishedAt.isBefore(startedAt) ? startedAt : finishedAt;
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
