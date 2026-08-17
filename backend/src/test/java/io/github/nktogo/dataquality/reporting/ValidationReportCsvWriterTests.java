package io.github.nktogo.dataquality.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import io.github.nktogo.dataquality.ingestion.ValidationRunResponse;
import io.github.nktogo.dataquality.ingestion.ValidationRunStatus;
import io.github.nktogo.dataquality.validation.ValidationIssueResponse;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

class ValidationReportCsvWriterTests {

  private static final List<String> EXPECTED_HEADERS =
      List.of(
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

  private static final Instant STARTED_AT = Instant.parse("2026-08-11T12:00:00.123456Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-08-11T12:00:02.123456Z");

  private final ValidationReportCsvWriter writer = new ValidationReportCsvWriter();

  @Test
  void writesExactHeadersUtf8CrLfAndPreservesIssueOrderAndSpecialText() throws IOException {
    UUID runId = UUID.randomUUID();
    String failureReason = "Failure, \"quoted\"\r\nnext";
    String firstMessage = "Markup <script>alert(\"x\")</script>, then\r\nnext";
    String firstObservedValue =
        "=HYPERLINK(\"https://example.invalid\")\r\nGr\u00fc\u00dfe \u6771\u4eac \t ";
    ValidationRunResponse run = failedRun(runId, failureReason);
    ValidationIssueResponse first =
        issue(
            UUID.randomUUID(),
            runId,
            2,
            " first,\"field\" ",
            ValidationRuleType.DATA_TYPE,
            ValidationRuleSeverity.WARNING,
            firstMessage,
            firstObservedValue);
    ValidationIssueResponse second =
        issue(
            UUID.randomUUID(),
            runId,
            4,
            "second",
            ValidationRuleType.UNIQUENESS,
            ValidationRuleSeverity.ERROR,
            "Second",
            " \t ");

    ValidationReport report = new ValidationReport(run, List.of(first, second));
    byte[] bytes = writer.write(report);
    byte[] repeatedBytes = writer.write(report);
    String csv = new String(bytes, StandardCharsets.UTF_8);
    ParsedCsv parsed = parse(csv);

    assertThat(bytes[0]).isEqualTo((byte) 'v');
    assertThat(repeatedBytes).containsExactly(bytes);
    assertThat(csv).endsWith("\r\n");
    assertThat(parsed.headers()).containsExactlyElementsOf(EXPECTED_HEADERS);
    assertThat(parsed.records()).hasSize(2);
    assertRunColumns(parsed.records().getFirst(), run);
    assertIssueColumns(parsed.records().getFirst(), first);
    assertRunColumns(parsed.records().get(1), run);
    assertIssueColumns(parsed.records().get(1), second);
    assertThat(parsed.records().getFirst().get("failure_reason")).isEqualTo(failureReason);
    assertThat(parsed.records().getFirst().get("message")).isEqualTo(firstMessage);
    assertThat(parsed.records().getFirst().get("observed_value"))
        .isEqualTo(firstObservedValue)
        .startsWith("=");
    assertThat(parsed.records().get(1).get("observed_value")).isEqualTo(" \t ");
  }

  @Test
  void distinguishesNullEmptyAndPresentObservedValues() throws IOException {
    UUID runId = UUID.randomUUID();
    ValidationRunResponse run = completedRun(runId, 2);
    ValidationIssueResponse nullValue =
        issue(
            UUID.randomUUID(),
            runId,
            2,
            "null-value",
            ValidationRuleType.REQUIRED_FIELD,
            ValidationRuleSeverity.ERROR,
            "Null",
            null);
    ValidationIssueResponse emptyValue =
        issue(
            UUID.randomUUID(),
            runId,
            3,
            "empty-value",
            ValidationRuleType.REQUIRED_FIELD,
            ValidationRuleSeverity.ERROR,
            "Empty",
            "");

    ParsedCsv parsed =
        parse(
            new String(
                writer.write(new ValidationReport(run, List.of(nullValue, emptyValue))),
                StandardCharsets.UTF_8));

    assertThat(parsed.records().getFirst().get("observed_value_present")).isEqualTo("false");
    assertThat(parsed.records().getFirst().get("observed_value")).isEmpty();
    assertThat(parsed.records().get(1).get("observed_value_present")).isEqualTo("true");
    assertThat(parsed.records().get(1).get("observed_value")).isEmpty();
  }

  @Test
  void writesOneRunOnlyRecordWhenThereAreNoIssues() throws IOException {
    UUID runId = UUID.randomUUID();
    ValidationRunResponse run =
        new ValidationRunResponse(
            runId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            ValidationRunStatus.PENDING,
            0,
            0,
            0,
            0,
            null,
            null,
            null);

    ParsedCsv parsed =
        parse(
            new String(writer.write(new ValidationReport(run, List.of())), StandardCharsets.UTF_8));

    assertThat(parsed.records()).hasSize(1);
    CSVRecord record = parsed.records().getFirst();
    assertRunColumns(record, run);
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
        .allSatisfy(header -> assertThat(record.get(header)).isEmpty());
  }

  private ParsedCsv parse(String csv) throws IOException {
    CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
    try (CSVParser parser = format.parse(new StringReader(csv))) {
      return new ParsedCsv(parser.getHeaderNames(), parser.getRecords());
    }
  }

  private void assertRunColumns(CSVRecord record, ValidationRunResponse run) {
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

  private void assertIssueColumns(CSVRecord record, ValidationIssueResponse issue) {
    assertThat(record.get("issue_present")).isEqualTo("true");
    assertThat(record.get("issue_id")).isEqualTo(issue.id().toString());
    assertThat(record.get("issue_run_id")).isEqualTo(issue.runId().toString());
    assertThat(record.get("row_number")).isEqualTo(Long.toString(issue.rowNumber()));
    assertThat(record.get("field_name")).isEqualTo(issue.fieldName());
    assertThat(record.get("rule_type")).isEqualTo(issue.ruleType().name());
    assertThat(record.get("severity")).isEqualTo(issue.severity().name());
    assertThat(record.get("message")).isEqualTo(issue.message());
    assertThat(record.get("observed_value_present"))
        .isEqualTo(Boolean.toString(issue.observedValue() != null));
    assertThat(record.get("observed_value"))
        .isEqualTo(issue.observedValue() == null ? "" : issue.observedValue());
  }

  private ValidationRunResponse completedRun(UUID runId, long issueCount) {
    return new ValidationRunResponse(
        runId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        ValidationRunStatus.COMPLETED,
        4,
        2,
        2,
        issueCount,
        STARTED_AT,
        FINISHED_AT,
        null);
  }

  private ValidationRunResponse failedRun(UUID runId, String failureReason) {
    return new ValidationRunResponse(
        runId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        ValidationRunStatus.FAILED,
        4,
        0,
        0,
        0,
        STARTED_AT,
        FINISHED_AT,
        failureReason);
  }

  private ValidationIssueResponse issue(
      UUID id,
      UUID runId,
      long rowNumber,
      String fieldName,
      ValidationRuleType ruleType,
      ValidationRuleSeverity severity,
      String message,
      String observedValue) {
    return new ValidationIssueResponse(
        id, runId, rowNumber, fieldName, ruleType, severity, message, observedValue);
  }

  private record ParsedCsv(List<String> headers, List<CSVRecord> records) {}
}
