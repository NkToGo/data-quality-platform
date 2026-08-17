package io.github.nktogo.dataquality.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import io.github.nktogo.dataquality.ingestion.ValidationRunResponse;
import io.github.nktogo.dataquality.ingestion.ValidationRunStatus;
import io.github.nktogo.dataquality.operations.OperationsMetrics;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportGenerationOutcome;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportGenerationSample;
import io.github.nktogo.dataquality.validation.ValidationIssueResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ValidationReportControllerTests {

  private static final String SENSITIVE_OBSERVED_VALUE = "=DO_NOT_LOG(\"raw value\")";

  private final ValidationReportService validationReportService =
      mock(ValidationReportService.class);
  private final ValidationReportCsvWriter validationReportCsvWriter =
      mock(ValidationReportCsvWriter.class);
  private final OperationsMetrics operationsMetrics = mock(OperationsMetrics.class);
  private final ReportGenerationSample generationSample = mock(ReportGenerationSample.class);
  private final ValidationReportController controller =
      new ValidationReportController(
          validationReportService, validationReportCsvWriter, operationsMetrics);
  private final Logger controllerLogger =
      (Logger) LoggerFactory.getLogger(ValidationReportController.class);
  private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
  private boolean originalAdditive;

  @BeforeEach
  void setUp() {
    when(operationsMetrics.startReportGeneration()).thenReturn(generationSample);
    originalAdditive = controllerLogger.isAdditive();
    controllerLogger.setAdditive(false);
    logAppender.start();
    controllerLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    controllerLogger.detachAppender(logAppender);
    controllerLogger.setAdditive(originalAdditive);
    logAppender.stop();
  }

  @Test
  void generatesJsonWithSuccessMetricsAndADataSafeStructuredEvent() {
    ValidationReport report = report();
    UUID runId = report.validationRun().id();
    when(validationReportService.getReport(runId)).thenReturn(report);

    var response = controller.getReport(runId, requestWithFormat("json"));

    assertThat(response.getBody()).isSameAs(report);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .isEqualTo("validation-run-" + runId + "-report.json");
    verifyNoInteractions(validationReportCsvWriter);
    verify(operationsMetrics).incrementReportsGenerated(OperationsMetrics.ReportFormat.JSON);
    verify(operationsMetrics)
        .recordReportGeneration(
            generationSample, OperationsMetrics.ReportFormat.JSON, ReportGenerationOutcome.SUCCESS);

    ILoggingEvent event = generatedEvent();
    assertThat(event.getLevel()).isEqualTo(Level.INFO);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getKeyValuePairs())
        .anySatisfy(
            pair -> {
              assertThat(pair.key).isEqualTo("runId");
              assertThat(pair.value).isEqualTo(runId);
            })
        .anySatisfy(
            pair -> {
              assertThat(pair.key).isEqualTo("format");
              assertThat(pair.value).isEqualTo("json");
            });
    assertThat(event.getFormattedMessage()).doesNotContain(SENSITIVE_OBSERVED_VALUE);
    assertThat(event.getKeyValuePairs().toString()).doesNotContain(SENSITIVE_OBSERVED_VALUE);
  }

  @Test
  void generatesCsvBeforeRecordingSuccess() {
    ValidationReport report = report();
    UUID runId = report.validationRun().id();
    byte[] csv = "header\r\nvalue\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(validationReportService.getReport(runId)).thenReturn(report);
    when(validationReportCsvWriter.write(report)).thenReturn(csv);

    var response = controller.getReport(runId, requestWithFormat("csv"));

    assertThat(response.getBody()).isSameAs(csv);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("text/csv;charset=UTF-8"));
    verify(operationsMetrics).incrementReportsGenerated(OperationsMetrics.ReportFormat.CSV);
    verify(operationsMetrics)
        .recordReportGeneration(
            generationSample, OperationsMetrics.ReportFormat.CSV, ReportGenerationOutcome.SUCCESS);
    assertThat(generatedEvent().getKeyValuePairs())
        .anySatisfy(
            pair -> {
              assertThat(pair.key).isEqualTo("format");
              assertThat(pair.value).isEqualTo("csv");
            });
  }

  @Test
  void recordsAnErrorAndDoesNotClaimAReportWhenTheSnapshotCannotBeRead() {
    ValidationReport report = report();
    UUID runId = report.validationRun().id();
    IllegalStateException failure = new IllegalStateException("Database unavailable.");
    when(validationReportService.getReport(runId)).thenThrow(failure);

    assertThatThrownBy(() -> controller.getReport(runId, requestWithFormat("json")))
        .isSameAs(failure);

    verify(operationsMetrics)
        .recordReportGeneration(
            generationSample, OperationsMetrics.ReportFormat.JSON, ReportGenerationOutcome.ERROR);
    verify(operationsMetrics, never()).incrementReportsGenerated(any());
    assertThat(logAppender.list).noneMatch(this::isGeneratedEvent);
  }

  @Test
  void recordsAnErrorAndDoesNotClaimAReportWhenCsvRenderingFails() {
    ValidationReport report = report();
    UUID runId = report.validationRun().id();
    IllegalStateException failure = new IllegalStateException("CSV rendering failed.");
    when(validationReportService.getReport(runId)).thenReturn(report);
    when(validationReportCsvWriter.write(report)).thenThrow(failure);

    assertThatThrownBy(() -> controller.getReport(runId, requestWithFormat("csv")))
        .isSameAs(failure);

    verify(operationsMetrics)
        .recordReportGeneration(
            generationSample, OperationsMetrics.ReportFormat.CSV, ReportGenerationOutcome.ERROR);
    verify(operationsMetrics, never()).incrementReportsGenerated(any());
    assertThat(logAppender.list).noneMatch(this::isGeneratedEvent);
  }

  private HttpServletRequest requestWithFormat(String format) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameterValues("format")).thenReturn(new String[] {format});
    return request;
  }

  private ILoggingEvent generatedEvent() {
    List<ILoggingEvent> matchingEvents =
        logAppender.list.stream().filter(this::isGeneratedEvent).toList();
    assertThat(matchingEvents).hasSize(1);
    return matchingEvents.getFirst();
  }

  private boolean isGeneratedEvent(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .anyMatch(
            pair -> pair.key.equals("event") && pair.value.equals("validation_report.generated"));
  }

  private ValidationReport report() {
    UUID runId = UUID.randomUUID();
    ValidationRunResponse run =
        new ValidationRunResponse(
            runId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            ValidationRunStatus.COMPLETED,
            3,
            1,
            2,
            1,
            Instant.parse("2026-08-11T12:00:00.123456Z"),
            Instant.parse("2026-08-11T12:00:01.123456Z"),
            null);
    ValidationIssueResponse issue =
        new ValidationIssueResponse(
            UUID.randomUUID(),
            runId,
            2,
            "email",
            ValidationRuleType.REQUIRED_FIELD,
            ValidationRuleSeverity.ERROR,
            "Value is required.",
            SENSITIVE_OBSERVED_VALUE);
    return new ValidationReport(run, List.of(issue));
  }
}
