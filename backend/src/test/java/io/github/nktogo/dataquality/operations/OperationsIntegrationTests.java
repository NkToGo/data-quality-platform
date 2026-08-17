package io.github.nktogo.dataquality.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportFormat;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ReportGenerationOutcome;
import io.github.nktogo.dataquality.operations.OperationsMetrics.ValidationProcessingOutcome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsIntegrationTests {

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private OperationsMetrics operationsMetrics;

  @Autowired private Environment environment;

  @BeforeAll
  void recordCustomMetrics() {
    operationsMetrics.incrementValidationRunsCreated();

    var validationSample = operationsMetrics.startValidationProcessing();
    operationsMetrics.recordValidationProcessing(
        validationSample, ValidationProcessingOutcome.COMPLETED);

    operationsMetrics.incrementReportsGenerated(ReportFormat.JSON);
    var reportSample = operationsMetrics.startReportGeneration();
    operationsMetrics.recordReportGeneration(
        reportSample, ReportFormat.JSON, ReportGenerationOutcome.SUCCESS);
  }

  @Test
  void exposesHealthWithoutDetails() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  void configuresLogstashStructuredConsoleOutput() {
    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
  }

  @Test
  void exposesRuntimeAndCustomMetricNames() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names", hasItem("jvm.memory.used")))
        .andExpect(jsonPath("$.names", hasItem("dataquality.validation.runs.created")))
        .andExpect(jsonPath("$.names", hasItem("dataquality.validation.processing.duration")))
        .andExpect(jsonPath("$.names", hasItem("dataquality.reports.generated")))
        .andExpect(jsonPath("$.names", hasItem("dataquality.report.generation.duration")));
  }

  @Test
  void exposesJvmMetric() throws Exception {
    mockMvc
        .perform(get("/actuator/metrics/jvm.memory.used"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("jvm.memory.used"))
        .andExpect(jsonPath("$.measurements.length()").value(greaterThanOrEqualTo(1)));
  }

  @Test
  void selectsOnlyLockedCustomMetricTagsAndIncludesApplicationTag() throws Exception {
    mockMvc
        .perform(
            get("/actuator/metrics/dataquality.validation.processing.duration")
                .queryParam("tag", "outcome:completed")
                .queryParam("tag", "application:data-quality-backend"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("dataquality.validation.processing.duration"))
        .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value", hasItem(1.0)));

    mockMvc
        .perform(
            get("/actuator/metrics/dataquality.report.generation.duration")
                .queryParam("tag", "format:json")
                .queryParam("tag", "outcome:success")
                .queryParam("tag", "application:data-quality-backend"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("dataquality.report.generation.duration"))
        .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value", hasItem(1.0)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"beans", "configprops", "env", "heapdump", "loggers", "mappings", "threaddump"})
  void doesNotExposeSensitiveActuatorEndpoints(String endpoint) throws Exception {
    mockMvc.perform(get("/actuator/{endpoint}", endpoint)).andExpect(status().isNotFound());
  }
}
