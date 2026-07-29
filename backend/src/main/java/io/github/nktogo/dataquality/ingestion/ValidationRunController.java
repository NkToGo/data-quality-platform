package io.github.nktogo.dataquality.ingestion;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class ValidationRunController {

  private final ValidationRunService validationRunService;

  ValidationRunController(ValidationRunService validationRunService) {
    this.validationRunService = validationRunService;
  }

  @PostMapping("/files/{fileId}/validation-runs")
  ResponseEntity<ValidationRunResponse> create(
      @PathVariable UUID fileId, @Valid @RequestBody CreateValidationRunRequest request) {
    ValidationRunResponse response = validationRunService.create(fileId, request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/validation-runs")
  List<ValidationRunResponse> getAll() {
    return validationRunService.getAll();
  }

  @GetMapping("/validation-runs/{runId}")
  ValidationRunResponse getById(@PathVariable UUID runId) {
    return validationRunService.getById(runId);
  }
}
