package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ValidationRunIntegrationTests {

  private static final byte[] CONTENT_BYTES =
      "id,name\n1,Alice\n2,Bob".getBytes(StandardCharsets.UTF_8);
  private static final Instant CREATED_AT = Instant.parse("2026-07-25T12:00:00.123456Z");
  private static final Instant STARTED_AT = Instant.parse("2026-07-25T12:34:56.123456Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-07-25T12:35:56.123456Z");

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private ValidationRunRepository validationRunRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private MockMvc mockMvc;

  @MockitoSpyBean private CsvParser csvParser;

  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void deleteRunsAndParents() {
    jdbcTemplate.update("delete from validation_run");
    jdbcTemplate.update("delete from validation_rule");
    jdbcTemplate.update("delete from source_file");
    jdbcTemplate.update("delete from validation_profile");
    jdbcTemplate.update("delete from dataset");
  }

  @Nested
  class RepositoryIntegration {

    @Test
    void flywayAppliesValidationRunMigrations() {
      assertThat(flyway.info().pending()).isEmpty();
      assertThat(
              jdbcTemplate.queryForObject(
                  """
                  select count(*)
                  from flyway_schema_history
                  where version = '6'
                    and success
                  """,
                  Long.class))
          .isEqualTo(1L);
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.validation_run')::text", String.class))
          .isEqualTo("validation_run");
      assertThat(
              jdbcTemplate.queryForList(
                  """
                  select indexname
                  from pg_indexes
                  where schemaname = 'public'
                    and tablename = 'validation_run'
                  """,
                  String.class))
          .contains(
              "ix_validation_run_dataset_id",
              "ix_validation_run_source_file_id",
              "ix_validation_run_profile_id");
      assertThat(
              jdbcTemplate.queryForList(
                  """
                  select conname
                  from pg_constraint
                  where conname in (
                    'uq_source_file_id_dataset_id',
                    'uq_validation_profile_id_dataset_id'
                  )
                  """,
                  String.class))
          .containsExactlyInAnyOrder(
              "uq_source_file_id_dataset_id", "uq_validation_profile_id_dataset_id");
      assertThat(
              jdbcTemplate.queryForList(
                  """
                  select conname
                  from pg_constraint
                  where conrelid = 'validation_run'::regclass
                  """,
                  String.class))
          .contains(
              "fk_validation_run_dataset",
              "fk_validation_run_source_file_dataset",
              "fk_validation_run_profile_dataset",
              "ck_validation_run_status",
              "ck_validation_run_total_rows_nonnegative",
              "ck_validation_run_valid_rows_nonnegative",
              "ck_validation_run_invalid_rows_nonnegative",
              "ck_validation_run_issue_count_nonnegative",
              "ck_validation_run_pending_state",
              "ck_validation_run_processing_state",
              "ck_validation_run_failed_state",
              "ck_validation_run_failure_reason_state",
              "ck_validation_run_time_order");
    }

    @Test
    @Transactional
    void persistsAndReloadsPendingValidationRun() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      ValidationRun saved =
          validationRunRepository.saveAndFlush(ValidationRun.pending(datasetId, fileId, profileId));
      UUID runId = saved.getId();

      entityManager.clear();

      ValidationRun reloaded = validationRunRepository.findById(runId).orElseThrow();
      assertThat(reloaded.getId()).isEqualTo(runId);
      assertThat(reloaded.getDatasetId()).isEqualTo(datasetId);
      assertThat(reloaded.getSourceFileId()).isEqualTo(fileId);
      assertThat(reloaded.getProfileId()).isEqualTo(profileId);
      assertThat(reloaded.getStatus()).isEqualTo(ValidationRunStatus.PENDING);
      assertThat(reloaded.getTotalRows()).isZero();
      assertThat(reloaded.getValidRows()).isZero();
      assertThat(reloaded.getInvalidRows()).isZero();
      assertThat(reloaded.getIssueCount()).isZero();
      assertThat(reloaded.getStartedAt()).isNull();
      assertThat(reloaded.getFinishedAt()).isNull();
      assertThat(reloaded.getFailureReason()).isNull();
    }

    @Test
    void mapsAllValidationRunStatusValues() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      insertValidationRun(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      insertValidationRun(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PROCESSING",
          2L,
          0L,
          0L,
          0L,
          STARTED_AT,
          null,
          null);
      insertValidationRun(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "COMPLETED",
          2L,
          2L,
          0L,
          0L,
          STARTED_AT,
          FINISHED_AT,
          null);
      insertValidationRun(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "FAILED",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          FINISHED_AT,
          "CSV content is malformed.");

      assertThat(validationRunRepository.findAll())
          .extracting(ValidationRun::getStatus)
          .containsExactlyInAnyOrder(ValidationRunStatus.values());
    }

    @Test
    void permitsRepeatedRunsForSameSourceFileAndProfile() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      validationRunRepository.saveAndFlush(ValidationRun.pending(datasetId, fileId, profileId));
      validationRunRepository.saveAndFlush(ValidationRun.pending(datasetId, fileId, profileId));

      assertThat(validationRunRepository.findAll())
          .hasSize(2)
          .extracting(ValidationRun::getSourceFileId, ValidationRun::getProfileId)
          .containsOnly(org.assertj.core.groups.Tuple.tuple(fileId, profileId));
    }

    @Test
    void databaseRejectsUnknownStatusAndNegativeCounts() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "UNKNOWN",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          -1L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          -1L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          -1L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          -1L,
          null,
          null,
          null);
    }

    @Test
    void databaseRejectsInvalidPendingState() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          1L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          1L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          1L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          1L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          FINISHED_AT,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          "Parser failure");
    }

    @Test
    void databaseRejectsInvalidProcessingStateAndFailureReasonForCompletedState() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PROCESSING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PROCESSING",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          FINISHED_AT,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PROCESSING",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          null,
          "Parser failure");
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "COMPLETED",
          0L,
          0L,
          0L,
          0L,
          null,
          FINISHED_AT,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "COMPLETED",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          FINISHED_AT,
          "Parser failure");
    }

    @Test
    void databaseEnforcesFailedStateAndFailureReasonBoundaries() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "FAILED",
          0L,
          0L,
          0L,
          0L,
          null,
          FINISHED_AT,
          "Parser failure");
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "FAILED",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          null,
          "Parser failure");

      for (String invalidReason : new String[] {null, "", "   ", "\t", "\n", "\r\n"}) {
        assertRunInsertRejected(
            UUID.randomUUID(),
            datasetId,
            fileId,
            profileId,
            "FAILED",
            0L,
            0L,
            0L,
            0L,
            STARTED_AT,
            FINISHED_AT,
            invalidReason);
      }
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "FAILED",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          FINISHED_AT,
          "x".repeat(256));

      UUID acceptedRunId = UUID.randomUUID();
      insertValidationRun(
          acceptedRunId,
          datasetId,
          fileId,
          profileId,
          "FAILED",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          FINISHED_AT,
          "x".repeat(255));
      assertThat(
              jdbcTemplate.queryForObject(
                  "select char_length(failure_reason) from validation_run where id = ?",
                  Integer.class,
                  acceptedRunId))
          .isEqualTo(255);
    }

    @Test
    void databaseRejectsFinishedTimestampBeforeStartedTimestamp() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "FAILED",
          0L,
          0L,
          0L,
          0L,
          STARTED_AT,
          STARTED_AT.minusSeconds(1),
          "Parser failure");
    }

    @Test
    void databaseRejectsRequiredNulls() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      assertRunInsertRejected(
          null, datasetId, fileId, profileId, "PENDING", 0L, 0L, 0L, 0L, null, null, null);
      assertRunInsertRejected(
          UUID.randomUUID(), null, fileId, profileId, "PENDING", 0L, 0L, 0L, 0L, null, null, null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          null,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(), datasetId, fileId, null, "PENDING", 0L, 0L, 0L, 0L, null, null, null);
      assertRunInsertRejected(
          UUID.randomUUID(), datasetId, fileId, profileId, null, 0L, 0L, 0L, 0L, null, null, null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          null,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          null,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          null,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "PENDING",
          0L,
          0L,
          0L,
          null,
          null,
          null,
          null);
    }

    @Test
    void databaseRejectsUnknownAndCrossDatasetParents() {
      UUID firstDatasetId = insertDataset("First dataset");
      UUID firstFileId = insertSourceFile(firstDatasetId, "first.csv");
      UUID firstProfileId = insertValidationProfile(firstDatasetId, "First profile");
      UUID secondDatasetId = insertDataset("Second dataset");
      UUID secondProfileId = insertValidationProfile(secondDatasetId, "Second profile");

      assertRunInsertRejected(
          UUID.randomUUID(),
          UUID.randomUUID(),
          firstFileId,
          firstProfileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          firstDatasetId,
          UUID.randomUUID(),
          firstProfileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          firstDatasetId,
          firstFileId,
          UUID.randomUUID(),
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          firstDatasetId,
          firstFileId,
          secondProfileId,
          "PENDING",
          0L,
          0L,
          0L,
          0L,
          null,
          null,
          null);
    }

    @Test
    void databaseRestrictsParentDeletionAndRetainsRows() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      ValidationRun run =
          validationRunRepository.saveAndFlush(ValidationRun.pending(datasetId, fileId, profileId));

      assertThatThrownBy(() -> jdbcTemplate.update("delete from source_file where id = ?", fileId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () -> jdbcTemplate.update("delete from validation_profile where id = ?", profileId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbcTemplate.update("delete from dataset where id = ?", datasetId))
          .isInstanceOf(DataIntegrityViolationException.class);

      assertThat(validationRunRepository.existsById(run.getId())).isTrue();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select exists(select 1 from source_file where id = ?)", Boolean.class, fileId))
          .isTrue();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select exists(select 1 from validation_profile where id = ?)",
                  Boolean.class,
                  profileId))
          .isTrue();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select exists(select 1 from dataset where id = ?)", Boolean.class, datasetId))
          .isTrue();
    }
  }

  @Nested
  class ApiIntegration {

    @Test
    void createsProcessingRunAndLeavesSourceFileBytesAndMetadataUnchanged() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      SourceFileSnapshot sourceFileBefore = readSourceFile(fileId);

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content("{\"profileId\":\"" + profileId + "\"}"))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", aMapWithSize(12)))
              .andExpect(jsonPath("$.id").isString())
              .andExpect(jsonPath("$.datasetId").value(datasetId.toString()))
              .andExpect(jsonPath("$.sourceFileId").value(fileId.toString()))
              .andExpect(jsonPath("$.profileId").value(profileId.toString()))
              .andExpect(jsonPath("$.status").value("PROCESSING"))
              .andExpect(jsonPath("$.totalRows").value(2))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").value(nullValue()))
              .andExpect(jsonPath("$.failureReason").value(nullValue()))
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      UUID runId = UUID.fromString(JsonPath.read(responseBody, "$.id"));
      Instant responseStartedAt = Instant.parse(JsonPath.read(responseBody, "$.startedAt"));
      ValidationRun persisted = validationRunRepository.findById(runId).orElseThrow();
      assertThat(persisted.getDatasetId()).isEqualTo(datasetId);
      assertThat(persisted.getSourceFileId()).isEqualTo(fileId);
      assertThat(persisted.getProfileId()).isEqualTo(profileId);
      assertThat(persisted.getStatus()).isEqualTo(ValidationRunStatus.PROCESSING);
      assertThat(persisted.getTotalRows()).isEqualTo(2);
      assertThat(persisted.getValidRows()).isZero();
      assertThat(persisted.getInvalidRows()).isZero();
      assertThat(persisted.getIssueCount()).isZero();
      assertThat(persisted.getStartedAt()).isEqualTo(responseStartedAt);
      assertThat(persisted.getStartedAt().getNano() % 1_000).isZero();
      assertThat(persisted.getFinishedAt()).isNull();
      assertThat(persisted.getFailureReason()).isNull();
      assertSourceFileUnchanged(fileId, sourceFileBefore);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulCsvCases")
    void recordsExpectedTotalForSuccessfulCsvSemantics(
        String description, byte[] contentBytes, int expectedTotalRows) throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv", contentBytes);
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      SourceFileSnapshot sourceFileBefore = readSourceFile(fileId);

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content("{\"profileId\":\"" + profileId + "\"}"))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", aMapWithSize(12)))
              .andExpect(jsonPath("$.status").value("PROCESSING"))
              .andExpect(jsonPath("$.totalRows").value(expectedTotalRows))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").value(nullValue()))
              .andExpect(jsonPath("$.failureReason").value(nullValue()))
              .andReturn();

      UUID runId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      assertThat(validationRunRepository.findById(runId).orElseThrow())
          .satisfies(
              persisted -> {
                assertThat(persisted.getStatus()).isEqualTo(ValidationRunStatus.PROCESSING);
                assertThat(persisted.getTotalRows()).isEqualTo(expectedTotalRows);
                assertThat(persisted.getStartedAt()).isNotNull();
                assertThat(persisted.getStartedAt().getNano() % 1_000).isZero();
                assertThat(persisted.getFinishedAt()).isNull();
                assertThat(persisted.getFailureReason()).isNull();
              });
      assertSourceFileUnchanged(fileId, sourceFileBefore);
    }

    private static Stream<Arguments> successfulCsvCases() {
      return Stream.of(
          Arguments.of("header-only CSV", "value\r\n".getBytes(StandardCharsets.UTF_8), 0),
          Arguments.of(
              "UTF-8 BOM, CRLF, blank logical record, and multiline field",
              withUtf8Bom("value\r\n\"first\r\ncontinued\"\r\n\r\nlast\r\n"),
              3));
    }

    private static byte[] withUtf8Bom(String content) {
      byte[] csvBytes = content.getBytes(StandardCharsets.UTF_8);
      byte[] bytesWithBom = new byte[csvBytes.length + 3];
      bytesWithBom[0] = (byte) 0xEF;
      bytesWithBom[1] = (byte) 0xBB;
      bytesWithBom[2] = (byte) 0xBF;
      System.arraycopy(csvBytes, 0, bytesWithBom, 3, csvBytes.length);
      return bytesWithBom;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parserFailureCases")
    void createsFailedRunForExpectedParserErrors(
        String description, byte[] contentBytes, String expectedFailureReason) throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv", contentBytes);
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      SourceFileSnapshot sourceFileBefore = readSourceFile(fileId);

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content("{\"profileId\":\"" + profileId + "\"}"))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", aMapWithSize(12)))
              .andExpect(jsonPath("$.id").isString())
              .andExpect(jsonPath("$.datasetId").value(datasetId.toString()))
              .andExpect(jsonPath("$.sourceFileId").value(fileId.toString()))
              .andExpect(jsonPath("$.profileId").value(profileId.toString()))
              .andExpect(jsonPath("$.status").value("FAILED"))
              .andExpect(jsonPath("$.totalRows").value(0))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").isString())
              .andExpect(jsonPath("$.failureReason").value(expectedFailureReason))
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      UUID runId = UUID.fromString(JsonPath.read(responseBody, "$.id"));
      Instant responseStartedAt = Instant.parse(JsonPath.read(responseBody, "$.startedAt"));
      Instant responseFinishedAt = Instant.parse(JsonPath.read(responseBody, "$.finishedAt"));
      ValidationRun persisted = validationRunRepository.findById(runId).orElseThrow();

      assertThat(persisted.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
      assertThat(persisted.getTotalRows()).isZero();
      assertThat(persisted.getValidRows()).isZero();
      assertThat(persisted.getInvalidRows()).isZero();
      assertThat(persisted.getIssueCount()).isZero();
      assertThat(persisted.getStartedAt()).isEqualTo(responseStartedAt);
      assertThat(persisted.getFinishedAt()).isEqualTo(responseFinishedAt);
      assertThat(persisted.getStartedAt().getNano() % 1_000).isZero();
      assertThat(persisted.getFinishedAt().getNano() % 1_000).isZero();
      assertThat(persisted.getFinishedAt()).isAfterOrEqualTo(persisted.getStartedAt());
      assertThat(persisted.getFailureReason()).isEqualTo(expectedFailureReason);
      assertSourceFileUnchanged(fileId, sourceFileBefore);
    }

    private static Stream<Arguments> parserFailureCases() {
      return Stream.of(
          Arguments.of(
              "invalid UTF-8",
              new byte[] {(byte) 0xC3, (byte) 0x28},
              "CSV content is not valid UTF-8."),
          Arguments.of(
              "blank header",
              ",name\n1,Alice".getBytes(StandardCharsets.UTF_8),
              "CSV header column 1 must have a name."),
          Arguments.of(
              "inconsistent field count",
              "id,name\n1".getBytes(StandardCharsets.UTF_8),
              "CSV record 2 has 1 fields; expected 2."),
          Arguments.of(
              "malformed quoting",
              "id,note\n1,\"unterminated".getBytes(StandardCharsets.UTF_8),
              "CSV content is malformed."));
    }

    @Test
    void unexpectedParserExceptionLeavesCommittedRunPending() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      SourceFileSnapshot sourceFileBefore = readSourceFile(fileId);
      String unexpectedMessage = "Unexpected parser failure.";
      doThrow(new IllegalStateException(unexpectedMessage))
          .when(csvParser)
          .parse(any(byte[].class));

      assertThatThrownBy(
              () ->
                  mockMvc.perform(
                      post("/api/files/{fileId}/validation-runs", fileId)
                          .contentType(APPLICATION_JSON)
                          .content("{\"profileId\":\"" + profileId + "\"}")))
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage(unexpectedMessage);

      assertThat(validationRunRepository.findAll())
          .singleElement()
          .satisfies(
              persisted -> {
                assertThat(persisted.getDatasetId()).isEqualTo(datasetId);
                assertThat(persisted.getSourceFileId()).isEqualTo(fileId);
                assertThat(persisted.getProfileId()).isEqualTo(profileId);
                assertThat(persisted.getStatus()).isEqualTo(ValidationRunStatus.PENDING);
                assertThat(persisted.getTotalRows()).isZero();
                assertThat(persisted.getValidRows()).isZero();
                assertThat(persisted.getInvalidRows()).isZero();
                assertThat(persisted.getIssueCount()).isZero();
                assertThat(persisted.getStartedAt()).isNull();
                assertThat(persisted.getFinishedAt()).isNull();
                assertThat(persisted.getFailureReason()).isNull();
              });
      assertSourceFileUnchanged(fileId, sourceFileBefore);
    }

    @Test
    void permitsRepeatedRunCreation() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      String requestBody = "{\"profileId\":\"" + profileId + "\"}";
      SourceFileSnapshot sourceFileBefore = readSourceFile(fileId);

      MvcResult first =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isCreated())
              .andReturn();
      MvcResult second =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isCreated())
              .andReturn();

      String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
      String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");
      assertThat(firstId).isNotEqualTo(secondId);
      assertThat(validationRunRepository.findAll())
          .hasSize(2)
          .allSatisfy(
              run -> {
                assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.PROCESSING);
                assertThat(run.getTotalRows()).isEqualTo(2);
                assertThat(run.getStartedAt()).isNotNull();
                assertThat(run.getFinishedAt()).isNull();
                assertThat(run.getFailureReason()).isNull();
              });
      assertSourceFileUnchanged(fileId, sourceFileBefore);
    }

    @Test
    void permitsRepeatedFailedRunCreation() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId =
          insertSourceFile(
              datasetId, "customers.csv", "id,name\n1".getBytes(StandardCharsets.UTF_8));
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      String requestBody = "{\"profileId\":\"" + profileId + "\"}";
      SourceFileSnapshot sourceFileBefore = readSourceFile(fileId);

      MvcResult first =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("FAILED"))
              .andReturn();
      MvcResult second =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("FAILED"))
              .andReturn();

      String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
      String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");
      assertThat(firstId).isNotEqualTo(secondId);
      assertThat(validationRunRepository.findAll())
          .hasSize(2)
          .allSatisfy(
              run -> {
                assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
                assertThat(run.getTotalRows()).isZero();
                assertThat(run.getStartedAt()).isNotNull();
                assertThat(run.getFinishedAt()).isNotNull();
                assertThat(run.getFailureReason())
                    .isEqualTo("CSV record 2 has 1 fields; expected 2.");
              });
      assertSourceFileUnchanged(fileId, sourceFileBefore);
    }

    @Test
    void rejectsMissingNullMalformedAndEmptyRequestBodies() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");

      assertInvalidCreate(fileId, "{}");
      assertInvalidCreate(fileId, "{\"profileId\":null}");
      assertInvalidCreate(fileId, "{\"profileId\":\"not-a-uuid\"}");
      mockMvc
          .perform(
              post("/api/files/{fileId}/validation-runs", fileId).contentType(APPLICATION_JSON))
          .andExpect(status().isBadRequest());

      assertThat(validationRunRepository.count()).isZero();
    }

    @Test
    void rejectsMalformedJsonAndUnsupportedMediaType() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      mockMvc
          .perform(
              post("/api/files/{fileId}/validation-runs", fileId)
                  .contentType(APPLICATION_JSON)
                  .content("{not-json}"))
          .andExpect(status().isBadRequest());
      mockMvc
          .perform(
              post("/api/files/{fileId}/validation-runs", fileId)
                  .contentType(MediaType.TEXT_PLAIN)
                  .content("{\"profileId\":\"" + profileId + "\"}"))
          .andExpect(status().isUnsupportedMediaType());

      assertThat(validationRunRepository.count()).isZero();
    }

    @Test
    void returnsProblemDetailForUnknownSourceFile() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      UUID fileId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/files/{fileId}/validation-runs", fileId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"profileId\":\"" + profileId + "\"}"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.type").doesNotExist())
          .andExpect(jsonPath("$.title").value("Source file not found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("Source file '" + fileId + "' was not found."))
          .andExpect(jsonPath("$.instance").value("/api/files/" + fileId + "/validation-runs"));

      assertThat(validationRunRepository.count()).isZero();
    }

    @Test
    void rejectsMalformedSourceFileId() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      mockMvc
          .perform(
              post("/api/files/not-a-uuid/validation-runs")
                  .contentType(APPLICATION_JSON)
                  .content("{\"profileId\":\"" + profileId + "\"}"))
          .andExpect(status().isBadRequest());

      assertThat(validationRunRepository.count()).isZero();
    }

    @Test
    void returnsProblemDetailForUnknownValidationProfile() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = UUID.randomUUID();

      mockMvc
          .perform(
              post("/api/files/{fileId}/validation-runs", fileId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"profileId\":\"" + profileId + "\"}"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.type").doesNotExist())
          .andExpect(jsonPath("$.title").value("Validation Profile not found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(
              jsonPath("$.detail").value("Validation Profile '" + profileId + "' was not found."))
          .andExpect(jsonPath("$.instance").value("/api/files/" + fileId + "/validation-runs"));

      assertThat(validationRunRepository.count()).isZero();
    }

    @Test
    void returnsConflictForParentsFromDifferentDatasets() throws Exception {
      UUID firstDatasetId = insertDataset("First dataset");
      UUID fileId = insertSourceFile(firstDatasetId, "first.csv");
      UUID secondDatasetId = insertDataset("Second dataset");
      UUID profileId = insertValidationProfile(secondDatasetId, "Second profile");

      mockMvc
          .perform(
              post("/api/files/{fileId}/validation-runs", fileId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"profileId\":\"" + profileId + "\"}"))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.type").doesNotExist())
          .andExpect(jsonPath("$.title").value("Validation Run parent mismatch"))
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(
              jsonPath("$.detail")
                  .value(
                      "Source file '"
                          + fileId
                          + "' and Validation Profile '"
                          + profileId
                          + "' belong to different Datasets."))
          .andExpect(jsonPath("$.instance").value("/api/files/" + fileId + "/validation-runs"));

      assertThat(validationRunRepository.count()).isZero();
    }

    private void assertInvalidCreate(UUID fileId, String requestBody) throws Exception {
      mockMvc
          .perform(
              post("/api/files/{fileId}/validation-runs", fileId)
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest());

      assertThat(validationRunRepository.count()).isZero();
    }
  }

  private UUID insertDataset(String name) {
    UUID datasetId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into dataset (id, name, description, created_at) values (?, ?, ?, ?)",
        datasetId,
        name,
        null,
        Timestamp.from(CREATED_AT));
    return datasetId;
  }

  private UUID insertSourceFile(UUID datasetId, String filename) {
    return insertSourceFile(datasetId, filename, CONTENT_BYTES);
  }

  private UUID insertSourceFile(UUID datasetId, String filename, byte[] contentBytes) {
    UUID fileId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into source_file
          (id, dataset_id, original_filename, content_type, size_bytes, sha256,
           content_bytes, uploaded_at)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        fileId,
        datasetId,
        filename,
        "text/csv",
        contentBytes.length,
        sha256(contentBytes),
        contentBytes,
        Timestamp.from(CREATED_AT));
    return fileId;
  }

  private SourceFileSnapshot readSourceFile(UUID fileId) {
    return jdbcTemplate.queryForObject(
        """
        select dataset_id, original_filename, content_type, size_bytes, sha256,
               content_bytes, uploaded_at
        from source_file
        where id = ?
        """,
        (resultSet, rowNumber) ->
            new SourceFileSnapshot(
                resultSet.getObject("dataset_id", UUID.class),
                resultSet.getString("original_filename"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256"),
                resultSet.getBytes("content_bytes"),
                resultSet.getTimestamp("uploaded_at").toInstant()),
        fileId);
  }

  private void assertSourceFileUnchanged(UUID fileId, SourceFileSnapshot before) {
    SourceFileSnapshot after = readSourceFile(fileId);

    assertThat(after.datasetId()).isEqualTo(before.datasetId());
    assertThat(after.originalFilename()).isEqualTo(before.originalFilename());
    assertThat(after.contentType()).isEqualTo(before.contentType());
    assertThat(after.sizeBytes()).isEqualTo(before.sizeBytes());
    assertThat(after.sha256()).isEqualTo(before.sha256());
    assertThat(after.contentBytes()).containsExactly(before.contentBytes());
    assertThat(after.uploadedAt()).isEqualTo(before.uploadedAt());
  }

  private String sha256(byte[] contentBytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contentBytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  private UUID insertValidationProfile(UUID datasetId, String name) {
    UUID profileId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into validation_profile (id, dataset_id, name, created_at)
        values (?, ?, ?, ?)
        """,
        profileId,
        datasetId,
        name,
        Timestamp.from(CREATED_AT));
    return profileId;
  }

  private void insertValidationRun(
      UUID id,
      UUID datasetId,
      UUID sourceFileId,
      UUID profileId,
      String status,
      Long totalRows,
      Long validRows,
      Long invalidRows,
      Long issueCount,
      Instant startedAt,
      Instant finishedAt,
      String failureReason) {
    jdbcTemplate.update(
        """
        insert into validation_run
          (id, dataset_id, source_file_id, profile_id, status, total_rows, valid_rows,
           invalid_rows, issue_count, started_at, finished_at, failure_reason)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        datasetId,
        sourceFileId,
        profileId,
        status,
        totalRows,
        validRows,
        invalidRows,
        issueCount,
        startedAt == null ? null : Timestamp.from(startedAt),
        finishedAt == null ? null : Timestamp.from(finishedAt),
        failureReason);
  }

  private void assertRunInsertRejected(
      UUID id,
      UUID datasetId,
      UUID sourceFileId,
      UUID profileId,
      String status,
      Long totalRows,
      Long validRows,
      Long invalidRows,
      Long issueCount,
      Instant startedAt,
      Instant finishedAt,
      String failureReason) {
    assertThatThrownBy(
            () ->
                insertValidationRun(
                    id,
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
                    failureReason))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private record SourceFileSnapshot(
      UUID datasetId,
      String originalFilename,
      String contentType,
      long sizeBytes,
      String sha256,
      byte[] contentBytes,
      Instant uploadedAt) {}
}
