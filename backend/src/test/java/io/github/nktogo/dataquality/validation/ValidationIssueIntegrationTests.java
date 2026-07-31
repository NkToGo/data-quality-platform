package io.github.nktogo.dataquality.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import io.github.nktogo.dataquality.ingestion.ValidationRunStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ValidationIssueIntegrationTests {

  private static final byte[] CONTENT_BYTES =
      "email,amount\nalice@example.com,10".getBytes(StandardCharsets.UTF_8);
  private static final Instant CREATED_AT = Instant.parse("2026-07-30T12:00:00.123456Z");
  private static final Instant STARTED_AT = Instant.parse("2026-07-30T12:01:00.123456Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-07-30T12:02:00.123456Z");

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private ValidationIssueRepository validationIssueRepository;

  @Autowired private ValidationIssueService validationIssueService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private MockMvc mockMvc;

  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void deleteIssuesRunsAndParents() {
    jdbcTemplate.update("delete from validation_issue");
    jdbcTemplate.update("delete from validation_run");
    jdbcTemplate.update("delete from validation_rule");
    jdbcTemplate.update("delete from source_file");
    jdbcTemplate.update("delete from validation_profile");
    jdbcTemplate.update("delete from dataset");
  }

  @Nested
  class SchemaAndRepositoryIntegration {

    @Test
    void flywayAppliesValidationIssueMigrationAndCreatesExactColumns() {
      assertThat(flyway.info().pending()).isEmpty();
      assertThat(
              jdbcTemplate.queryForObject(
                  """
                  select count(*)
                  from flyway_schema_history
                  where version = '8'
                    and success
                  """,
                  Long.class))
          .isEqualTo(1L);
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.validation_issue')::text", String.class))
          .isEqualTo("validation_issue");

      List<ColumnDefinition> columns =
          jdbcTemplate.query(
              """
              select column_name, data_type, character_maximum_length, is_nullable
              from information_schema.columns
              where table_schema = 'public'
                and table_name = 'validation_issue'
              order by ordinal_position
              """,
              (resultSet, rowNumber) ->
                  new ColumnDefinition(
                      resultSet.getString("column_name"),
                      resultSet.getString("data_type"),
                      resultSet.getObject("character_maximum_length", Integer.class),
                      resultSet.getString("is_nullable")));

      assertThat(columns)
          .containsExactly(
              new ColumnDefinition("id", "uuid", null, "NO"),
              new ColumnDefinition("run_id", "uuid", null, "NO"),
              new ColumnDefinition("row_number", "bigint", null, "NO"),
              new ColumnDefinition("field_name", "character varying", 255, "NO"),
              new ColumnDefinition("rule_type", "character varying", 32, "NO"),
              new ColumnDefinition("severity", "character varying", 16, "NO"),
              new ColumnDefinition("message", "character varying", 500, "NO"),
              new ColumnDefinition("observed_value", "text", null, "YES"));
    }

    @Test
    void createsPrimaryKeyRestrictedForeignKeyConstraintsAndOrderedIndex() {
      assertThat(
              jdbcTemplate.queryForObject(
                  """
                  select contype::text
                  from pg_constraint
                  where conrelid = 'validation_issue'::regclass
                    and conname = 'pk_validation_issue'
                  """,
                  String.class))
          .isEqualTo("p");
      assertThat(
              jdbcTemplate.queryForList(
                  """
                  select conname
                  from pg_constraint
                  where conrelid = 'validation_issue'::regclass
                  """,
                  String.class))
          .contains(
              "pk_validation_issue",
              "fk_validation_issue_validation_run",
              "ck_validation_issue_row_number",
              "ck_validation_issue_field_name_not_blank",
              "ck_validation_issue_rule_type",
              "ck_validation_issue_severity",
              "ck_validation_issue_message_not_blank");
      assertThat(
              jdbcTemplate.queryForObject(
                  """
                  select confdeltype::text
                  from pg_constraint
                  where conname = 'fk_validation_issue_validation_run'
                  """,
                  String.class))
          .isEqualTo("r");
      assertThat(
              jdbcTemplate.queryForList(
                  """
                  select attribute.attname
                  from pg_index index_definition
                  cross join lateral unnest(index_definition.indkey)
                    with ordinality as index_column(attnum, position)
                  join pg_attribute attribute
                    on attribute.attrelid = index_definition.indrelid
                   and attribute.attnum = index_column.attnum
                  where index_definition.indexrelid =
                    'ix_validation_issue_run_row_field_type_id'::regclass
                  order by index_column.position
                  """,
                  String.class))
          .containsExactly("run_id", "row_number", "field_name", "rule_type", "id");
    }

    @Test
    void acceptsFirstDataRowAndRejectsEarlierRowNumbers() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PENDING);

      insertIssue(
          UUID.randomUUID(),
          run.id(),
          2L,
          "email",
          "REQUIRED_FIELD",
          "ERROR",
          "Value is required.",
          "");

      assertThat(validationIssueRepository.count()).isOne();
      assertIssueInsertRejected(
          UUID.randomUUID(),
          run.id(),
          1L,
          "email",
          "REQUIRED_FIELD",
          "ERROR",
          "Value is required.",
          "");
      assertIssueInsertRejected(
          UUID.randomUUID(),
          run.id(),
          0L,
          "email",
          "REQUIRED_FIELD",
          "ERROR",
          "Value is required.",
          "");
    }

    @Test
    void rejectsWhitespaceOnlyFieldNamesAndMessages() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PENDING);

      for (String blank : List.of("", "   ", "\t", "\r\n")) {
        assertIssueInsertRejected(
            UUID.randomUUID(),
            run.id(),
            2L,
            blank,
            "REQUIRED_FIELD",
            "ERROR",
            "Value is required.",
            "");
        assertIssueInsertRejected(
            UUID.randomUUID(), run.id(), 2L, "email", "REQUIRED_FIELD", "ERROR", blank, "");
      }
    }

    @Test
    void enforcesEnumRequiredAndLengthConstraints() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PENDING);

      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), 2L, "email", "UNKNOWN", "ERROR", "Message", "");
      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), 2L, "email", "REQUIRED_FIELD", "INFO", "Message", "");

      assertIssueInsertRejected(
          null, run.id(), 2L, "email", "REQUIRED_FIELD", "ERROR", "Message", "");
      assertIssueInsertRejected(
          UUID.randomUUID(), null, 2L, "email", "REQUIRED_FIELD", "ERROR", "Message", "");
      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), null, "email", "REQUIRED_FIELD", "ERROR", "Message", "");
      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), 2L, null, "REQUIRED_FIELD", "ERROR", "Message", "");
      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), 2L, "email", null, "ERROR", "Message", "");
      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), 2L, "email", "REQUIRED_FIELD", null, "Message", "");
      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), 2L, "email", "REQUIRED_FIELD", "ERROR", null, "");

      insertIssue(
          UUID.randomUUID(),
          run.id(),
          2L,
          "f".repeat(255),
          "REQUIRED_FIELD",
          "ERROR",
          "m".repeat(500),
          "");
      assertIssueInsertRejected(
          UUID.randomUUID(),
          run.id(),
          2L,
          "f".repeat(256),
          "REQUIRED_FIELD",
          "ERROR",
          "Message",
          "");
      assertIssueInsertRejected(
          UUID.randomUUID(), run.id(), 2L, "email", "REQUIRED_FIELD", "ERROR", "m".repeat(501), "");
    }

    @Test
    void preservesNullEmptyAndWhitespaceObservedValues() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PENDING);
      UUID nullId = UUID.randomUUID();
      UUID emptyId = UUID.randomUUID();
      UUID whitespaceId = UUID.randomUUID();

      insertIssue(nullId, run.id(), 2L, "null", "REQUIRED_FIELD", "ERROR", "Message", null);
      insertIssue(emptyId, run.id(), 3L, "empty", "REQUIRED_FIELD", "ERROR", "Message", "");
      insertIssue(
          whitespaceId,
          run.id(),
          4L,
          "whitespace",
          "REQUIRED_FIELD",
          "ERROR",
          "Message",
          " \t\r\n ");

      assertThat(readObservedValue(nullId)).isNull();
      assertThat(readObservedValue(emptyId)).isEmpty();
      assertThat(readObservedValue(whitespaceId)).isEqualTo(" \t\r\n ");
    }

    @Test
    void permitsDuplicateIssuesAndRestrictsUnknownOrDeletedRuns() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PENDING);

      insertIssue(
          UUID.randomUUID(),
          run.id(),
          2L,
          "email",
          "REQUIRED_FIELD",
          "ERROR",
          "Value is required.",
          "");
      insertIssue(
          UUID.randomUUID(),
          run.id(),
          2L,
          "email",
          "REQUIRED_FIELD",
          "ERROR",
          "Value is required.",
          "");

      assertThat(validationIssueRepository.count()).isEqualTo(2);
      assertIssueInsertRejected(
          UUID.randomUUID(),
          UUID.randomUUID(),
          2L,
          "email",
          "REQUIRED_FIELD",
          "ERROR",
          "Message",
          "");

      assertThatThrownBy(
              () -> jdbcTemplate.update("delete from validation_run where id = ?", run.id()))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(readRunSnapshot(run.id())).isNotNull();
      assertThat(validationIssueRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    void persistsReloadsAndGeneratesUuidForEveryField() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PROCESSING);
      ValidationIssue saved =
          validationIssueRepository.saveAndFlush(
              new ValidationIssue(
                  run.id(),
                  new ValidationIssueDraft(
                      2,
                      "città",
                      ValidationRuleType.DATA_TYPE,
                      ValidationRuleSeverity.WARNING,
                      "Valore non valido.",
                      "東京 \t")));
      UUID issueId = saved.getId();

      entityManager.clear();

      ValidationIssue reloaded = validationIssueRepository.findById(issueId).orElseThrow();
      assertThat(reloaded.getId()).isEqualTo(issueId);
      assertThat(reloaded.getRunId()).isEqualTo(run.id());
      assertThat(reloaded.getRowNumber()).isEqualTo(2);
      assertThat(reloaded.getFieldName()).isEqualTo("città");
      assertThat(reloaded.getRuleType()).isEqualTo(ValidationRuleType.DATA_TYPE);
      assertThat(reloaded.getSeverity()).isEqualTo(ValidationRuleSeverity.WARNING);
      assertThat(reloaded.getMessage()).isEqualTo("Valore non valido.");
      assertThat(reloaded.getObservedValue()).isEqualTo("東京 \t");
    }
  }

  @Nested
  class InternalPersistenceIntegration {

    @Test
    void persistsAllRuleTypesBothSeveritiesAndLeavesRunUnchanged() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PROCESSING);
      ValidationRunSnapshot before = readRunSnapshot(run.id());
      List<ValidationIssueDraft> drafts =
          List.of(
              draft(2, "required", ValidationRuleType.REQUIRED_FIELD, ValidationRuleSeverity.ERROR),
              draft(3, "typed", ValidationRuleType.DATA_TYPE, ValidationRuleSeverity.WARNING),
              draft(4, "unique", ValidationRuleType.UNIQUENESS, ValidationRuleSeverity.ERROR),
              draft(5, "amount", ValidationRuleType.NUMERIC_RANGE, ValidationRuleSeverity.WARNING),
              draft(6, "date", ValidationRuleType.DATE_FORMAT, ValidationRuleSeverity.ERROR));

      validationIssueService.persistAll(run.id(), drafts);

      List<ValidationIssue> issues =
          validationIssueRepository.findAllByRunIdOrderByRowNumberAscFieldNameAscRuleTypeAscIdAsc(
              run.id());
      assertThat(issues).hasSize(5).allMatch(issue -> issue.getId() != null);
      assertThat(issues)
          .extracting(ValidationIssue::getRuleType)
          .containsExactly(
              ValidationRuleType.REQUIRED_FIELD,
              ValidationRuleType.DATA_TYPE,
              ValidationRuleType.UNIQUENESS,
              ValidationRuleType.NUMERIC_RANGE,
              ValidationRuleType.DATE_FORMAT);
      assertThat(issues)
          .extracting(ValidationIssue::getSeverity)
          .containsExactly(
              ValidationRuleSeverity.ERROR,
              ValidationRuleSeverity.WARNING,
              ValidationRuleSeverity.ERROR,
              ValidationRuleSeverity.WARNING,
              ValidationRuleSeverity.ERROR);
      assertThat(readRunSnapshot(run.id())).isEqualTo(before);
    }

    @Test
    void persistsSimilarDraftsWithoutDeduplication() {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PROCESSING);
      ValidationIssueDraft draft =
          new ValidationIssueDraft(
              2,
              "email",
              ValidationRuleType.REQUIRED_FIELD,
              ValidationRuleSeverity.ERROR,
              "Value is required.",
              "");

      validationIssueService.persistAll(run.id(), List.of(draft, draft));

      assertThat(validationIssueRepository.findAll()).hasSize(2);
      assertThat(validationIssueRepository.findAll())
          .extracting(ValidationIssue::getId)
          .doesNotHaveDuplicates();
    }
  }

  @Nested
  class ApiIntegration {

    @Test
    void returnsEmptyArrayForExistingRunWithoutIssues() throws Exception {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PENDING);

      mockMvc
          .perform(get("/api/validation-runs/{runId}/issues", run.id()))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @EnumSource(ValidationRunStatus.class)
    void retrievesPersistedIssuesForEveryRunStatus(ValidationRunStatus statusValue)
        throws Exception {
      ValidationRunFixture run = insertValidationRunFixture(statusValue);
      IssueFixture issue =
          insertIssueFixture(
              UUID.randomUUID(),
              run.id(),
              2,
              "email",
              ValidationRuleType.REQUIRED_FIELD,
              ValidationRuleSeverity.ERROR,
              "Value is required.",
              "");

      ResultActions response =
          mockMvc
              .perform(get("/api/validation-runs/{runId}/issues", run.id()))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", hasSize(1)));

      assertIssueResponse(response, "$[0]", issue);
    }

    @Test
    void returnsExactFieldsNullableValuesAndPostgresqlOrdering() throws Exception {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.COMPLETED);
      IssueFixture first =
          insertIssueFixture(
              UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
              run.id(),
              2,
              "alpha",
              ValidationRuleType.DATE_FORMAT,
              ValidationRuleSeverity.WARNING,
              "First",
              null);
      IssueFixture second =
          insertIssueFixture(
              UUID.fromString("00000000-0000-0000-0000-000000000001"),
              run.id(),
              2,
              "beta",
              ValidationRuleType.DATA_TYPE,
              ValidationRuleSeverity.ERROR,
              "Second",
              "");
      IssueFixture third =
          insertIssueFixture(
              UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"),
              run.id(),
              3,
              "same",
              ValidationRuleType.REQUIRED_FIELD,
              ValidationRuleSeverity.ERROR,
              "Third",
              " \t ");
      IssueFixture fourth =
          insertIssueFixture(
              UUID.fromString("80000000-0000-0000-0000-000000000000"),
              run.id(),
              3,
              "same",
              ValidationRuleType.REQUIRED_FIELD,
              ValidationRuleSeverity.WARNING,
              "Fourth",
              "fourth");
      IssueFixture fifth =
          insertIssueFixture(
              UUID.fromString("00000000-0000-0000-0000-000000000002"),
              run.id(),
              3,
              "same",
              ValidationRuleType.UNIQUENESS,
              ValidationRuleSeverity.ERROR,
              "Fifth",
              "fifth");

      ResultActions response =
          mockMvc
              .perform(get("/api/validation-runs/{runId}/issues", run.id()))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", hasSize(5)));

      assertIssueResponse(response, "$[0]", first);
      assertIssueResponse(response, "$[1]", second);
      assertIssueResponse(response, "$[2]", third);
      assertIssueResponse(response, "$[3]", fourth);
      assertIssueResponse(response, "$[4]", fifth);
    }

    @Test
    void returnsExistingValidationRunProblemDetailForUnknownRun() throws Exception {
      UUID runId = UUID.randomUUID();

      mockMvc
          .perform(get("/api/validation-runs/{runId}/issues", runId))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.type").doesNotExist())
          .andExpect(jsonPath("$.title").value("Validation Run not found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("Validation Run '" + runId + "' was not found."))
          .andExpect(jsonPath("$.instance").value("/api/validation-runs/" + runId + "/issues"));
    }

    @Test
    void rejectsMalformedRunIdAndDoesNotExposePublicWriter() throws Exception {
      mockMvc
          .perform(get("/api/validation-runs/not-a-uuid/issues"))
          .andExpect(status().isBadRequest());
      mockMvc
          .perform(post("/api/validation-runs/{runId}/issues", UUID.randomUUID()))
          .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void retrievalHasNoDatabaseSideEffects() throws Exception {
      ValidationRunFixture run = insertValidationRunFixture(ValidationRunStatus.PROCESSING);
      insertIssueFixture(
          UUID.randomUUID(),
          run.id(),
          2,
          "email",
          ValidationRuleType.REQUIRED_FIELD,
          ValidationRuleSeverity.ERROR,
          "Value is required.",
          "");
      ValidationRunSnapshot runBefore = readRunSnapshot(run.id());
      List<ValidationIssueSnapshot> issuesBefore = readIssueSnapshots(run.id());

      mockMvc
          .perform(get("/api/validation-runs/{runId}/issues", run.id()))
          .andExpect(status().isOk());

      assertThat(readRunSnapshot(run.id())).isEqualTo(runBefore);
      assertThat(readIssueSnapshots(run.id())).containsExactlyElementsOf(issuesBefore);
    }

    private ResultActions assertIssueResponse(
        ResultActions response, String path, IssueFixture expected) throws Exception {
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

      return response;
    }
  }

  private ValidationIssueDraft draft(
      long rowNumber,
      String fieldName,
      ValidationRuleType ruleType,
      ValidationRuleSeverity severity) {
    return new ValidationIssueDraft(
        rowNumber,
        fieldName,
        ruleType,
        severity,
        "Deterministic message for " + ruleType + ".",
        "observed-" + rowNumber);
  }

  private ValidationRunFixture insertValidationRunFixture(ValidationRunStatus status) {
    UUID datasetId = insertDataset();
    UUID sourceFileId = insertSourceFile(datasetId);
    UUID profileId = insertValidationProfile(datasetId);
    UUID runId = UUID.randomUUID();
    long totalRows = 0;
    long validRows = 0;
    long invalidRows = 0;
    long issueCount = 0;
    Instant startedAt = null;
    Instant finishedAt = null;
    String failureReason = null;

    switch (status) {
      case PENDING -> {}
      case PROCESSING -> {
        totalRows = 5;
        startedAt = STARTED_AT;
      }
      case COMPLETED -> {
        totalRows = 5;
        validRows = 4;
        invalidRows = 1;
        issueCount = 1;
        startedAt = STARTED_AT;
        finishedAt = FINISHED_AT;
      }
      case FAILED -> {
        startedAt = STARTED_AT;
        finishedAt = FINISHED_AT;
        failureReason = "CSV content is malformed.";
      }
    }

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

    return new ValidationRunFixture(
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
        "Issue test dataset",
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
        "issues.csv",
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
        "Issue test profile",
        Timestamp.from(CREATED_AT));
    return profileId;
  }

  private IssueFixture insertIssueFixture(
      UUID id,
      UUID runId,
      long rowNumber,
      String fieldName,
      ValidationRuleType ruleType,
      ValidationRuleSeverity severity,
      String message,
      String observedValue) {
    insertIssue(
        id, runId, rowNumber, fieldName, ruleType.name(), severity.name(), message, observedValue);
    return new IssueFixture(
        id, runId, rowNumber, fieldName, ruleType, severity, message, observedValue);
  }

  private void insertIssue(
      UUID id,
      UUID runId,
      Long rowNumber,
      String fieldName,
      String ruleType,
      String severity,
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
        ruleType,
        severity,
        message,
        observedValue);
  }

  private void assertIssueInsertRejected(
      UUID id,
      UUID runId,
      Long rowNumber,
      String fieldName,
      String ruleType,
      String severity,
      String message,
      String observedValue) {
    assertThatThrownBy(
            () ->
                insertIssue(
                    id, runId, rowNumber, fieldName, ruleType, severity, message, observedValue))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String readObservedValue(UUID issueId) {
    return jdbcTemplate.queryForObject(
        "select observed_value from validation_issue where id = ?", String.class, issueId);
  }

  private ValidationRunSnapshot readRunSnapshot(UUID runId) {
    return jdbcTemplate.queryForObject(
        """
        select id, dataset_id, source_file_id, profile_id, status, total_rows, valid_rows,
               invalid_rows, issue_count, started_at, finished_at, failure_reason
        from validation_run
        where id = ?
        """,
        (resultSet, rowNumber) -> {
          Timestamp startedAt = resultSet.getTimestamp("started_at");
          Timestamp finishedAt = resultSet.getTimestamp("finished_at");
          return new ValidationRunSnapshot(
              resultSet.getObject("id", UUID.class),
              resultSet.getObject("dataset_id", UUID.class),
              resultSet.getObject("source_file_id", UUID.class),
              resultSet.getObject("profile_id", UUID.class),
              ValidationRunStatus.valueOf(resultSet.getString("status")),
              resultSet.getLong("total_rows"),
              resultSet.getLong("valid_rows"),
              resultSet.getLong("invalid_rows"),
              resultSet.getLong("issue_count"),
              startedAt == null ? null : startedAt.toInstant(),
              finishedAt == null ? null : finishedAt.toInstant(),
              resultSet.getString("failure_reason"));
        },
        runId);
  }

  private List<ValidationIssueSnapshot> readIssueSnapshots(UUID runId) {
    return jdbcTemplate.query(
        """
        select id, run_id, row_number, field_name, rule_type, severity, message, observed_value
        from validation_issue
        where run_id = ?
        order by row_number, field_name, rule_type, id
        """,
        (resultSet, rowNumber) ->
            new ValidationIssueSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("run_id", UUID.class),
                resultSet.getLong("row_number"),
                resultSet.getString("field_name"),
                resultSet.getString("rule_type"),
                resultSet.getString("severity"),
                resultSet.getString("message"),
                resultSet.getString("observed_value")),
        runId);
  }

  private String sha256(byte[] contentBytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contentBytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  private record ColumnDefinition(
      String name, String dataType, Integer maximumLength, String nullable) {}

  private record ValidationRunFixture(
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

  private record ValidationRunSnapshot(
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

  private record ValidationIssueSnapshot(
      UUID id,
      UUID runId,
      long rowNumber,
      String fieldName,
      String ruleType,
      String severity,
      String message,
      String observedValue) {}
}
