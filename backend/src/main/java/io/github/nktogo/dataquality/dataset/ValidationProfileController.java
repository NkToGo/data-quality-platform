package io.github.nktogo.dataquality.dataset;

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
@RequestMapping("/api/datasets/{datasetId}/profiles")
class ValidationProfileController {

  private final ValidationProfileService validationProfileService;

  ValidationProfileController(ValidationProfileService validationProfileService) {
    this.validationProfileService = validationProfileService;
  }

  @PostMapping
  ResponseEntity<ValidationProfileResponse> create(
      @PathVariable UUID datasetId, @Valid @RequestBody CreateValidationProfileRequest request) {
    ValidationProfileResponse response = validationProfileService.create(datasetId, request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  List<ValidationProfileResponse> getAll(@PathVariable UUID datasetId) {
    return validationProfileService.getAll(datasetId);
  }
}
