package io.github.nktogo.dataquality.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
    properties = {
      "app.ingestion.max-file-size=1KB",
      "spring.servlet.multipart.max-file-size=2KB",
      "spring.servlet.multipart.max-request-size=3KB"
    })
@AutoConfigureMockMvc
class SourceFileIntegrationTests {

  private static final byte[] CONTENT_BYTES = "abc".getBytes(StandardCharsets.UTF_8);
  private static final String CONTENT_SHA256 =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
  private static final Instant UPLOADED_AT = Instant.parse("2026-07-25T12:34:56.123456Z");

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private SourceFileRepository sourceFileRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private MockMvc mockMvc;

  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void deleteSourceFilesAndDatasets() {
    jdbcTemplate.update("delete from source_file");
    jdbcTemplate.update("delete from dataset");
  }

  @Nested
  class RepositoryIntegration {

    @Test
    void flywayAppliesSourceFileMigration() {
      assertThat(flyway.info().pending()).isEmpty();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.source_file')::text", String.class))
          .isEqualTo("source_file");
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.ix_source_file_dataset_id')::text", String.class))
          .isEqualTo("ix_source_file_dataset_id");
      assertThat(
              jdbcTemplate.queryForObject(
                  """
                  select data_type
                  from information_schema.columns
                  where table_schema = 'public'
                    and table_name = 'source_file'
                    and column_name = 'content_bytes'
                  """,
                  String.class))
          .isEqualTo("bytea");
    }

    @Test
    @Transactional
    void persistsAndReloadsMetadataAndExactContentBytes() {
      UUID datasetId = insertDataset("Customer import");
      SourceFile saved =
          sourceFileRepository.saveAndFlush(
              new SourceFile(
                  datasetId,
                  "customers.csv",
                  "text/csv",
                  CONTENT_BYTES.length,
                  CONTENT_SHA256,
                  CONTENT_BYTES,
                  UPLOADED_AT));
      UUID sourceFileId = saved.getId();

      entityManager.clear();

      SourceFile reloaded = sourceFileRepository.findById(sourceFileId).orElseThrow();
      assertThat(reloaded.getId()).isEqualTo(sourceFileId);
      assertThat(reloaded.getDatasetId()).isEqualTo(datasetId);
      assertThat(reloaded.getOriginalFilename()).isEqualTo("customers.csv");
      assertThat(reloaded.getContentType()).isEqualTo("text/csv");
      assertThat(reloaded.getSizeBytes()).isEqualTo(CONTENT_BYTES.length);
      assertThat(reloaded.getSha256()).isEqualTo(CONTENT_SHA256);
      assertThat(reloaded.getContentBytes()).containsExactly(CONTENT_BYTES);
      assertThat(reloaded.getUploadedAt()).isEqualTo(UPLOADED_AT);
    }

    @Test
    void permitsDuplicateFilenamesAndChecksums() {
      UUID datasetId = insertDataset("Customer import");

      sourceFileRepository.saveAndFlush(
          new SourceFile(
              datasetId,
              "customers.csv",
              "text/csv",
              CONTENT_BYTES.length,
              CONTENT_SHA256,
              CONTENT_BYTES,
              UPLOADED_AT));
      sourceFileRepository.saveAndFlush(
          new SourceFile(
              datasetId,
              "customers.csv",
              "text/csv",
              CONTENT_BYTES.length,
              CONTENT_SHA256,
              CONTENT_BYTES,
              UPLOADED_AT.plusSeconds(1)));

      assertThat(sourceFileRepository.findAll())
          .hasSize(2)
          .extracting(SourceFile::getSha256)
          .containsOnly(CONTENT_SHA256);
    }

    @Test
    void databaseRejectsInvalidFilenamesAndContentTypes() {
      UUID datasetId = insertDataset("Customer import");

      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "   ",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "folder/customers.csv",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "folder\\customers.csv",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.txt",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "   ",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
    }

