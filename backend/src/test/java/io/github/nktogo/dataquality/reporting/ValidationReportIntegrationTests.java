package io.github.nktogo.dataquality.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import io.github.nktogo.dataquality.ingestion.ValidationRunStatus;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ValidationReportIntegrationTests {

  private static final byte[] CONTENT_BYTES =
      "name,age\r\nAlice,42\r\n".getBytes(StandardCharsets.UTF_8);
  private static final Instant CREATED_AT = Instant.parse("2026-08-11T11:00:00.123456Z");
  private static final Instant STARTED_AT = Instant.parse("2026-08-11T12:00:00.123456Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-08-11T12:00:02.123456Z");
  private static final MediaType CSV_MEDIA_TYPE =
      new MediaType("text", "csv", StandardCharsets.UTF_8);
  private static final String INVALID_FORMAT_DETAIL =
      "Query parameter 'format' must be exactly one of: json, csv.";

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void deleteReportsRunsAndParents() {
    jdbcTemplate.update("delete from validation_issue");
    jdbcTemplate.update("delete from validation_run");
    jdbcTemplate.update("delete from validation_rule");
    jdbcTemplate.update("delete from source_file");
    jdbcTemplate.update("delete from validation_profile");
    jdbcTemplate.update("delete from dataset");
  }

  @Test
  void exportsExactJsonContractHeadersAndPersistedIssueOrder() throws Exception {
    RunFixture run = insertRun(ValidationRunStatus.COMPLETED, 5, 3, 2, 3);
    IssueFixture third =
        insertIssue(
            UUID.randomUUID(),
            run.id(),
            3,
            "same",
            ValidationRuleType.UNIQUENESS,
            ValidationRuleSeverity.ERROR,
            "Formula-like value is persisted literally.",
            "=2+3");
    IssueFixture second =
        insertIssue(
            UUID.randomUUID(),
            run.id(),
            2,
            "beta",
            ValidationRuleType.DATA_TYPE,
            ValidationRuleSeverity.WARNING,
            "An empty value remains distinct from null.",
            "");
    IssueFixture first =
        insertIssue(
            UUID.randomUUID(),
            run.id(),
            2,
            "alpha",
            ValidationRuleType.REQUIRED_FIELD,
            ValidationRuleSeverity.ERROR,
            "Markup <strong>renders as data</strong>.",
            null);

    var response =
        mockMvc
            .perform(
                get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "json"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"validation-run-" + run.id() + "-report.json\""))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$", aMapWithSize(2)))
            .andExpect(jsonPath("$.validationRun", aMapWithSize(12)))
            .andExpect(jsonPath("$.validationRun.id").value(run.id().toString()))
            .andExpect(jsonPath("$.validationRun.datasetId").value(run.datasetId().toString()))
            .andExpect(
                jsonPath("$.validationRun.sourceFileId").value(run.sourceFileId().toString()))
            .andExpect(jsonPath("$.validationRun.profileId").value(run.profileId().toString()))
            .andExpect(jsonPath("$.validationRun.status").value("COMPLETED"))
            .andExpect(jsonPath("$.validationRun.totalRows").value(5))
            .andExpect(jsonPath("$.validationRun.validRows").value(3))
            .andExpect(jsonPath("$.validationRun.invalidRows").value(2))
            .andExpect(jsonPath("$.validationRun.issueCount").value(3))
            .andExpect(jsonPath("$.validationRun.startedAt").value(STARTED_AT.toString()))
            .andExpect(jsonPath("$.validationRun.finishedAt").value(FINISHED_AT.toString()))
            .andExpect(jsonPath("$.validationRun.failureReason").value(nullValue()))
            .andExpect(jsonPath("$.issues", hasSize(3)));

    assertJsonIssue(response, 0, first);
    assertJsonIssue(response, 1, second);
    assertJsonIssue(response, 2, third);
  }

  @Test
  void exportsExactCsvContractAndPreservesSpecialValues() throws Exception {
    RunFixture run = insertRun(ValidationRunStatus.COMPLETED, 4, 2, 2, 3);
    IssueFixture formula =
        insertIssue(
            UUID.randomUUID(),
            run.id(),
            4,
            "zeta",
            ValidationRuleType.NUMERIC_RANGE,
            ValidationRuleSeverity.ERROR,
            "Comma, quote \" and CRLF\r\nremain data.",
            "=HYPERLINK(\"https://example.invalid\")");
    IssueFixture empty =
        insertIssue(
            UUID.randomUUID(),
            run.id(),
            2,
            "beta",
            ValidationRuleType.REQUIRED_FIELD,
            ValidationRuleSeverity.WARNING,
            "Empty",
            "");
    IssueFixture whitespace =
        insertIssue(
            UUID.randomUUID(),
            run.id(),
            2,
            "alpha",
            ValidationRuleType.DATA_TYPE,
            ValidationRuleSeverity.ERROR,
            "Unicode Gr\u00fc\u00dfe \u6771\u4eac and <tag>.",
            " \t ");

    byte[] responseBytes =
        mockMvc
            .perform(
                get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "csv"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(CSV_MEDIA_TYPE))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"validation-run-" + run.id() + "-report.csv\""))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(responseBytes[0]).isEqualTo((byte) 'v');
    String csv = new String(responseBytes, StandardCharsets.UTF_8);
    assertThat(csv).endsWith("\r\n");
    ParsedCsv parsed = parse(csv);
    assertThat(parsed.headers())
        .containsExactly(
            "validation_run_id",
            "dataset_id",
            "source_file_id",
            "profile_id",
            "status",
            "total_rows",
            "valid_rows",
            "invalid_rows",
            "issue_count",
            "started_at",
            "finished_at",
            "failure_reason",
            "issue_present",
            "issue_id",
            "issue_run_id",
            "row_number",
            "field_name",
            "rule_type",
            "severity",
            "message",
            "observed_value_present",
            "observed_value");
    assertThat(parsed.records()).hasSize(3);
    assertCsvIssue(parsed.records().get(0), run, whitespace, true);
    assertCsvIssue(parsed.records().get(1), run, empty, true);
    assertCsvIssue(parsed.records().get(2), run, formula, true);
    assertThat(parsed.records().get(2).get("observed_value"))
        .isEqualTo(formula.observedValue())
        .startsWith("=");
  }

  @Test
  void jsonAndCsvRepresentTheSamePersistedSnapshot() throws Exception {
    RunFixture run = insertRun(ValidationRunStatus.COMPLETED, 3, 2, 1, 1);
    insertIssue(
        UUID.randomUUID(),
        run.id(),
        3,
        "email",
        ValidationRuleType.REQUIRED_FIELD,
        ValidationRuleSeverity.ERROR,
        "Value is required.",
        "");

    String json =
        mockMvc
            .perform(
                get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String csv =
        mockMvc
            .perform(
                get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "csv"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    ParsedCsv parsed = parse(csv);
    assertThat(parsed.records()).hasSize(1);
    assertJsonCsvParity(json, parsed.records().getFirst());
  }

  @ParameterizedTest
  @EnumSource(ValidationRunStatus.class)
  void exportsEveryPersistedStatusWithoutIssues(ValidationRunStatus statusValue) throws Exception {
    RunFixture run =
        insertRun(statusValue, statusValue == ValidationRunStatus.PROCESSING ? 5 : 0, 0, 0, 0);

    mockMvc
        .perform(get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationRun.status").value(statusValue.name()))
        .andExpect(jsonPath("$.issues").isEmpty());
  }

  @Test
  void csvWithoutIssuesContainsOneRunOnlyRowAndExplicitPresenceFlags() throws Exception {
    RunFixture run = insertRun(ValidationRunStatus.PENDING, 0, 0, 0, 0);

    byte[] responseBytes =
        mockMvc
            .perform(
                get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "csv"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    ParsedCsv parsed = parse(new String(responseBytes, StandardCharsets.UTF_8));

    assertThat(parsed.records()).hasSize(1);
    CSVRecord record = parsed.records().getFirst();
    assertCsvRun(record, run);
    assertThat(record.get("issue_present")).isEqualTo("false");
    assertThat(record.get("observed_value_present")).isEqualTo("false");
    assertThat(
            List.of(
                "issue_id",
                "issue_run_id",
                "row_number",
                "field_name",
                "rule_type",
                "severity",
                "message",
                "observed_value"))
        .allSatisfy(headerName -> assertThat(record.get(headerName)).isEmpty());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidFormatRequests")
  void returnsApplicationOwnedProblemDetailForEveryInvalidFormatShape(
      String description, List<String> formatValues) throws Exception {
    UUID unknownRunId = UUID.randomUUID();
    MockHttpServletRequestBuilder request =
        get("/api/validation-runs/{runId}/report", unknownRunId);
    if (formatValues != null) {
      request.queryParam("format", formatValues.toArray(String[]::new));
    }

    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$", aMapWithSize(4)))
        .andExpect(jsonPath("$.type").doesNotExist())
        .andExpect(jsonPath("$.title").value("Invalid report format"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value(INVALID_FORMAT_DETAIL))
        .andExpect(
            jsonPath("$.instance").value("/api/validation-runs/" + unknownRunId + "/report"));
  }

  @ParameterizedTest
  @MethodSource("supportedFormats")
  void reusesValidationRunNotFoundProblemDetailForUnknownRun(String format) throws Exception {
    UUID runId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/validation-runs/{runId}/report", runId).queryParam("format", format))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$", aMapWithSize(4)))
        .andExpect(jsonPath("$.type").doesNotExist())
        .andExpect(jsonPath("$.title").value("Validation Run not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Validation Run '" + runId + "' was not found."))
        .andExpect(jsonPath("$.instance").value("/api/validation-runs/" + runId + "/report"));
  }

  @Test
  void rejectsMalformedRunIdAndDoesNotExposeAWriter() throws Exception {
    mockMvc
        .perform(get("/api/validation-runs/not-a-uuid/report").queryParam("format", "json"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/validation-runs/{runId}/report", UUID.randomUUID())
                .queryParam("format", "json"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void bothExportsLeaveThePersistedRunAndIssuesUnchanged() throws Exception {
    RunFixture run = insertRun(ValidationRunStatus.COMPLETED, 2, 1, 1, 1);
    insertIssue(
        UUID.randomUUID(),
        run.id(),
        2,
        "name",
        ValidationRuleType.REQUIRED_FIELD,
        ValidationRuleSeverity.ERROR,
        "Value is required.",
        "");
    List<Map<String, Object>> runsBefore = readRuns(run.id());
    List<Map<String, Object>> issuesBefore = readIssues(run.id());

    mockMvc
        .perform(get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "json"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/validation-runs/{runId}/report", run.id()).queryParam("format", "csv"))
        .andExpect(status().isOk());

    assertThat(readRuns(run.id())).containsExactlyElementsOf(runsBefore);
    assertThat(readIssues(run.id())).containsExactlyElementsOf(issuesBefore);
  }

  private static Stream<Arguments> invalidFormatRequests() {
    return Stream.of(
        Arguments.of("missing", null),
        Arguments.of("blank", List.of("")),
        Arguments.of("whitespace", List.of(" ")),
        Arguments.of("uppercase", List.of("JSON")),
        Arguments.of("mixed case", List.of("Csv")),
        Arguments.of("unsupported", List.of("xml")),
        Arguments.of("trailing whitespace", List.of("json ")),
        Arguments.of("repeated alternatives", List.of("json", "csv")),
        Arguments.of("repeated same value", List.of("json", "json")),
        Arguments.of("comma separated", List.of("json,csv")));
  }

  private static Stream<String> supportedFormats() {
    return Stream.of("json", "csv");
  }

  private void assertJsonIssue(
      org.springframework.test.web.servlet.ResultActions response, int index, IssueFixture expected)
      throws Exception {
    String path = "$.issues[" + index + "]";
    response
        .andExpect(jsonPath(path, aMapWithSize(8)))
        .andExpect(jsonPath(path + ".id").value(expected.id().toString()))
        .andExpect(jsonPath(path + ".runId").value(expected.runId().toString()))
        .andExpect(jsonPath(path + ".rowNumber").value(expected.rowNumber()))
        .andExpect(jsonPath(path + ".fieldName").value(expected.fieldName()))
        .andExpect(jsonPath(path + ".ruleType").value(expected.ruleType().name()))
        .andExpect(jsonPath(path + ".severity").value(expected.severity().name()))
        .andExpect(jsonPath(path + ".message").value(expected.message()));
    if (expected.observedValue() == null) {
      response.andExpect(jsonPath(path + ".observedValue").value(nullValue()));
    } else {
      response.andExpect(jsonPath(path + ".observedValue").value(expected.observedValue()));
    }
  }

  private ParsedCsv parse(String csv) throws IOException {
    CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
    try (CSVParser parser = format.parse(new StringReader(csv))) {
      return new ParsedCsv(parser.getHeaderNames(), parser.getRecords());
    }
  }

  private void assertCsvIssue(
      CSVRecord record, RunFixture run, IssueFixture issue, boolean observedValuePresent) {
    assertCsvRun(record, run);
    assertThat(record.get("issue_present")).isEqualTo("true");
    assertThat(record.get("issue_id")).isEqualTo(issue.id().toString());
    assertThat(record.get("issue_run_id")).isEqualTo(issue.runId().toString());
    assertThat(record.get("row_number")).isEqualTo(Long.toString(issue.rowNumber()));
    assertThat(record.get("field_name")).isEqualTo(issue.fieldName());
    assertThat(record.get("rule_type")).isEqualTo(issue.ruleType().name());
    assertThat(record.get("severity")).isEqualTo(issue.severity().name());
    assertThat(record.get("message")).isEqualTo(issue.message());
    assertThat(record.get("observed_value_present"))
        .isEqualTo(Boolean.toString(observedValuePresent));
    assertThat(record.get("observed_value"))
        .isEqualTo(issue.observedValue() == null ? "" : issue.observedValue());
  }

  private void assertCsvRun(CSVRecord record, RunFixture run) {
    assertThat(record.get("validation_run_id")).isEqualTo(run.id().toString());
    assertThat(record.get("dataset_id")).isEqualTo(run.datasetId().toString());
    assertThat(record.get("source_file_id")).isEqualTo(run.sourceFileId().toString());
    assertThat(record.get("profile_id")).isEqualTo(run.profileId().toString());
    assertThat(record.get("status")).isEqualTo(run.status().name());
    assertThat(record.get("total_rows")).isEqualTo(Long.toString(run.totalRows()));
    assertThat(record.get("valid_rows")).isEqualTo(Long.toString(run.validRows()));
    assertThat(record.get("invalid_rows")).isEqualTo(Long.toString(run.invalidRows()));
    assertThat(record.get("issue_count")).isEqualTo(Long.toString(run.issueCount()));
    assertThat(record.get("started_at"))
        .isEqualTo(run.startedAt() == null ? "" : run.startedAt().toString());
    assertThat(record.get("finished_at"))
        .isEqualTo(run.finishedAt() == null ? "" : run.finishedAt().toString());
    assertThat(record.get("failure_reason"))
        .isEqualTo(run.failureReason() == null ? "" : run.failureReason());
  }

  @SuppressWarnings("unchecked")
  private void assertJsonCsvParity(String json, CSVRecord csv) {
    Map<String, Object> validationRun = JsonPath.read(json, "$.validationRun");
    List<Map<String, Object>> issues = JsonPath.read(json, "$.issues");
    assertThat(issues).hasSize(1);
    Map<String, Object> issue = issues.getFirst();

    Map<String, String> runColumns =
        Map.ofEntries(
            Map.entry("validation_run_id", "id"),
            Map.entry("dataset_id", "datasetId"),
            Map.entry("source_file_id", "sourceFileId"),
            Map.entry("profile_id", "profileId"),
            Map.entry("status", "status"),
            Map.entry("total_rows", "totalRows"),
            Map.entry("valid_rows", "validRows"),
            Map.entry("invalid_rows", "invalidRows"),
            Map.entry("issue_count", "issueCount"),
            Map.entry("started_at", "startedAt"),
            Map.entry("finished_at", "finishedAt"),
            Map.entry("failure_reason", "failureReason"));
    Map<String, String> issueColumns =
        Map.ofEntries(
            Map.entry("issue_id", "id"),
            Map.entry("issue_run_id", "runId"),
            Map.entry("row_number", "rowNumber"),
            Map.entry("field_name", "fieldName"),
            Map.entry("rule_type", "ruleType"),
            Map.entry("severity", "severity"),
            Map.entry("message", "message"),
            Map.entry("observed_value", "observedValue"));

    runColumns.forEach(
        (csvColumn, jsonField) ->
            assertThat(csv.get(csvColumn)).isEqualTo(csvValue(validationRun.get(jsonField))));
    issueColumns.forEach(
        (csvColumn, jsonField) ->
            assertThat(csv.get(csvColumn)).isEqualTo(csvValue(issue.get(jsonField))));
    assertThat(csv.get("issue_present")).isEqualTo("true");
    assertThat(csv.get("observed_value_present"))
        .isEqualTo(Boolean.toString(issue.get("observedValue") != null));
  }

  private String csvValue(Object jsonValue) {
    return jsonValue == null ? "" : jsonValue.toString();
  }

  private RunFixture insertRun(
      ValidationRunStatus status,
      long totalRows,
      long validRows,
      long invalidRows,
      long issueCount) {
    UUID datasetId = insertDataset();
    UUID sourceFileId = insertSourceFile(datasetId);
    UUID profileId = insertValidationProfile(datasetId);
    UUID runId = UUID.randomUUID();
    Instant startedAt = status == ValidationRunStatus.PENDING ? null : STARTED_AT;
    Instant finishedAt =
        status == ValidationRunStatus.COMPLETED || status == ValidationRunStatus.FAILED
            ? FINISHED_AT
            : null;
    String failureReason =
        status == ValidationRunStatus.FAILED ? "Persisted validation failure." : null;

    jdbcTemplate.update(
        """
        insert into validation_run
          (id, dataset_id, source_file_id, profile_id, status, total_rows, valid_rows,
           invalid_rows, issue_count, started_at, finished_at, failure_reason)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        runId,
        datasetId,
        sourceFileId,
        profileId,
        status.name(),
        totalRows,
        validRows,
        invalidRows,
        issueCount,
        startedAt == null ? null : Timestamp.from(startedAt),
        finishedAt == null ? null : Timestamp.from(finishedAt),
        failureReason);

    return new RunFixture(
        runId,
        datasetId,
        sourceFileId,
        profileId,
        status,
        totalRows,
        validRows,
        invalidRows,
        issueCount,
        startedAt,
        finishedAt,
        failureReason);
  }

  private UUID insertDataset() {
    UUID datasetId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into dataset (id, name, description, created_at) values (?, ?, ?, ?)",
        datasetId,
        "Reporting test dataset",
        null,
        Timestamp.from(CREATED_AT));
    return datasetId;
  }

  private UUID insertSourceFile(UUID datasetId) {
    UUID sourceFileId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into source_file
          (id, dataset_id, original_filename, content_type, size_bytes, sha256,
           content_bytes, uploaded_at)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        sourceFileId,
        datasetId,
        "report.csv",
        "text/csv",
        CONTENT_BYTES.length,
        sha256(CONTENT_BYTES),
        CONTENT_BYTES,
        Timestamp.from(CREATED_AT));
    return sourceFileId;
  }

  private UUID insertValidationProfile(UUID datasetId) {
    UUID profileId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into validation_profile (id, dataset_id, name, created_at)
        values (?, ?, ?, ?)
        """,
        profileId,
        datasetId,
        "Reporting test profile",
        Timestamp.from(CREATED_AT));
    return profileId;
  }

  private IssueFixture insertIssue(
      UUID id,
      UUID runId,
      long rowNumber,
      String fieldName,
      ValidationRuleType ruleType,
      ValidationRuleSeverity severity,
      String message,
      String observedValue) {
    jdbcTemplate.update(
        """
        insert into validation_issue
          (id, run_id, row_number, field_name, rule_type, severity, message, observed_value)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        runId,
        rowNumber,
        fieldName,
        ruleType.name(),
        severity.name(),
        message,
        observedValue);
    return new IssueFixture(
        id, runId, rowNumber, fieldName, ruleType, severity, message, observedValue);
  }

  private List<Map<String, Object>> readRuns(UUID runId) {
    return jdbcTemplate.queryForList(
        "select * from validation_run where id = ? order by id", runId);
  }

  private List<Map<String, Object>> readIssues(UUID runId) {
    return jdbcTemplate.queryForList(
        """
        select * from validation_issue
        where run_id = ?
        order by row_number, field_name, rule_type, id
        """,
        runId);
  }

  private String sha256(byte[] contentBytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contentBytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  private record ParsedCsv(List<String> headers, List<CSVRecord> records) {}

  private record RunFixture(
      UUID id,
      UUID datasetId,
      UUID sourceFileId,
      UUID profileId,
      ValidationRunStatus status,
      long totalRows,
      long validRows,
      long invalidRows,
      long issueCount,
      Instant startedAt,
      Instant finishedAt,
      String failureReason) {}

  private record IssueFixture(
      UUID id,
      UUID runId,
      long rowNumber,
      String fieldName,
      ValidationRuleType ruleType,
      ValidationRuleSeverity severity,
      String message,
      String observedValue) {}
}
