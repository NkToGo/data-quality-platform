package io.github.nktogo.dataquality.ingestion;

import io.github.nktogo.dataquality.dataset.DatasetAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
class SourceFileService {

  private static final int MAX_FILENAME_LENGTH = 255;
  private static final int MAX_CONTENT_TYPE_LENGTH = 255;

  private final SourceFileRepository sourceFileRepository;
  private final DatasetAccess datasetAccess;
  private final long maximumFileSizeBytes;

  SourceFileService(
      SourceFileRepository sourceFileRepository,
      DatasetAccess datasetAccess,
      @Value("${app.ingestion.max-file-size}") String maximumFileSize) {
    this.sourceFileRepository = sourceFileRepository;
    this.datasetAccess = datasetAccess;
    this.maximumFileSizeBytes = DataSize.parse(maximumFileSize).toBytes();
  }

  @Transactional
  SourceFileResponse upload(UUID datasetId, MultipartFile file) {
    validateReportedSize(file);
    String originalFilename = safeBasename(file.getOriginalFilename());
    String contentType = normalizedContentType(file.getContentType());
    datasetAccess.requireDataset(datasetId);

    byte[] contentBytes = readBytes(file);
    validateActualSize(contentBytes);

    String sha256 = sha256(contentBytes);
    Instant uploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    SourceFile sourceFile =
        new SourceFile(
            datasetId,
            originalFilename,
            contentType,
            contentBytes.length,
            sha256,
            contentBytes,
            uploadedAt);

    return toResponse(sourceFileRepository.save(sourceFile));
  }

  UUID requireDatasetId(UUID fileId) {
    return sourceFileRepository
        .findDatasetIdById(fileId)
        .orElseThrow(() -> new SourceFileNotFoundException(fileId));
  }

  private void validateReportedSize(MultipartFile file) {
    if (file.isEmpty() || file.getSize() <= 0) {
      throw new InvalidSourceFileException("The uploaded file must not be empty.");
    }
    if (file.getSize() > maximumFileSizeBytes) {
      throw new SourceFileTooLargeException(maximumFileSizeBytes);
    }
  }

  private void validateActualSize(byte[] contentBytes) {
    if (contentBytes.length == 0) {
      throw new InvalidSourceFileException("The uploaded file must not be empty.");
    }
    if (contentBytes.length > maximumFileSizeBytes) {
      throw new SourceFileTooLargeException(maximumFileSizeBytes);
    }
  }

  private String safeBasename(String submittedFilename) {
    if (submittedFilename == null || submittedFilename.indexOf('\0') >= 0) {
      throw new InvalidSourceFileException("The uploaded file must have a valid filename.");
    }

    String normalized = submittedFilename.replace('\\', '/');
    String basename = normalized.substring(normalized.lastIndexOf('/') + 1);

    if (!StringUtils.hasText(basename)) {
      throw new InvalidSourceFileException("The uploaded file must have a valid filename.");
    }
    if (basename.length() > MAX_FILENAME_LENGTH) {
      throw new InvalidSourceFileException(
          "The uploaded filename must not exceed " + MAX_FILENAME_LENGTH + " characters.");
    }
    if (!basename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new InvalidSourceFileException("The uploaded filename must end with '.csv'.");
    }

    return basename;
  }

  private String normalizedContentType(String submittedContentType) {
    String contentType =
        StringUtils.hasText(submittedContentType)
            ? submittedContentType
            : MediaType.APPLICATION_OCTET_STREAM_VALUE;

    if (contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
      throw new InvalidSourceFileException(
          "The uploaded content type must not exceed " + MAX_CONTENT_TYPE_LENGTH + " characters.");
    }

    return contentType;
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException exception) {
      throw new UncheckedIOException("The uploaded file could not be read.", exception);
    }
  }

  private String sha256(byte[] contentBytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(contentBytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  private static SourceFileResponse toResponse(SourceFile sourceFile) {
    return new SourceFileResponse(
        sourceFile.getId(),
        sourceFile.getDatasetId(),
        sourceFile.getOriginalFilename(),
        sourceFile.getContentType(),
        sourceFile.getSizeBytes(),
        sourceFile.getSha256(),
        sourceFile.getUploadedAt());
  }
}
