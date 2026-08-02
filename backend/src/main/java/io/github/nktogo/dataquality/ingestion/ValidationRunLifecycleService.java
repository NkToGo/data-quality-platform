package io.github.nktogo.dataquality.ingestion;

import io.github.nktogo.dataquality.dataset.ValidationProfileAccess;
import io.github.nktogo.dataquality.validation.ValidationInput;
import io.github.nktogo.dataquality.validation.ValidationProcessingAccess;
import io.github.nktogo.dataquality.validation.ValidationResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ValidationRunLifecycleService {

  private static final int MAXIMUM_FAILURE_REASON_LENGTH = 255;
  private static final String GENERIC_FAILURE_REASON = "CSV content could not be parsed.";

  private final ValidationRunRepository validationRunRepository;
  private final SourceFileService sourceFileService;
  private final ValidationProfileAccess validationProfileAccess;
  private final CsvParser csvParser;
  private final ValidationInputAdapter validationInputAdapter;
  private final ValidationProcessingAccess validationProcessingAccess;

  ValidationRunLifecycleService(
      ValidationRunRepository validationRunRepository,
      SourceFileService sourceFileService,
      ValidationProfileAccess validationProfileAccess,
      CsvParser csvParser,
      ValidationInputAdapter validationInputAdapter,
      ValidationProcessingAccess validationProcessingAccess) {
    this.validationRunRepository = validationRunRepository;
    this.sourceFileService = sourceFileService;
    this.validationProfileAccess = validationProfileAccess;
    this.csvParser = csvParser;
    this.validationInputAdapter = validationInputAdapter;
    this.validationProcessingAccess = validationProcessingAccess;
  }

  @Transactional
  UUID createPending(UUID fileId, UUID profileId) {
    UUID sourceFileDatasetId = sourceFileService.requireDatasetId(fileId);
    UUID profileDatasetId = validationProfileAccess.requireValidationProfileDatasetId(profileId);

    if (!sourceFileDatasetId.equals(profileDatasetId)) {
      throw new ValidationRunParentMismatchException(fileId, profileId);
    }

    ValidationRun validationRun = ValidationRun.pending(sourceFileDatasetId, fileId, profileId);

    return validationRunRepository.save(validationRun).getId();
  }

  @Transactional
  ValidationRunResponse process(UUID runId) {
    ValidationRun validationRun =
        validationRunRepository
            .findById(runId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Created Validation Run '" + runId + "' was not found before processing."));

    Instant startedAt = currentTime();
    validationRun.start(startedAt);
    byte[] contentBytes = sourceFileService.requireContentBytes(validationRun.getSourceFileId());

    ParsedCsv parsedCsv;
    try {
      parsedCsv = csvParser.parse(contentBytes);
    } catch (CsvParsingException exception) {
      validationRun.failParsing(finishedAt(startedAt), safeFailureReason(exception));
      return toResponse(validationRun);
    }

    long totalRows = parsedCsv.rows().size();
    validationRun.recordParsedRowCount(totalRows);

    try {
      ValidationInput validationInput = validationInputAdapter.adapt(parsedCsv);
      ValidationResult validationResult =
          validationProcessingAccess.validateAndPersist(
              validationRun.getId(), validationRun.getProfileId(), validationInput);

      switch (validationResult) {
        case ValidationResult.Success success ->
            validationRun.complete(success.summary(), finishedAt(startedAt));
        case ValidationResult.MissingHeader missingHeader ->
            validationRun.failValidation(finishedAt(startedAt), missingHeader.reason());
      }

      validationRunRepository.flush();
    } catch (RuntimeException exception) {
      throw new ValidationProcessingFailureException(
          validationRun.getId(), startedAt, totalRows, exception);
    }

    return toResponse(validationRun);
  }

  private static Instant currentTime() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static Instant finishedAt(Instant startedAt) {
    Instant finishedAt = currentTime();
    return finishedAt.isBefore(startedAt) ? startedAt : finishedAt;
  }

  private static String safeFailureReason(CsvParsingException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank() || message.length() > MAXIMUM_FAILURE_REASON_LENGTH) {
      return GENERIC_FAILURE_REASON;
    }
    return message;
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
