package io.github.nktogo.dataquality.reporting;

import io.github.nktogo.dataquality.ingestion.ValidationRunResponse;
import io.github.nktogo.dataquality.validation.ValidationIssueResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
class ValidationReportCsvWriter {

  private static final String[] HEADERS = {
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
    "observed_value"
  };

  private static final CSVFormat CSV_FORMAT =
      CSVFormat.RFC4180
          .builder()
          .setHeader(HEADERS)
          .setSkipHeaderRecord(false)
          .setRecordSeparator("\r\n")
          .get();

  byte[] write(ValidationReport report) {
    StringWriter output = new StringWriter();
    try (CSVPrinter printer = new CSVPrinter(output, CSV_FORMAT)) {
      if (report.issues().isEmpty()) {
        printRecord(printer, report.validationRun(), null);
      } else {
        for (ValidationIssueResponse issue : report.issues()) {
          printRecord(printer, report.validationRun(), issue);
        }
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Validation report CSV could not be generated.", exception);
    }

    return output.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void printRecord(
      CSVPrinter printer, ValidationRunResponse validationRun, ValidationIssueResponse issue)
      throws IOException {
    boolean issuePresent = issue != null;
    String observedValue = issuePresent ? issue.observedValue() : null;

    printer.printRecord(
        validationRun.id(),
        validationRun.datasetId(),
        validationRun.sourceFileId(),
        validationRun.profileId(),
        validationRun.status(),
        validationRun.totalRows(),
        validationRun.validRows(),
        validationRun.invalidRows(),
        validationRun.issueCount(),
        valueOrEmpty(validationRun.startedAt()),
        valueOrEmpty(validationRun.finishedAt()),
        valueOrEmpty(validationRun.failureReason()),
        issuePresent,
        issuePresent ? issue.id() : "",
        issuePresent ? issue.runId() : "",
        issuePresent ? issue.rowNumber() : "",
        issuePresent ? issue.fieldName() : "",
        issuePresent ? issue.ruleType() : "",
        issuePresent ? issue.severity() : "",
        issuePresent ? issue.message() : "",
        observedValue != null,
        valueOrEmpty(observedValue));
  }

  private Object valueOrEmpty(Object value) {
    return value == null ? "" : value;
  }
}
