package io.github.nktogo.dataquality.reporting;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ValidationReportController {

  private static final MediaType CSV_MEDIA_TYPE =
      new MediaType("text", "csv", StandardCharsets.UTF_8);

  private final ValidationReportService validationReportService;
  private final ValidationReportCsvWriter validationReportCsvWriter;

  ValidationReportController(
      ValidationReportService validationReportService,
      ValidationReportCsvWriter validationReportCsvWriter) {
    this.validationReportService = validationReportService;
    this.validationReportCsvWriter = validationReportCsvWriter;
  }

  @GetMapping("/api/validation-runs/{runId}/report")
  ResponseEntity<?> getReport(@PathVariable UUID runId, HttpServletRequest request) {
    String[] formatValues = request.getParameterValues("format");
    ReportFormat format = ReportFormat.parse(formatValues == null ? null : List.of(formatValues));

    ValidationReport report = validationReportService.getReport(runId);
    ResponseEntity<?> response =
        switch (format) {
          case JSON -> downloadResponse(runId, format, MediaType.APPLICATION_JSON).body(report);
          case CSV ->
              downloadResponse(runId, format, CSV_MEDIA_TYPE)
                  .body(validationReportCsvWriter.write(report));
        };

    return response;
  }

  private ResponseEntity.BodyBuilder downloadResponse(
      UUID runId, ReportFormat format, MediaType mediaType) {
    String filename = "validation-run-" + runId + "-report." + format.queryValue();
    String contentDisposition =
        ContentDisposition.attachment().filename(filename).build().toString();

    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
        .header(HttpHeaders.CACHE_CONTROL, "no-store");
  }
}
