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
@RequestMapping("/api/profiles/{profileId}/rules")
class ValidationRuleController {

  private final ValidationRuleService validationRuleService;

  ValidationRuleController(ValidationRuleService validationRuleService) {
    this.validationRuleService = validationRuleService;
  }

  @PostMapping
  ResponseEntity<ValidationRuleResponse> create(
      @PathVariable UUID profileId, @Valid @RequestBody CreateValidationRuleRequest request) {
    ValidationRuleResponse response = validationRuleService.create(profileId, request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  List<ValidationRuleResponse> getAll(@PathVariable UUID profileId) {
    return validationRuleService.getAll(profileId);
  }
}
