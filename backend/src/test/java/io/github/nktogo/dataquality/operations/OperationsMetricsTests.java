package io.github.nktogo.dataquality.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportFormat;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportGenerationOutcome;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ValidationProcessingOutcome;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationsMetricsTests {

  private MockClock clock;
  private SimpleMeterRegistry meterRegistry;
  private OperationsMetrics operationsMetrics;

  @BeforeEach
  void setUp() {
    clock = new MockClock();
    meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    operationsMetrics = new OperationsMetrics(meterRegistry);
  }

  @Test
  void incrementsValidationRunCreationCounterWithoutDomainTags() {
    operationsMetrics.incrementValidationRunsCreated();
    operationsMetrics.incrementValidationRunsCreated();

    var counter = meterRegistry.get("dataquality.validation.runs.created").counter();
    assertThat(counter.count()).isEqualTo(2.0);
    assertThat(counter.getId().getTags()).isEmpty();
  }

  @Test
  void recordsValidationProcessingDurationWithOnlyBoundedOutcomes() {
    recordValidationDuration(ValidationProcessingOutcome.COMPLETED, Duration.ofMillis(125));
    recordValidationDuration(ValidationProcessingOutcome.FAILED, Duration.ofMillis(250));
    recordValidationDuration(ValidationProcessingOutcome.ERROR, Duration.ofMillis(500));

    assertTimer("dataquality.validation.processing.duration", "completed", 125.0);
    assertTimer("dataquality.validation.processing.duration", "failed", 250.0);
    assertTimer("dataquality.validation.processing.duration", "error", 500.0);
    assertThat(meterRegistry.find("dataquality.validation.processing.duration").timers())
        .hasSize(3);
  }

  @Test
  void incrementsReportCountersWithOnlyBoundedFormats() {
    operationsMetrics.incrementReportsGenerated(ReportFormat.JSON);
    operationsMetrics.incrementReportsGenerated(ReportFormat.CSV);
    operationsMetrics.incrementReportsGenerated(ReportFormat.CSV);

    assertThat(
            meterRegistry
                .get("dataquality.reports.generated")
                .tag("format", "json")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get("dataquality.reports.generated")
                .tag("format", "csv")
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(meterRegistry.find("dataquality.reports.generated").counters()).hasSize(2);
  }

  @Test
  void recordsReportGenerationDurationWithOnlyBoundedFormatsAndOutcomes() {
    for (ReportFormat format : ReportFormat.values()) {
      for (ReportGenerationOutcome outcome : ReportGenerationOutcome.values()) {
        var sample = operationsMetrics.startReportGeneration();
        clock.add(Duration.ofMillis(100));
        operationsMetrics.recordReportGeneration(sample, format, outcome);
      }
    }

    assertReportTimer("json", "success");
    assertReportTimer("json", "error");
    assertReportTimer("csv", "success");
    assertReportTimer("csv", "error");
    assertThat(meterRegistry.find("dataquality.report.generation.duration").timers()).hasSize(4);
  }

  @Test
  void rejectsMissingSamplesAndDimensions() {
    var validationSample = operationsMetrics.startValidationProcessing();
    var reportSample = operationsMetrics.startReportGeneration();

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                operationsMetrics.recordValidationProcessing(
                    null, ValidationProcessingOutcome.COMPLETED));
    assertThatNullPointerException()
        .isThrownBy(() -> operationsMetrics.recordValidationProcessing(validationSample, null));
    assertThatNullPointerException()
        .isThrownBy(() -> operationsMetrics.incrementReportsGenerated(null));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                operationsMetrics.recordReportGeneration(
                    null, ReportFormat.JSON, ReportGenerationOutcome.SUCCESS));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                operationsMetrics.recordReportGeneration(
                    reportSample, null, ReportGenerationOutcome.SUCCESS));
    assertThatNullPointerException()
        .isThrownBy(
            () -> operationsMetrics.recordReportGeneration(reportSample, ReportFormat.JSON, null));
  }

  private void recordValidationDuration(ValidationProcessingOutcome outcome, Duration duration) {
    var sample = operationsMetrics.startValidationProcessing();
    clock.add(duration);
    operationsMetrics.recordValidationProcessing(sample, outcome);
  }

  private void assertTimer(String meterName, String outcome, double expectedMilliseconds) {
    Timer timer = meterRegistry.get(meterName).tag("outcome", outcome).timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(expectedMilliseconds);
    assertThat(timer.getId().getTags()).hasSize(1);
  }

  private void assertReportTimer(String format, String outcome) {
    Timer timer =
        meterRegistry
            .get("dataquality.report.generation.duration")
            .tag("format", format)
            .tag("outcome", outcome)
            .timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100.0);
    assertThat(timer.getId().getTags()).hasSize(2);
  }
}