    @Test
    void databaseRejectsInvalidSizesChecksumsAndContentLengths() {
      UUID datasetId = insertDataset("Customer import");

      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "text/csv",
          0L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "text/csv",
          3L,
          "not-a-sha256",
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "text/csv",
          4L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
    }

    @Test
    void databaseRejectsRequiredNulls() {
      UUID datasetId = insertDataset("Customer import");

      assertInsertRejected(
          null,
          datasetId,
          "customers.csv",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          null,
          "customers.csv",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          null,
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          null,
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "text/csv",
          null,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "text/csv",
          3L,
          null,
          CONTENT_BYTES,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "text/csv",
          3L,
          CONTENT_SHA256,
          null,
          UPLOADED_AT);
      assertInsertRejected(
          UUID.randomUUID(),
          datasetId,
          "customers.csv",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          null);
    }

    @Test
    void databaseRejectsUnknownDatasetId() {
      assertInsertRejected(
          UUID.randomUUID(),
          UUID.randomUUID(),
          "customers.csv",
          "text/csv",
          3L,
          CONTENT_SHA256,
          CONTENT_BYTES,
          UPLOADED_AT);
    }

    @Test
    void databaseRestrictsDatasetDeletionWhileSourceFileExists() {
      UUID datasetId = insertDataset("Customer import");
      SourceFile sourceFile =
          sourceFileRepository.saveAndFlush(
              new SourceFile(
                  datasetId,
                  "customers.csv",
                  "text/csv",
                  CONTENT_BYTES.length,
                  CONTENT_SHA256,
                  CONTENT_BYTES,
                  UPLOADED_AT));

      assertThatThrownBy(() -> jdbcTemplate.update("delete from dataset where id = ?", datasetId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(sourceFileRepository.existsById(sourceFile.getId())).isTrue();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select exists(select 1 from dataset where id = ?)", Boolean.class, datasetId))
          .isTrue();
    }
  }

  @Nested
  class ApiIntegration {

    @Test
    void uploadsAndPersistsSourceFile() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      MockMultipartFile file =
          new MockMultipartFile("file", "customers.csv", "text/csv", CONTENT_BYTES);

      MvcResult result =
          mockMvc
              .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(file))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", aMapWithSize(7)))
              .andExpect(jsonPath("$.id").isString())
              .andExpect(jsonPath("$.datasetId").value(datasetId.toString()))
              .andExpect(jsonPath("$.originalFilename").value("customers.csv"))
              .andExpect(jsonPath("$.contentType").value("text/csv"))
              .andExpect(jsonPath("$.sizeBytes").value(3))
              .andExpect(jsonPath("$.sha256").value(CONTENT_SHA256))
              .andExpect(jsonPath("$.uploadedAt").isString())
              .andExpect(jsonPath("$.contentBytes").doesNotExist())
              .andReturn();

      UUID sourceFileId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      SourceFile persisted = sourceFileRepository.findById(sourceFileId).orElseThrow();
      assertThat(persisted.getDatasetId()).isEqualTo(datasetId);
      assertThat(persisted.getOriginalFilename()).isEqualTo("customers.csv");
      assertThat(persisted.getContentType()).isEqualTo("text/csv");
      assertThat(persisted.getSizeBytes()).isEqualTo(3);
      assertThat(persisted.getSha256()).isEqualTo(CONTENT_SHA256);
      assertThat(persisted.getContentBytes()).containsExactly(CONTENT_BYTES);
      assertThat(persisted.getUploadedAt()).isNotNull();
    }

