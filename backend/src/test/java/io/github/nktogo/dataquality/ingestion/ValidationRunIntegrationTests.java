package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.nktogo.dataquality.dataset.ValidationRuleAccess;
import io.github.nktogo.dataquality.dataset.ValidationRuleSeverity;
import io.github.nktogo.dataquality.dataset.ValidationRuleType;
import io.github.nktogo.dataquality.validation.ValidationEngine;
import io.github.nktogo.dataquality.validation.ValidationIssueDraft;
import io.github.nktogo.dataquality.validation.ValidationResult;
import io.github.nktogo.dataquality.validation.ValidationSummary;
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
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.test.web.servlet.ResultActions;
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

  @MockitoSpyBean private ValidationRuleAccess validationRuleAccess;

  @MockitoSpyBean private ValidationEngine validationEngine;

  @MockitoSpyBean private ValidationRunRecoveryService validationRunRecoveryService;

  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void deleteRunsAndParents() {
    jdbcTemplate.update("delete from validation_issue");
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
                  where version in ('6', '9')
                    and success
                  """,
                  Long.class))
          .isEqualTo(2L);
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
              "ck_validation_run_time_order",
              "ck_validation_run_completed_state");
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
    void ordersValidationRunsByPostgresqlUuidOrder() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID secondId = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
      UUID thirdId = UUID.fromString("80000000-0000-0000-0000-000000000000");
      UUID fourthId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

      insertValidationRun(
          fourthId, datasetId, fileId, profileId, "PENDING", 0L, 0L, 0L, 0L, null, null, null);
      insertValidationRun(
          secondId, datasetId, fileId, profileId, "PENDING", 0L, 0L, 0L, 0L, null, null, null);
      insertValidationRun(
          thirdId, datasetId, fileId, profileId, "PENDING", 0L, 0L, 0L, 0L, null, null, null);
      insertValidationRun(
          firstId, datasetId, fileId, profileId, "PENDING", 0L, 0L, 0L, 0L, null, null, null);

      assertThat(validationRunRepository.findAllByOrderByIdAsc())
          .extracting(ValidationRun::getId)
          .containsExactly(firstId, secondId, thirdId, fourthId);
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
    void databaseEnforcesCompletedSummaryInvariant() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");

      insertValidationRun(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "COMPLETED",
          3L,
          2L,
          1L,
          2L,
          STARTED_AT,
          FINISHED_AT,
          null);
      insertValidationRun(
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
          null);
      insertValidationRun(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "COMPLETED",
          1L,
          1L,
          0L,
          1L,
          STARTED_AT,
          FINISHED_AT,
          null);

      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "COMPLETED",
          3L,
          1L,
          1L,
          1L,
          STARTED_AT,
          FINISHED_AT,
          null);
      assertRunInsertRejected(
          UUID.randomUUID(),
          datasetId,
          fileId,
          profileId,
          "COMPLETED",
          2L,
          1L,
          1L,
          0L,
          STARTED_AT,
          FINISHED_AT,
          null);
      assertRunInsertRejected(
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
          STARTED_AT.minusSeconds(1),
          null);

      assertThat(validationRunRepository.count()).isEqualTo(3);
    }

    @Test
    void coherentVersionEightRunAcceptsVersionNineWithoutDataRewrite() {
      String schema = newMigrationTestSchema();
      try {
        migrateSchemaToVersionEight(schema);
        MigrationRunFixture run = insertMigrationTestRun(schema, 3, 2, 1, 2);
        ValidationRunSnapshot before = readMigrationRunSnapshot(schema, run.id());

        var result = flywayForVersionNineSchema(schema).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(flywayForVersionNineSchema(schema).info().pending()).isEmpty();
        assertThat(readMigrationRunSnapshot(schema, run.id())).isEqualTo(before);
      } finally {
        jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
      }
    }

    @Test
    void incoherentVersionEightRunBlocksVersionNineWithoutRewrite() {
      String schema = newMigrationTestSchema();
      try {
        migrateSchemaToVersionEight(schema);
        MigrationRunFixture run = insertMigrationTestRun(schema, 3, 1, 1, 1);
        ValidationRunSnapshot before = readMigrationRunSnapshot(schema, run.id());

        assertThatThrownBy(() -> flywayForVersionNineSchema(schema).migrate())
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining("ck_validation_run_completed_state");

        assertThat(flywayForVersionNineSchema(schema).info().current().getVersion().getVersion())
            .isEqualTo("8");
        assertThat(readMigrationRunSnapshot(schema, run.id())).isEqualTo(before);
      } finally {
        jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
      }
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
    void listsNoValidationRunsAsEmptyArray() throws Exception {
      mockMvc
          .perform(get("/api/validation-runs"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(content().json("[]"));
    }

    @Test
    void listsAllStatusesInPostgresqlUuidOrderWithExactResponses() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      ValidationRunFixture pending =
          insertValidationRunFixture(
              UUID.fromString("00000000-0000-0000-0000-000000000001"),
              datasetId,
              fileId,
              profileId,
              ValidationRunStatus.PENDING);
      ValidationRunFixture processing =
          insertValidationRunFixture(
              UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"),
              datasetId,
              fileId,
              profileId,
              ValidationRunStatus.PROCESSING);
      ValidationRunFixture failed =
          insertValidationRunFixture(
              UUID.fromString("80000000-0000-0000-0000-000000000000"),
              datasetId,
              fileId,
              profileId,
              ValidationRunStatus.FAILED);
      ValidationRunFixture completed =
          insertValidationRunFixture(
              UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
              datasetId,
              fileId,
              profileId,
              ValidationRunStatus.COMPLETED);

      ResultActions response =
          mockMvc
              .perform(get("/api/validation-runs"))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", hasSize(4)));

      assertValidationRunResponse(response, "$[0]", pending);
      assertValidationRunResponse(response, "$[1]", processing);
      assertValidationRunResponse(response, "$[2]", failed);
      assertValidationRunResponse(response, "$[3]", completed);
    }

    @ParameterizedTest
    @EnumSource(ValidationRunStatus.class)
    void retrievesValidationRunById(ValidationRunStatus runStatus) throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      ValidationRunFixture requested =
          insertValidationRunFixture(UUID.randomUUID(), datasetId, fileId, profileId, runStatus);
      insertValidationRunFixture(
          UUID.randomUUID(), datasetId, fileId, profileId, ValidationRunStatus.PENDING);

      ResultActions response =
          mockMvc
              .perform(get("/api/validation-runs/{runId}", requested.id()))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));

      assertValidationRunResponse(response, "$", requested);
    }

    @Test
    void returnsProblemDetailForUnknownValidationRun() throws Exception {
      UUID runId = UUID.randomUUID();

      mockMvc
          .perform(get("/api/validation-runs/{runId}", runId))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.type").doesNotExist())
          .andExpect(jsonPath("$.title").value("Validation Run not found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("Validation Run '" + runId + "' was not found."))
          .andExpect(jsonPath("$.instance").value("/api/validation-runs/" + runId));
    }

    @Test
    void rejectsMalformedValidationRunId() throws Exception {
      mockMvc.perform(get("/api/validation-runs/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void retrievalDoesNotChangePersistedValidationRuns() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      ValidationRunFixture pending =
          insertValidationRunFixture(
              UUID.randomUUID(), datasetId, fileId, profileId, ValidationRunStatus.PENDING);
      insertValidationRunFixture(
          UUID.randomUUID(), datasetId, fileId, profileId, ValidationRunStatus.FAILED);
      List<ValidationRunSnapshot> before = readValidationRunSnapshots();

      mockMvc.perform(get("/api/validation-runs")).andExpect(status().isOk());
      mockMvc.perform(get("/api/validation-runs/{runId}", pending.id())).andExpect(status().isOk());

      assertThat(readValidationRunSnapshots()).containsExactlyElementsOf(before);
    }

    @Test
    void createsCompletedRunAndLeavesSourceFileBytesAndMetadataUnchanged() throws Exception {
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
              .andExpect(jsonPath("$.status").value("COMPLETED"))
              .andExpect(jsonPath("$.totalRows").value(2))
              .andExpect(jsonPath("$.validRows").value(2))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").isString())
              .andExpect(jsonPath("$.failureReason").value(nullValue()))
              .andReturn();

      String responseBody = result.getResponse().getContentAsString();
      UUID runId = UUID.fromString(JsonPath.read(responseBody, "$.id"));
      Instant responseStartedAt = Instant.parse(JsonPath.read(responseBody, "$.startedAt"));
      Instant responseFinishedAt = Instant.parse(JsonPath.read(responseBody, "$.finishedAt"));
      ValidationRun persisted = validationRunRepository.findById(runId).orElseThrow();
      assertThat(persisted.getDatasetId()).isEqualTo(datasetId);
      assertThat(persisted.getSourceFileId()).isEqualTo(fileId);
      assertThat(persisted.getProfileId()).isEqualTo(profileId);
      assertThat(persisted.getStatus()).isEqualTo(ValidationRunStatus.COMPLETED);
      assertThat(persisted.getTotalRows()).isEqualTo(2);
      assertThat(persisted.getValidRows()).isEqualTo(2);
      assertThat(persisted.getInvalidRows()).isZero();
      assertThat(persisted.getIssueCount()).isZero();
      assertThat(persisted.getStartedAt()).isEqualTo(responseStartedAt);
      assertThat(persisted.getFinishedAt()).isEqualTo(responseFinishedAt);
      assertThat(persisted.getStartedAt().getNano() % 1_000).isZero();
      assertThat(persisted.getFinishedAt().getNano() % 1_000).isZero();
      assertThat(persisted.getFinishedAt()).isAfterOrEqualTo(persisted.getStartedAt());
      assertThat(persisted.getFailureReason()).isNull();
      assertThat(issueCount(runId)).isZero();
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
              .andExpect(jsonPath("$.status").value("COMPLETED"))
              .andExpect(jsonPath("$.totalRows").value(expectedTotalRows))
              .andExpect(jsonPath("$.validRows").value(expectedTotalRows))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").isString())
              .andExpect(jsonPath("$.failureReason").value(nullValue()))
              .andReturn();

      UUID runId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      assertThat(validationRunRepository.findById(runId).orElseThrow())
          .satisfies(
              persisted -> {
                assertThat(persisted.getStatus()).isEqualTo(ValidationRunStatus.COMPLETED);
                assertThat(persisted.getTotalRows()).isEqualTo(expectedTotalRows);
                assertThat(persisted.getValidRows()).isEqualTo(expectedTotalRows);
                assertThat(persisted.getInvalidRows()).isZero();
                assertThat(persisted.getIssueCount()).isZero();
                assertThat(persisted.getStartedAt()).isNotNull();
                assertThat(persisted.getStartedAt().getNano() % 1_000).isZero();
                assertThat(persisted.getFinishedAt()).isNotNull();
                assertThat(persisted.getFinishedAt().getNano() % 1_000).isZero();
                assertThat(persisted.getFinishedAt()).isAfterOrEqualTo(persisted.getStartedAt());
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

    @Test
    void executesAllRuleTypesPersistsIssuesAndCompletesWithExactSummary() throws Exception {
      byte[] contentBytes =
          ("name,age,email,date,active\n"
                  + ",17,duplicate@example.com,2024-02-29,TRUE\n"
                  + "Alice,25,duplicate@example.com,2024-02-29,true\n"
                  + "Bob,oops,unique@example.com,2024-02-29,false\n")
              .getBytes(StandardCharsets.UTF_8);
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv", contentBytes);
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      insertValidationRule(
          profileId, "name", ValidationRuleType.REQUIRED_FIELD, "{}", "ERROR", true);
      insertValidationRule(
          profileId,
          "active",
          ValidationRuleType.DATA_TYPE,
          "{\"type\":\"BOOLEAN\"}",
          "WARNING",
          true);
      insertValidationRule(
          profileId, "email", ValidationRuleType.UNIQUENESS, "{}", "WARNING", true);
      insertValidationRule(
          profileId,
          "age",
          ValidationRuleType.NUMERIC_RANGE,
          "{\"minimum\":18,\"maximum\":65}",
          "ERROR",
          true);
      insertValidationRule(
          profileId,
          "date",
          ValidationRuleType.DATE_FORMAT,
          "{\"format\":\"ISO_DATE\"}",
          "ERROR",
          true);
      SourceFileSnapshot sourceFileBefore = readSourceFile(fileId);

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content("{\"profileId\":\"" + profileId + "\"}"))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(jsonPath("$", aMapWithSize(12)))
              .andExpect(jsonPath("$.status").value("COMPLETED"))
              .andExpect(jsonPath("$.totalRows").value(3))
              .andExpect(jsonPath("$.validRows").value(1))
              .andExpect(jsonPath("$.invalidRows").value(2))
              .andExpect(jsonPath("$.issueCount").value(6))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").isString())
              .andExpect(jsonPath("$.failureReason").value(nullValue()))
              .andReturn();

      UUID runId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      ValidationRun persisted = validationRunRepository.findById(runId).orElseThrow();
      assertThat(persisted.getStatus()).isEqualTo(ValidationRunStatus.COMPLETED);
      assertThat(persisted.getTotalRows()).isEqualTo(3);
      assertThat(persisted.getValidRows()).isEqualTo(1);
      assertThat(persisted.getInvalidRows()).isEqualTo(2);
      assertThat(persisted.getIssueCount()).isEqualTo(6);
      assertThat(persisted.getFinishedAt()).isAfterOrEqualTo(persisted.getStartedAt());
      assertThat(issueCount(runId)).isEqualTo(6);

      mockMvc
          .perform(get("/api/validation-runs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].id").value(runId.toString()))
          .andExpect(jsonPath("$[0].issueCount").value(6));
      mockMvc
          .perform(get("/api/validation-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("COMPLETED"))
          .andExpect(jsonPath("$.validRows").value(1))
          .andExpect(jsonPath("$.invalidRows").value(2));
      mockMvc
          .perform(get("/api/validation-runs/{runId}/issues", runId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(6)))
          .andExpect(jsonPath("$[0]", aMapWithSize(8)))
          .andExpect(jsonPath("$[0].rowNumber").value(2))
          .andExpect(jsonPath("$[0].fieldName").value("active"))
          .andExpect(jsonPath("$[0].ruleType").value("DATA_TYPE"))
          .andExpect(jsonPath("$[0].severity").value("WARNING"))
          .andExpect(jsonPath("$[0].observedValue").value("TRUE"))
          .andExpect(jsonPath("$[1].rowNumber").value(2))
          .andExpect(jsonPath("$[1].fieldName").value("age"))
          .andExpect(jsonPath("$[2].fieldName").value("email"))
          .andExpect(jsonPath("$[3].fieldName").value("name"))
          .andExpect(jsonPath("$[4].rowNumber").value(3))
          .andExpect(jsonPath("$[4].fieldName").value("email"))
          .andExpect(jsonPath("$[5].rowNumber").value(4))
          .andExpect(jsonPath("$[5].fieldName").value("age"));

      assertSourceFileUnchanged(fileId, sourceFileBefore);
    }

    @Test
    void ignoresDisabledRulesAndCompletesHeaderOnlyInput() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId =
          insertSourceFile(datasetId, "customers.csv", "name\n".getBytes(StandardCharsets.UTF_8));
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      insertValidationRule(
          profileId, "missing", ValidationRuleType.REQUIRED_FIELD, "{}", "ERROR", false);
      insertValidationRule(
          profileId, "name", ValidationRuleType.REQUIRED_FIELD, "{}", "ERROR", true);

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content("{\"profileId\":\"" + profileId + "\"}"))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("COMPLETED"))
              .andExpect(jsonPath("$.totalRows").value(0))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andReturn();

      UUID runId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      assertThat(issueCount(runId)).isZero();
    }

    @Test
    void missingRequiredHeaderPersistsFailedRunWithParsedTotalAndNoIssues() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId =
          insertSourceFile(
              datasetId, "customers.csv", "name\nAlice\n".getBytes(StandardCharsets.UTF_8));
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      insertValidationRule(
          profileId, "email", ValidationRuleType.REQUIRED_FIELD, "{}", "ERROR", true);

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content("{\"profileId\":\"" + profileId + "\"}"))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("FAILED"))
              .andExpect(jsonPath("$.totalRows").value(1))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").isString())
              .andExpect(
                  jsonPath("$.failureReason")
                      .value(
                          "CSV header does not contain a field required by the Validation Profile."))
              .andReturn();

      UUID runId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      assertThat(issueCount(runId)).isZero();
      mockMvc
          .perform(get("/api/validation-runs/{runId}/issues", runId))
          .andExpect(status().isOk())
          .andExpect(content().json("[]"));
    }

    @Test
    void validationEngineFailureRollsBackAndRecoversFailedRun() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      doThrow(new IllegalStateException("Internal engine detail."))
          .when(validationEngine)
          .validate(any(), anyList());

      assertRecoveredValidationFailure(fileId, profileId, 2);
    }

    @Test
    void ruleLoadingFailureRollsBackAndRecoversFailedRun() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      doThrow(new IllegalStateException("Internal rule-loading detail."))
          .when(validationRuleAccess)
          .getEnabledRules(profileId);

      assertRecoveredValidationFailure(fileId, profileId, 2);
    }

    @Test
    void issuePersistenceFailureRollsBackAllIssuesAndRecoversFailedRun() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      ValidationIssueDraft invalidDraft =
          new ValidationIssueDraft(
              2,
              "name",
              ValidationRuleType.REQUIRED_FIELD,
              ValidationRuleSeverity.ERROR,
              "x".repeat(501),
              "");
      ValidationResult.Success invalidResult =
          new ValidationResult.Success(new ValidationSummary(2, 1, 1, 1), List.of(invalidDraft));
      doReturn(invalidResult).when(validationEngine).validate(any(), anyList());

      assertRecoveredValidationFailure(fileId, profileId, 2);
    }

    @Test
    void invalidSummaryRollsBackAndRecoversFailedRun() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      ValidationResult.Success invalidResult =
          new ValidationResult.Success(new ValidationSummary(1, 1, 0, 0), List.of());
      doReturn(invalidResult).when(validationEngine).validate(any(), anyList());

      assertRecoveredValidationFailure(fileId, profileId, 2);
    }

    @Test
    void recoveryFailureLeavesCommittedRunPendingWithoutIssues() {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      doThrow(new IllegalStateException("Internal engine detail."))
          .when(validationEngine)
          .validate(any(), anyList());
      doThrow(new IllegalStateException("Recovery database failure."))
          .when(validationRunRecoveryService)
          .recover(any(ValidationProcessingFailureException.class));

      assertThatThrownBy(
              () ->
                  mockMvc.perform(
                      post("/api/files/{fileId}/validation-runs", fileId)
                          .contentType(APPLICATION_JSON)
                          .content("{\"profileId\":\"" + profileId + "\"}")))
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("Recovery database failure.");

      assertThat(validationRunRepository.findAll())
          .singleElement()
          .satisfies(
              run -> {
                assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.PENDING);
                assertThat(run.getTotalRows()).isZero();
                assertThat(run.getStartedAt()).isNull();
                assertThat(run.getFinishedAt()).isNull();
                assertThat(run.getFailureReason()).isNull();
                assertThat(issueCount(run.getId())).isZero();
              });
    }

    @Test
    void repeatedRunsKeepUniquenessStateAndIssuesIsolated() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId =
          insertSourceFile(
              datasetId,
              "customers.csv",
              "email\nsame@example.com\nsame@example.com\n".getBytes(StandardCharsets.UTF_8));
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      insertValidationRule(profileId, "email", ValidationRuleType.UNIQUENESS, "{}", "ERROR", true);
      String requestBody = "{\"profileId\":\"" + profileId + "\"}";

      MvcResult first =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("COMPLETED"))
              .andExpect(jsonPath("$.totalRows").value(2))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(2))
              .andExpect(jsonPath("$.issueCount").value(2))
              .andReturn();
      MvcResult second =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.status").value("COMPLETED"))
              .andExpect(jsonPath("$.issueCount").value(2))
              .andReturn();

      UUID firstRunId =
          UUID.fromString(JsonPath.read(first.getResponse().getContentAsString(), "$.id"));
      UUID secondRunId =
          UUID.fromString(JsonPath.read(second.getResponse().getContentAsString(), "$.id"));
      assertThat(secondRunId).isNotEqualTo(firstRunId);
      assertThat(issueCount(firstRunId)).isEqualTo(2);
      assertThat(issueCount(secondRunId)).isEqualTo(2);
      assertThat(
              jdbcTemplate.queryForObject(
                  "select count(distinct run_id) from validation_issue", Long.class))
          .isEqualTo(2);
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
                assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.COMPLETED);
                assertThat(run.getTotalRows()).isEqualTo(2);
                assertThat(run.getValidRows()).isEqualTo(2);
                assertThat(run.getInvalidRows()).isZero();
                assertThat(run.getIssueCount()).isZero();
                assertThat(run.getStartedAt()).isNotNull();
                assertThat(run.getFinishedAt()).isNotNull();
                assertThat(run.getFinishedAt()).isAfterOrEqualTo(run.getStartedAt());
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

    private void assertRecoveredValidationFailure(UUID fileId, UUID profileId, long totalRows)
        throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/files/{fileId}/validation-runs", fileId)
                      .contentType(APPLICATION_JSON)
                      .content("{\"profileId\":\"" + profileId + "\"}"))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(jsonPath("$", aMapWithSize(12)))
              .andExpect(jsonPath("$.status").value("FAILED"))
              .andExpect(jsonPath("$.totalRows").value(totalRows))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").isString())
              .andExpect(jsonPath("$.finishedAt").isString())
              .andExpect(jsonPath("$.failureReason").value("Validation processing failed."))
              .andReturn();

      String body = result.getResponse().getContentAsString();
      UUID runId = UUID.fromString(JsonPath.read(body, "$.id"));
      Instant startedAt = Instant.parse(JsonPath.read(body, "$.startedAt"));
      Instant finishedAt = Instant.parse(JsonPath.read(body, "$.finishedAt"));
      assertThat(startedAt.getNano() % 1_000).isZero();
      assertThat(finishedAt.getNano() % 1_000).isZero();
      assertThat(finishedAt).isAfterOrEqualTo(startedAt);
      assertThat(issueCount(runId)).isZero();
      assertThat(validationRunRepository.findById(runId).orElseThrow())
          .satisfies(
              run -> {
                assertThat(run.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
                assertThat(run.getTotalRows()).isEqualTo(totalRows);
                assertThat(run.getValidRows()).isZero();
                assertThat(run.getInvalidRows()).isZero();
                assertThat(run.getIssueCount()).isZero();
                assertThat(run.getStartedAt()).isEqualTo(startedAt);
                assertThat(run.getFinishedAt()).isEqualTo(finishedAt);
                assertThat(run.getFailureReason()).isEqualTo("Validation processing failed.");
              });
    }

    private ResultActions assertValidationRunResponse(
        ResultActions response, String path, ValidationRunFixture expected) throws Exception {
      response
          .andExpect(jsonPath(path, aMapWithSize(12)))
          .andExpect(jsonPath(path + ".id").value(expected.id().toString()))
          .andExpect(jsonPath(path + ".datasetId").value(expected.datasetId().toString()))
          .andExpect(jsonPath(path + ".sourceFileId").value(expected.sourceFileId().toString()))
          .andExpect(jsonPath(path + ".profileId").value(expected.profileId().toString()))
          .andExpect(jsonPath(path + ".status").value(expected.status().name()))
          .andExpect(jsonPath(path + ".totalRows").value(expected.totalRows()))
          .andExpect(jsonPath(path + ".validRows").value(expected.validRows()))
          .andExpect(jsonPath(path + ".invalidRows").value(expected.invalidRows()))
          .andExpect(jsonPath(path + ".issueCount").value(expected.issueCount()));

      if (expected.startedAt() == null) {
        response.andExpect(jsonPath(path + ".startedAt").value(nullValue()));
      } else {
        response.andExpect(jsonPath(path + ".startedAt").value(expected.startedAt().toString()));
      }
      if (expected.finishedAt() == null) {
        response.andExpect(jsonPath(path + ".finishedAt").value(nullValue()));
      } else {
        response.andExpect(jsonPath(path + ".finishedAt").value(expected.finishedAt().toString()));
      }
      if (expected.failureReason() == null) {
        response.andExpect(jsonPath(path + ".failureReason").value(nullValue()));
      } else {
        response.andExpect(jsonPath(path + ".failureReason").value(expected.failureReason()));
      }

      return response;
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

  private UUID insertValidationRule(
      UUID profileId,
      String fieldName,
      ValidationRuleType ruleType,
      String parametersJson,
      String severity,
      boolean enabled) {
    UUID ruleId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into validation_rule
          (id, profile_id, field_name, rule_type, parameters_json, severity, enabled)
        values (?, ?, ?, ?, cast(? as jsonb), ?, ?)
        """,
        ruleId,
        profileId,
        fieldName,
        ruleType.name(),
        parametersJson,
        severity,
        enabled);
    return ruleId;
  }

  private long issueCount(UUID runId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from validation_issue where run_id = ?", Long.class, runId);
  }

  private String newMigrationTestSchema() {
    return "validation_run_upgrade_" + UUID.randomUUID().toString().replace("-", "");
  }

  private Flyway flywayForVersionNineSchema(String schema) {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .defaultSchema(schema)
        .schemas(schema)
        .target("9")
        .load();
  }

  private void migrateSchemaToVersionEight(String schema) {
    Flyway versionEight =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .defaultSchema(schema)
            .schemas(schema)
            .target("8")
            .load();

    assertThat(versionEight.migrate().success).isTrue();
  }

  private MigrationRunFixture insertMigrationTestRun(
      String schema, long totalRows, long validRows, long invalidRows, long issueCount) {
    UUID datasetId = UUID.randomUUID();
    UUID sourceFileId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into "
            + schema
            + ".dataset (id, name, description, created_at) values (?, ?, ?, ?)",
        datasetId,
        "Migration dataset",
        null,
        Timestamp.from(CREATED_AT));
    jdbcTemplate.update(
        "insert into "
            + schema
            + ".source_file "
            + "(id, dataset_id, original_filename, content_type, size_bytes, sha256, "
            + "content_bytes, uploaded_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
        sourceFileId,
        datasetId,
        "migration.csv",
        "text/csv",
        CONTENT_BYTES.length,
        sha256(CONTENT_BYTES),
        CONTENT_BYTES,
        Timestamp.from(CREATED_AT));
    jdbcTemplate.update(
        "insert into "
            + schema
            + ".validation_profile (id, dataset_id, name, created_at) values (?, ?, ?, ?)",
        profileId,
        datasetId,
        "Migration profile",
        Timestamp.from(CREATED_AT));
    jdbcTemplate.update(
        "insert into "
            + schema
            + ".validation_run "
            + "(id, dataset_id, source_file_id, profile_id, status, total_rows, valid_rows, "
            + "invalid_rows, issue_count, started_at, finished_at, failure_reason) "
            + "values (?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?, null)",
        runId,
        datasetId,
        sourceFileId,
        profileId,
        totalRows,
        validRows,
        invalidRows,
        issueCount,
        Timestamp.from(STARTED_AT),
        Timestamp.from(FINISHED_AT));
    return new MigrationRunFixture(runId);
  }

  private ValidationRunSnapshot readMigrationRunSnapshot(String schema, UUID runId) {
    return jdbcTemplate.queryForObject(
        "select id, dataset_id, source_file_id, profile_id, status, total_rows, valid_rows, "
            + "invalid_rows, issue_count, started_at, finished_at, failure_reason from "
            + schema
            + ".validation_run where id = ?",
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

  private ValidationRunFixture insertValidationRunFixture(
      UUID id, UUID datasetId, UUID sourceFileId, UUID profileId, ValidationRunStatus status) {
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
        totalRows = 3;
        startedAt = STARTED_AT;
      }
      case COMPLETED -> {
        totalRows = 3;
        validRows = 2;
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

    insertValidationRun(
        id,
        datasetId,
        sourceFileId,
        profileId,
        status.name(),
        totalRows,
        validRows,
        invalidRows,
        issueCount,
        startedAt,
        finishedAt,
        failureReason);

    return new ValidationRunFixture(
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
        failureReason);
  }

  private List<ValidationRunSnapshot> readValidationRunSnapshots() {
    return jdbcTemplate.query(
        """
        select id, dataset_id, source_file_id, profile_id, status, total_rows, valid_rows,
               invalid_rows, issue_count, started_at, finished_at, failure_reason
        from validation_run
        order by id
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
        });
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

  private record MigrationRunFixture(UUID id) {}
}
