package io.github.nktogo.dataquality.reporting;

import io.github.nktogo.dataquality.operations.OperationsMetrics;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportGenerationOutcome;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportGenerationSample;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ValidationReportController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValidationReportController.class);
  private static final MediaType CSV_MEDIA_TYPE =
      new MediaType("text", "csv", StandardCharsets.UTF_8);

  private final ValidationReportService validationReportService;
  private final ValidationReportCsvWriter validationReportCsvWriter;
  private final OperationsMetrics operationsMetrics;

  ValidationReportController(
      ValidationReportService validationReportService,
      ValidationReportCsvWriter validationReportCsvWriter,
      OperationsMetrics operationsMetrics) {
    this.validationReportService = validationReportService;
    this.validationReportCsvWriter = validationReportCsvWriter;
    this.operationsMetrics = operationsMetrics;
  }

  @GetMapping("/api/validation-runs/{runId}/report")
  ResponseEntity<?> getReport(@PathVariable UUID runId, HttpServletRequest request) {
    String[] formatValues = request.getParameterValues("format");
    ReportFormat format = ReportFormat.parse(formatValues == null ? null : List.of(formatValues));
    ReportGenerationSample generationSample = operationsMetrics.startReportGeneration();

    try {
      ValidationReport report = validationReportService.getReport(runId);
      ResponseEntity<?> response =
          switch (format) {
            case JSON -> downloadResponse(runId, format, MediaType.APPLICATION_JSON).body(report);
            case CSV ->
                downloadResponse(runId, format, CSV_MEDIA_TYPE)
                    .body(validationReportCsvWriter.write(report));
          };

      OperationsMetrics.ReportFormat metricsFormat = toMetricsFormat(format);
      operationsMetrics.incrementReportsGenerated(metricsFormat);
      operationsMetrics.recordReportGeneration(
          generationSample, metricsFormat, ReportGenerationOutcome.SUCCESS);
      logGenerated(report, format);
      return response;
    } catch (RuntimeException failure) {
      operationsMetrics.recordReportGeneration(
          generationSample, toMetricsFormat(format), ReportGenerationOutcome.ERROR);
      throw failure;
    }
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

  private static OperationsMetrics.ReportFormat toMetricsFormat(ReportFormat format) {
    return switch (format) {
      case JSON -> OperationsMetrics.ReportFormat.JSON;
      case CSV -> OperationsMetrics.ReportFormat.CSV;
    };
  }

  private static void logGenerated(ValidationReport report, ReportFormat format) {
    var validationRun = report.validationRun();
    LOGGER
        .atInfo()
        .addKeyValue("event", "validation_report.generated")
        .addKeyValue("runId", validationRun.id())
        .addKeyValue("datasetId", validationRun.datasetId())
        .addKeyValue("sourceFileId", validationRun.sourceFileId())
        .addKeyValue("profileId", validationRun.profileId())
        .addKeyValue("status", validationRun.status())
        .addKeyValue("format", format.queryValue())
        .addKeyValue("totalRows", validationRun.totalRows())
        .addKeyValue("validRows", validationRun.validRows())
        .addKeyValue("invalidRows", validationRun.invalidRows())
        .addKeyValue("issueCount", validationRun.issueCount())
        .log("Validation report generated.");
  }
}
