package io.github.nktogo.dataquality.validation;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/validation-runs/{runId}/issues")
class ValidationIssueController {

  private final ValidationIssueService validationIssueService;

  ValidationIssueController(ValidationIssueService validationIssueService) {
    this.validationIssueService = validationIssueService;
  }

  @GetMapping
  List<ValidationIssueResponse> getAll(@PathVariable UUID runId) {
    return validationIssueService.getAll(runId);
  }
}
