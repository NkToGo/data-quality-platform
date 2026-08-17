package io.github.nktogo.dataquality.operations;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class OperationsMetrics {

  private static final String VALIDATION_RUNS_CREATED = "dataquality.validation.runs.created";
  private static final String VALIDATION_PROCESSING_DURATION =
      "dataquality.validation.processing.duration";
  private static final String REPORTS_GENERATED = "dataquality.reports.generated";
  private static final String REPORT_GENERATION_DURATION = "dataquality.report.generation.duration";

  private final Counter validationRunsCreated;
  private final MeterRegistry meterRegistry;
  private final Map<ValidationProcessingOutcome, Timer> validationProcessingTimers;
  private final Map<ReportFormat, Counter> reportsGenerated;
  private final Map<ReportFormat, Map<ReportGenerationOutcome, Timer>> reportGenerationTimers;

  OperationsMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    this.validationRunsCreated =
        Counter.builder(VALIDATION_RUNS_CREATED)
            .description("Number of Validation Runs created")
            .register(meterRegistry);
    this.validationProcessingTimers = registerValidationProcessingTimers(meterRegistry);
    this.reportsGenerated = registerReportCounters(meterRegistry);
    this.reportGenerationTimers = registerReportGenerationTimers(meterRegistry);
  }

  public void incrementValidationRunsCreated() {
    validationRunsCreated.increment();
  }

  public ValidationProcessingSample startValidationProcessing() {
    return new ValidationProcessingSample(Timer.start(meterRegistry));
  }

  public void recordValidationProcessing(
      ValidationProcessingSample sample, ValidationProcessingOutcome outcome) {
    Objects.requireNonNull(sample, "sample must not be null")
        .stop(
            validationProcessingTimers.get(
                Objects.requireNonNull(outcome, "outcome must not be null")));
  }

  public void incrementReportsGenerated(ReportFormat format) {
    reportsGenerated.get(Objects.requireNonNull(format, "format must not be null")).increment();
  }

  public ReportGenerationSample startReportGeneration() {
    return new ReportGenerationSample(Timer.start(meterRegistry));
  }

  public void recordReportGeneration(
      ReportGenerationSample sample, ReportFormat format, ReportGenerationOutcome outcome) {
    Objects.requireNonNull(sample, "sample must not be null")
        .stop(
            reportGenerationTimers
                .get(Objects.requireNonNull(format, "format must not be null"))
                .get(Objects.requireNonNull(outcome, "outcome must not be null")));
  }

  private static Map<ValidationProcessingOutcome, Timer> registerValidationProcessingTimers(
      MeterRegistry meterRegistry) {
    Map<ValidationProcessingOutcome, Timer> timers =
        new EnumMap<>(ValidationProcessingOutcome.class);
    for (ValidationProcessingOutcome outcome : ValidationProcessingOutcome.values()) {
      timers.put(
          outcome,
          Timer.builder(VALIDATION_PROCESSING_DURATION)
              .description("Validation Run processing duration")
              .tag("outcome", outcome.tagValue)
              .register(meterRegistry));
    }
    return Map.copyOf(timers);
  }

  private static Map<ReportFormat, Counter> registerReportCounters(MeterRegistry meterRegistry) {
    Map<ReportFormat, Counter> counters = new EnumMap<>(ReportFormat.class);
    for (ReportFormat format : ReportFormat.values()) {
      counters.put(
          format,
          Counter.builder(REPORTS_GENERATED)
              .description("Number of reports generated")
              .tag("format", format.tagValue)
              .register(meterRegistry));
    }
    return Map.copyOf(counters);
  }

  private static Map<ReportFormat, Map<ReportGenerationOutcome, Timer>>
      registerReportGenerationTimers(MeterRegistry meterRegistry) {
    Map<ReportFormat, Map<ReportGenerationOutcome, Timer>> timers =
        new EnumMap<>(ReportFormat.class);
    for (ReportFormat format : ReportFormat.values()) {
      Map<ReportGenerationOutcome, Timer> formatTimers =
          new EnumMap<>(ReportGenerationOutcome.class);
      for (ReportGenerationOutcome outcome : ReportGenerationOutcome.values()) {
        formatTimers.put(
            outcome,
            Timer.builder(REPORT_GENERATION_DURATION)
                .description("Report generation duration")
                .tag("format", format.tagValue)
                .tag("outcome", outcome.tagValue)
                .register(meterRegistry));
      }
      timers.put(format, Map.copyOf(formatTimers));
    }
    return Map.copyOf(timers);
  }

  public enum ValidationProcessingOutcome {
    COMPLETED("completed"),
    FAILED("failed"),
    ERROR("error");

    private final String tagValue;

    ValidationProcessingOutcome(String tagValue) {
      this.tagValue = tagValue;
    }
  }

  public enum ReportFormat {
    JSON("json"),
    CSV("csv");

    private final String tagValue;

    ReportFormat(String tagValue) {
      this.tagValue = tagValue;
    }
  }

  public enum ReportGenerationOutcome {
    SUCCESS("success"),
    ERROR("error");

    private final String tagValue;

    ReportGenerationOutcome(String tagValue) {
      this.tagValue = tagValue;
    }
  }

  public static final class ValidationProcessingSample {

    private final Timer.Sample sample;

    private ValidationProcessingSample(Timer.Sample sample) {
      this.sample = sample;
    }

    private void stop(Timer timer) {
      sample.stop(timer);
    }
  }

  public static final class ReportGenerationSample {

    private final Timer.Sample sample;

    private ReportGenerationSample(Timer.Sample sample) {
      this.sample = sample;
    }

    private void stop(Timer timer) {
      sample.stop(timer);
    }
  }
}