    @Test
    void storesSafeBasenamesAndAcceptsCaseInsensitiveCsvSuffixes() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      MockMultipartFile windowsPath =
          new MockMultipartFile("file", "C:\\fakepath\\customers.csv", "text/csv", CONTENT_BYTES);
      MockMultipartFile unixPath =
          new MockMultipartFile("file", "/tmp/ORDERS.CSV", "text/csv", CONTENT_BYTES);

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(windowsPath))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.originalFilename").value("customers.csv"));
      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(unixPath))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.originalFilename").value("ORDERS.CSV"));

      assertThat(sourceFileRepository.findAll())
          .extracting(SourceFile::getOriginalFilename)
          .containsExactlyInAnyOrder("customers.csv", "ORDERS.CSV");
    }

    @Test
    void preservesSubmittedContentTypeAndUsesFallbackWhenMissingOrBlank() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      MockMultipartFile customType =
          new MockMultipartFile(
              "file", "custom.csv", "application/x-data-quality-csv", CONTENT_BYTES);
      MockMultipartFile missingType =
          new MockMultipartFile("file", "fallback.csv", null, CONTENT_BYTES);
      MockMultipartFile blankType =
          new MockMultipartFile("file", "blank.csv", "   ", CONTENT_BYTES);

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(customType))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.contentType").value("application/x-data-quality-csv"));
      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(missingType))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.contentType").value("application/octet-stream"));
      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(blankType))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.contentType").value("application/octet-stream"));
    }

    @Test
    void acceptsMaximumFileSizeAndFilenameLength() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      String filename = "f".repeat(251) + ".csv";
      MockMultipartFile file = new MockMultipartFile("file", filename, "text/csv", new byte[1024]);

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(file))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.originalFilename").value(filename))
          .andExpect(jsonPath("$.sizeBytes").value(1024));
    }

    @Test
    void permitsDuplicateUploads() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      MockMultipartFile first =
          new MockMultipartFile("file", "customers.csv", "text/csv", CONTENT_BYTES);
      MockMultipartFile second =
          new MockMultipartFile("file", "customers.csv", "text/csv", CONTENT_BYTES);

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(first))
          .andExpect(status().isCreated());
      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(second))
          .andExpect(status().isCreated());

      assertThat(sourceFileRepository.findAll())
          .hasSize(2)
          .extracting(SourceFile::getSha256)
          .containsOnly(CONTENT_SHA256);
    }

    @Test
    void rejectsMissingFilePartWithoutWritingSourceFile() throws Exception {
      UUID datasetId = insertDataset("Customer import");

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId))
          .andExpect(status().isBadRequest());

      assertThat(sourceFileRepository.count()).isZero();
    }

    @Test
    void rejectsEmptyFileWithProblemDetail() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      MockMultipartFile file =
          new MockMultipartFile("file", "customers.csv", "text/csv", new byte[0]);

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(file))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Invalid source file"))
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.detail").value("The uploaded file must not be empty."))
          .andExpect(jsonPath("$.instance").value("/api/datasets/" + datasetId + "/files"));

      assertThat(sourceFileRepository.count()).isZero();
    }

    @Test
    void rejectsInvalidFilenamesWithoutWritingSourceFile() throws Exception {
      UUID datasetId = insertDataset("Customer import");

      assertInvalidUpload(
          datasetId, new MockMultipartFile("file", null, "text/csv", CONTENT_BYTES));
      assertInvalidUpload(
          datasetId, new MockMultipartFile("file", "   ", "text/csv", CONTENT_BYTES));
      assertInvalidUpload(
          datasetId, new MockMultipartFile("file", "/tmp/", "text/csv", CONTENT_BYTES));
      assertInvalidUpload(
          datasetId, new MockMultipartFile("file", "customers.txt", "text/csv", CONTENT_BYTES));
      assertInvalidUpload(
          datasetId,
          new MockMultipartFile("file", "f".repeat(252) + ".csv", "text/csv", CONTENT_BYTES));
    }

    @Test
    void rejectsOversizedContentTypeWithoutWritingSourceFile() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      MockMultipartFile file =
          new MockMultipartFile("file", "customers.csv", "t".repeat(256), CONTENT_BYTES);

      assertInvalidUpload(datasetId, file);
    }

    @Test
    void rejectsOversizedFileWithProblemDetail() throws Exception {
      UUID datasetId = insertDataset("Customer import");
      MockMultipartFile file =
          new MockMultipartFile("file", "customers.csv", "text/csv", new byte[1025]);

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(file))
          .andExpect(status().isContentTooLarge())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Source file too large"))
          .andExpect(jsonPath("$.status").value(413))
          .andExpect(
              jsonPath("$.detail")
                  .value("The uploaded file exceeds the configured maximum size of 1024 bytes."))
          .andExpect(jsonPath("$.instance").value("/api/datasets/" + datasetId + "/files"));

      assertThat(sourceFileRepository.count()).isZero();
    }

    @Test
    void returnsProblemDetailWhenUploadingForUnknownDataset() throws Exception {
      UUID datasetId = UUID.randomUUID();
      MockMultipartFile file =
          new MockMultipartFile("file", "customers.csv", "text/csv", CONTENT_BYTES);

      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(file))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$", aMapWithSize(4)))
          .andExpect(jsonPath("$.type").doesNotExist())
          .andExpect(jsonPath("$.title").value("Dataset not found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("Dataset '" + datasetId + "' was not found."))
          .andExpect(jsonPath("$.instance").value("/api/datasets/" + datasetId + "/files"));

      assertThat(sourceFileRepository.count()).isZero();
    }

    @Test
    void rejectsMalformedDatasetIdWithoutWritingSourceFile() throws Exception {
      MockMultipartFile file =
          new MockMultipartFile("file", "customers.csv", "text/csv", CONTENT_BYTES);

      mockMvc
          .perform(multipart("/api/datasets/not-a-uuid/files").file(file))
          .andExpect(status().isBadRequest());

      assertThat(sourceFileRepository.count()).isZero();
    }

    @Test
    void rejectsNonMultipartRequestWithoutWritingSourceFile() throws Exception {
      UUID datasetId = insertDataset("Customer import");

      mockMvc
          .perform(
              post("/api/datasets/{datasetId}/files", datasetId)
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isUnsupportedMediaType());

      assertThat(sourceFileRepository.count()).isZero();
    }

    private void assertInvalidUpload(UUID datasetId, MockMultipartFile file) throws Exception {
      mockMvc
          .perform(multipart("/api/datasets/{datasetId}/files", datasetId).file(file))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.title").value("Invalid source file"))
          .andExpect(jsonPath("$.status").value(400));

      assertThat(sourceFileRepository.count()).isZero();
    }
  }

  private UUID insertDataset(String name) {
    UUID datasetId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into dataset (id, name, description, created_at) values (?, ?, ?, ?)",
        datasetId,
        name,
        null,
        Timestamp.from(Instant.parse("2026-07-25T12:00:00.123456Z")));
    return datasetId;
  }

  private void insertSourceFile(
      UUID id,
      UUID datasetId,
      String originalFilename,
      String contentType,
      Long sizeBytes,
      String sha256,
      byte[] contentBytes,
      Instant uploadedAt) {
    jdbcTemplate.update(
        """
        insert into source_file
          (id, dataset_id, original_filename, content_type, size_bytes, sha256,
           content_bytes, uploaded_at)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        datasetId,
        originalFilename,
        contentType,
        sizeBytes,
        sha256,
        contentBytes,
        uploadedAt == null ? null : Timestamp.from(uploadedAt));
  }

  private void assertInsertRejected(
      UUID id,
      UUID datasetId,
      String originalFilename,
      String contentType,
      Long sizeBytes,
      String sha256,
      byte[] contentBytes,
      Instant uploadedAt) {
    assertThatThrownBy(
            () ->
                insertSourceFile(
                    id,
                    datasetId,
                    originalFilename,
                    contentType,
                    sizeBytes,
                    sha256,
                    contentBytes,
                    uploadedAt))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
