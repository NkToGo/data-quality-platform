package io.github.nktogo.dataquality.ingestion;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/datasets/{datasetId}/files")
class SourceFileController {

  private final SourceFileService sourceFileService;

  SourceFileController(SourceFileService sourceFileService) {
    this.sourceFileService = sourceFileService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<SourceFileResponse> upload(
      @PathVariable UUID datasetId, @RequestPart("file") MultipartFile file) {
    SourceFileResponse response = sourceFileService.upload(datasetId, file);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
