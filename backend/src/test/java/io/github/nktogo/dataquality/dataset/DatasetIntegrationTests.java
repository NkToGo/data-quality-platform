package io.github.nktogo.dataquality.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DatasetIntegrationTests {

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private DatasetRepository datasetRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void deleteDatasets() {
    datasetRepository.deleteAll();
  }

  @Nested
  class RepositoryIntegration {

    @Test
    void flywayAppliesDatasetMigration() {
      assertThat(flyway.info().pending()).isEmpty();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.dataset')::text", String.class))
          .isEqualTo("dataset");
    }

    @Test
    void persistsAndReloadsDatasetFields() {
      Instant createdAt = Instant.parse("2026-07-20T12:34:56.123456Z");

      Dataset saved =
          datasetRepository.saveAndFlush(new Dataset("Customer import", "Source data", createdAt));

      assertThat(saved.getId()).isNotNull();

      Dataset reloaded = datasetRepository.findById(saved.getId()).orElseThrow();
      assertThat(reloaded.getName()).isEqualTo("Customer import");
      assertThat(reloaded.getDescription()).isEqualTo("Source data");
      assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void permitsNullableDescriptionsAndDuplicateNames() {
      Instant firstCreatedAt = Instant.parse("2026-07-20T12:34:56.123456Z");
      Instant secondCreatedAt = firstCreatedAt.plusSeconds(1);

      datasetRepository.saveAndFlush(new Dataset("Shared name", null, firstCreatedAt));
      datasetRepository.saveAndFlush(
          new Dataset("Shared name", "Second description", secondCreatedAt));

      assertThat(datasetRepository.findAllByOrderByCreatedAtAscIdAsc())
          .extracting(Dataset::getName)
          .containsExactly("Shared name", "Shared name");
      assertThat(datasetRepository.findAllByOrderByCreatedAtAscIdAsc().getFirst().getDescription())
          .isNull();
    }

    @Test
    void ordersDatasetsByCreationTime() {
      datasetRepository.saveAndFlush(
          new Dataset("Later dataset", null, Instant.parse("2026-07-20T12:35:56.123456Z")));
      datasetRepository.saveAndFlush(
          new Dataset("Earlier dataset", null, Instant.parse("2026-07-20T12:34:56.123456Z")));

      assertThat(datasetRepository.findAllByOrderByCreatedAtAscIdAsc())
          .extracting(Dataset::getName)
          .containsExactly("Earlier dataset", "Later dataset");
    }

    @Test
    void ordersEqualCreationTimesById() {
      Instant createdAt = Instant.parse("2026-07-20T12:34:56.123456Z");
      Dataset first = datasetRepository.saveAndFlush(new Dataset("First", null, createdAt));
      Dataset second = datasetRepository.saveAndFlush(new Dataset("Second", null, createdAt));
      List<UUID> expectedIds =
          List.of(first.getId(), second.getId()).stream()
              .sorted(Comparator.comparing(UUID::toString))
              .toList();

      assertThat(datasetRepository.findAllByOrderByCreatedAtAscIdAsc())
          .extracting(Dataset::getId)
          .containsExactlyElementsOf(expectedIds);
    }

    @Test
    void databaseRejectsBlankNames() {
      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "insert into dataset (id, name, description, created_at) values (?, ?, ?, ?)",
                      UUID.randomUUID(),
                      "   ",
                      null,
                      Timestamp.from(Instant.now())))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  class ApiIntegration {

    @Test
    void createsAndPersistsDataset() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/datasets")
                      .contentType(APPLICATION_JSON)
                      .content(
                          """
                          {
                            "name": "Customer import",
                            "description": "Manual import"
                          }
                          """))
              .andExpect(status().isCreated())
              .andExpect(header().string("Location", matchesPattern("/api/datasets/[0-9a-f-]{36}")))
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", aMapWithSize(4)))
              .andExpect(jsonPath("$.id").isString())
              .andExpect(jsonPath("$.name").value("Customer import"))
              .andExpect(jsonPath("$.description").value("Manual import"))
              .andExpect(jsonPath("$.createdAt").isString())
              .andReturn();

      String location = result.getResponse().getHeader("Location");
      assertThat(location).isNotNull();
      UUID datasetId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

      Dataset persisted = datasetRepository.findById(datasetId).orElseThrow();
      assertThat(persisted.getName()).isEqualTo("Customer import");
      assertThat(persisted.getDescription()).isEqualTo("Manual import");
      assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void createsDatasetWithoutDescription() throws Exception {
      mockMvc
          .perform(
              post("/api/datasets")
                  .contentType(APPLICATION_JSON)
                  .content("{\"name\":\"No description\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.description").value(nullValue()));

      assertThat(datasetRepository.findAll())
          .singleElement()
          .extracting(Dataset::getDescription)
          .isNull();
    }

    @Test
    void rejectsMissingNameWithoutWritingDataset() throws Exception {
      assertInvalidCreate("{\"description\":\"Missing name\"}");
    }

    @Test
    void rejectsNullNameWithoutWritingDataset() throws Exception {
      assertInvalidCreate("{\"name\":null}");
    }

    @Test
    void rejectsBlankNameWithoutWritingDataset() throws Exception {
      assertInvalidCreate("{\"name\":\"\"}");
      assertInvalidCreate("{\"name\":\"   \"}");
    }

    @Test
    void rejectsOversizedNameWithoutWritingDataset() throws Exception {
      assertInvalidCreate("{\"name\":\"" + "n".repeat(256) + "\"}");
    }

    @Test
    void rejectsOversizedDescriptionWithoutWritingDataset() throws Exception {
      assertInvalidCreate("{\"name\":\"Valid name\",\"description\":\"" + "d".repeat(2001) + "\"}");
    }

    @Test
    void listsNoDatasetsAsEmptyArray() throws Exception {
      mockMvc
          .perform(get("/api/datasets"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(content().json("[]"));
    }

    @Test
    void listsDatasetResponsesInCreationOrder() throws Exception {
      datasetRepository.saveAndFlush(
          new Dataset("Later dataset", null, Instant.parse("2026-07-20T12:35:56.123456Z")));
      datasetRepository.saveAndFlush(
          new Dataset("Earlier dataset", null, Instant.parse("2026-07-20T12:34:56.123456Z")));

      mockMvc
          .perform(get("/api/datasets"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0]", aMapWithSize(4)))
          .andExpect(jsonPath("$[0].name").value("Earlier dataset"))
          .andExpect(jsonPath("$[1].name").value("Later dataset"));
    }

    @Test
    void retrievesDatasetById() throws Exception {
      Dataset dataset =
          datasetRepository.saveAndFlush(
              new Dataset(
                  "Requested dataset",
                  "Requested description",
                  Instant.parse("2026-07-20T12:34:56.123456Z")));

      mockMvc
          .perform(get("/api/datasets/{datasetId}", dataset.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.id").value(dataset.getId().toString()))
          .andExpect(jsonPath("$.name").value("Requested dataset"))
          .andExpect(jsonPath("$.description").value("Requested description"))
          .andExpect(jsonPath("$.createdAt").value("2026-07-20T12:34:56.123456Z"));
    }

    @Test
    void returnsProblemDetailForUnknownDataset() throws Exception {
      UUID datasetId = UUID.randomUUID();

      mockMvc
          .perform(get("/api/datasets/{datasetId}", datasetId))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.type").doesNotExist())
          .andExpect(jsonPath("$.title").value("Dataset not found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("Dataset '" + datasetId + "' was not found."))
          .andExpect(jsonPath("$.instance").value("/api/datasets/" + datasetId));
    }

    @Test
    void rejectsMalformedDatasetId() throws Exception {
      mockMvc.perform(get("/api/datasets/not-a-uuid")).andExpect(status().isBadRequest());
    }

    private void assertInvalidCreate(String requestBody) throws Exception {
      mockMvc
          .perform(post("/api/datasets").contentType(APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest());

      assertThat(datasetRepository.count()).isZero();
    }
  }
}
