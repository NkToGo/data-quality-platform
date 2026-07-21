package io.github.nktogo.dataquality.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
class ValidationProfileIntegrationTests {

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private DatasetRepository datasetRepository;

  @Autowired private ValidationProfileRepository validationProfileRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private MockMvc mockMvc;

  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void deleteProfilesAndDatasets() {
    validationProfileRepository.deleteAll();
    datasetRepository.deleteAll();
  }

  @Nested
  class RepositoryIntegration {

    @Test
    void flywayAppliesValidationProfileMigration() {
      assertThat(flyway.info().pending()).isEmpty();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.validation_profile')::text", String.class))
          .isEqualTo("validation_profile");
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.ix_validation_profile_dataset_created_at_id')::text",
                  String.class))
          .isEqualTo("ix_validation_profile_dataset_created_at_id");
    }

    @Test
    @Transactional
    void persistsAndReloadsValidationProfileFieldsAndRelationship() {
      Dataset dataset = saveDataset("Customer import");
      Instant createdAt = Instant.parse("2026-07-21T12:34:56.123456Z");
      ValidationProfile saved =
          validationProfileRepository.saveAndFlush(
              new ValidationProfile(dataset, "Default validation", createdAt));
      UUID profileId = saved.getId();

      entityManager.clear();

      ValidationProfile reloaded = validationProfileRepository.findById(profileId).orElseThrow();
      assertThat(reloaded.getId()).isEqualTo(profileId);
      assertThat(reloaded.getDataset().getId()).isEqualTo(dataset.getId());
      assertThat(reloaded.getName()).isEqualTo("Default validation");
      assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void permitsDuplicateNamesWithinDataset() {
      Dataset dataset = saveDataset("Customer import");
      Instant firstCreatedAt = Instant.parse("2026-07-21T12:34:56.123456Z");

      validationProfileRepository.saveAndFlush(
          new ValidationProfile(dataset, "Duplicate name", firstCreatedAt));
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(dataset, "Duplicate name", firstCreatedAt.plusSeconds(1)));

      assertThat(validationProfileRepository.findAllByDatasetOrderByCreatedAtAscIdAsc(dataset))
          .extracting(ValidationProfile::getName)
          .containsExactly("Duplicate name", "Duplicate name");
    }

    @Test
    void databaseRejectsBlankNames() {
      Dataset dataset = saveDataset("Customer import");

      assertThatThrownBy(
              () ->
                  insertProfile(
                      UUID.randomUUID(),
                      dataset.getId(),
                      "   ",
                      Instant.parse("2026-07-21T12:34:56.123456Z")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownDatasetId() {
      assertThatThrownBy(
              () ->
                  insertProfile(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      "Orphan profile",
                      Instant.parse("2026-07-21T12:34:56.123456Z")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRestrictsDatasetDeletionWhileProfileExists() {
      Dataset dataset = saveDataset("Customer import");
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(
              dataset, "Default validation", Instant.parse("2026-07-21T12:34:56.123456Z")));

      assertThatThrownBy(
              () -> jdbcTemplate.update("delete from dataset where id = ?", dataset.getId()))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void isolatesProfilesByDatasetAndOrdersByCreationTime() {
      Dataset firstDataset = saveDataset("First dataset");
      Dataset secondDataset = saveDataset("Second dataset");
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(
              firstDataset, "Later profile", Instant.parse("2026-07-21T12:35:56.123456Z")));
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(
              secondDataset, "Other profile", Instant.parse("2026-07-21T12:33:56.123456Z")));
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(
              firstDataset, "Earlier profile", Instant.parse("2026-07-21T12:34:56.123456Z")));

      assertThat(validationProfileRepository.findAllByDatasetOrderByCreatedAtAscIdAsc(firstDataset))
          .extracting(ValidationProfile::getName)
          .containsExactly("Earlier profile", "Later profile");
    }

    @Test
    void ordersEqualCreationTimesById() {
      Dataset dataset = saveDataset("Customer import");
      Instant createdAt = Instant.parse("2026-07-21T12:34:56.123456Z");
      ValidationProfile first =
          validationProfileRepository.saveAndFlush(
              new ValidationProfile(dataset, "First profile", createdAt));
      ValidationProfile second =
          validationProfileRepository.saveAndFlush(
              new ValidationProfile(dataset, "Second profile", createdAt));
      List<UUID> expectedIds =
          List.of(first.getId(), second.getId()).stream()
              .sorted(Comparator.comparing(UUID::toString))
              .toList();

      assertThat(validationProfileRepository.findAllByDatasetOrderByCreatedAtAscIdAsc(dataset))
          .extracting(ValidationProfile::getId)
          .containsExactlyElementsOf(expectedIds);
    }
  }

  @Nested
  class ApiIntegration {

    @Test
    void createsAndPersistsValidationProfile() throws Exception {
      Dataset dataset = saveDataset("Customer import");

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/datasets/{datasetId}/profiles", dataset.getId())
                      .contentType(APPLICATION_JSON)
                      .content("{\"name\":\"Default validation\"}"))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", aMapWithSize(4)))
              .andExpect(jsonPath("$.id").isString())
              .andExpect(jsonPath("$.datasetId").value(dataset.getId().toString()))
              .andExpect(jsonPath("$.name").value("Default validation"))
              .andExpect(jsonPath("$.createdAt").isString())
              .andReturn();

      String profileId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
      UUID persistedProfileId = UUID.fromString(profileId);
      ValidationProfile persisted =
          validationProfileRepository.findById(persistedProfileId).orElseThrow();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select dataset_id from validation_profile where id = ?",
                  UUID.class,
                  persistedProfileId))
          .isEqualTo(dataset.getId());
      assertThat(persisted.getName()).isEqualTo("Default validation");
      assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsMissingAndNullNamesWithoutWritingProfile() throws Exception {
      Dataset dataset = saveDataset("Customer import");

      assertInvalidCreate(dataset.getId(), "{}");
      assertInvalidCreate(dataset.getId(), "{\"name\":null}");
    }

    @Test
    void rejectsBlankNamesWithoutWritingProfile() throws Exception {
      Dataset dataset = saveDataset("Customer import");

      assertInvalidCreate(dataset.getId(), "{\"name\":\"\"}");
      assertInvalidCreate(dataset.getId(), "{\"name\":\"   \"}");
    }

    @Test
    void rejectsOversizedNameWithoutWritingProfile() throws Exception {
      Dataset dataset = saveDataset("Customer import");

      assertInvalidCreate(dataset.getId(), "{\"name\":\"" + "n".repeat(256) + "\"}");
    }

    @Test
    void returnsProblemDetailWhenCreatingForUnknownDataset() throws Exception {
      UUID datasetId = UUID.randomUUID();

      assertDatasetNotFound(
          mockMvc.perform(
              post("/api/datasets/{datasetId}/profiles", datasetId)
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":\"Default validation\"}")),
          datasetId);
      assertThat(validationProfileRepository.count()).isZero();
    }

    @Test
    void listsNoProfilesForExistingDatasetAsEmptyArray() throws Exception {
      Dataset dataset = saveDataset("Customer import");

      mockMvc
          .perform(get("/api/datasets/{datasetId}/profiles", dataset.getId()))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(content().json("[]"));
    }

    @Test
    void listsOnlySelectedDatasetProfilesInDeterministicOrder() throws Exception {
      Dataset firstDataset = saveDataset("First dataset");
      Dataset secondDataset = saveDataset("Second dataset");
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(
              firstDataset, "Later profile", Instant.parse("2026-07-21T12:35:56.123456Z")));
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(
              secondDataset, "Other profile", Instant.parse("2026-07-21T12:33:56.123456Z")));
      validationProfileRepository.saveAndFlush(
          new ValidationProfile(
              firstDataset, "Earlier profile", Instant.parse("2026-07-21T12:34:56.123456Z")));

      mockMvc
          .perform(get("/api/datasets/{datasetId}/profiles", firstDataset.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0]", aMapWithSize(4)))
          .andExpect(jsonPath("$[0].datasetId").value(firstDataset.getId().toString()))
          .andExpect(jsonPath("$[0].name").value("Earlier profile"))
          .andExpect(jsonPath("$[1].datasetId").value(firstDataset.getId().toString()))
          .andExpect(jsonPath("$[1].name").value("Later profile"));
    }

    @Test
    void returnsProblemDetailWhenListingUnknownDataset() throws Exception {
      UUID datasetId = UUID.randomUUID();

      assertDatasetNotFound(
          mockMvc.perform(get("/api/datasets/{datasetId}/profiles", datasetId)), datasetId);
    }

    @Test
    void rejectsMalformedDatasetIds() throws Exception {
      mockMvc.perform(get("/api/datasets/not-a-uuid/profiles")).andExpect(status().isBadRequest());
      mockMvc
          .perform(
              post("/api/datasets/not-a-uuid/profiles")
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":\"Default validation\"}"))
          .andExpect(status().isBadRequest());
    }

    private void assertInvalidCreate(UUID datasetId, String requestBody) throws Exception {
      mockMvc
          .perform(
              post("/api/datasets/{datasetId}/profiles", datasetId)
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest());

      assertThat(validationProfileRepository.count()).isZero();
    }
  }

  private Dataset saveDataset(String name) {
    return datasetRepository.saveAndFlush(
        new Dataset(name, null, Instant.parse("2026-07-21T12:00:00.123456Z")));
  }

  private void insertProfile(UUID id, UUID datasetId, String name, Instant createdAt) {
    jdbcTemplate.update(
        "insert into validation_profile (id, dataset_id, name, created_at) values (?, ?, ?, ?)",
        id,
        datasetId,
        name,
        Timestamp.from(createdAt));
  }

  private void assertDatasetNotFound(
      org.springframework.test.web.servlet.ResultActions resultActions, UUID datasetId)
      throws Exception {
    resultActions
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$", aMapWithSize(4)))
        .andExpect(jsonPath("$.type").doesNotExist())
        .andExpect(jsonPath("$.title").value("Dataset not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Dataset '" + datasetId + "' was not found."))
        .andExpect(jsonPath("$.instance").value("/api/datasets/" + datasetId + "/profiles"));
  }
}
