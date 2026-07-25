package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

  private static final byte[] CONTENT_BYTES = "abc".getBytes(StandardCharsets.UTF_8);
  private static final String CONTENT_SHA256 =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
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
    void flywayAppliesValidationRunMigration() {
      assertThat(flyway.info().pending()).isEmpty();
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
              "ck_validation_run_pending_state");
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

      for (ValidationRunStatus runStatus : ValidationRunStatus.values()) {
        insertValidationRun(
            UUID.randomUUID(),
            datasetId,
            fileId,
            profileId,
            runStatus.name(),
            0L,
            0L,
            0L,
            0L,
            null,
            null,
            null);
      }

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
    void createsPendingRunAndLeavesSourceFileBytesUnchanged() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      byte[] contentBefore =
          jdbcTemplate.queryForObject(
              "select content_bytes from source_file where id = ?", byte[].class, fileId);

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
              .andExpect(jsonPath("$.status").value("PENDING"))
              .andExpect(jsonPath("$.totalRows").value(0))
              .andExpect(jsonPath("$.validRows").value(0))
              .andExpect(jsonPath("$.invalidRows").value(0))
              .andExpect(jsonPath("$.issueCount").value(0))
              .andExpect(jsonPath("$.startedAt").value(nullValue()))
              .andExpect(jsonPath("$.finishedAt").value(nullValue()))
              .andExpect(jsonPath("$.failureReason").value(nullValue()))
              .andReturn();

      UUID runId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      ValidationRun persisted = validationRunRepository.findById(runId).orElseThrow();
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
      assertThat(
              jdbcTemplate.queryForObject(
                  "select content_bytes from source_file where id = ?", byte[].class, fileId))
          .containsExactly(contentBefore);
    }

    @Test
    void permitsRepeatedRunCreation() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      UUID fileId = insertSourceFile(datasetId, "customers.csv");
      UUID profileId = insertValidationProfile(datasetId, "Default validation");
      String requestBody = "{\"profileId\":\"" + profileId + "\"}";

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
      assertThat(validationRunRepository.count()).isEqualTo(2);
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
        CONTENT_BYTES.length,
        CONTENT_SHA256,
        CONTENT_BYTES,
        Timestamp.from(CREATED_AT));
    return fileId;
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
}
