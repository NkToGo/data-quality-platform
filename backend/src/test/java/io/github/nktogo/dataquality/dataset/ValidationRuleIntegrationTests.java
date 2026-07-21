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
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class ValidationRuleIntegrationTests {

  @Container @ServiceConnection
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18.4-alpine");

  @Autowired private DatasetRepository datasetRepository;

  @Autowired private ValidationProfileRepository validationProfileRepository;

  @Autowired private ValidationRuleRepository validationRuleRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private MockMvc mockMvc;

  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void deleteRulesProfilesAndDatasets() {
    validationRuleRepository.deleteAll();
    validationProfileRepository.deleteAll();
    datasetRepository.deleteAll();
  }

  @Nested
  class RepositoryIntegration {

    @Test
    void flywayAppliesValidationRuleMigration() {
      assertThat(flyway.info().pending()).isEmpty();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.validation_rule')::text", String.class))
          .isEqualTo("validation_rule");
      assertThat(
              jdbcTemplate.queryForObject(
                  "select to_regclass('public.ix_validation_rule_profile_id_id')::text",
                  String.class))
          .isEqualTo("ix_validation_rule_profile_id_id");
      assertThat(
              jdbcTemplate.queryForObject(
                  """
                  select data_type
                  from information_schema.columns
                  where table_schema = 'public'
                    and table_name = 'validation_rule'
                    and column_name = 'parameters_json'
                  """,
                  String.class))
          .isEqualTo("jsonb");
    }

    @Test
    @Transactional
    void persistsAndReloadsEveryFieldAndProfileRelationship() {
      ValidationProfile profile = saveProfile("Default validation");
      Map<String, Object> parameters =
          Map.of(
              "minimum",
              1,
              "maximum",
              10,
              "options",
              Map.of("inclusive", true, "labels", List.of("low", "high")));

      ValidationRule saved =
          validationRuleRepository.saveAndFlush(
              new ValidationRule(
                  profile,
                  "amount",
                  ValidationRuleType.NUMERIC_RANGE,
                  parameters,
                  ValidationRuleSeverity.WARNING,
                  false));
      UUID ruleId = saved.getId();

      entityManager.clear();

      ValidationRule reloaded = validationRuleRepository.findById(ruleId).orElseThrow();
      assertThat(reloaded.getId()).isEqualTo(ruleId);
      assertThat(reloaded.getProfile().getId()).isEqualTo(profile.getId());
      assertThat(reloaded.getFieldName()).isEqualTo("amount");
      assertThat(reloaded.getRuleType()).isEqualTo(ValidationRuleType.NUMERIC_RANGE);
      assertThat(reloaded.getParameters()).usingRecursiveComparison().isEqualTo(parameters);
      assertThat(reloaded.getSeverity()).isEqualTo(ValidationRuleSeverity.WARNING);
      assertThat(reloaded.isEnabled()).isFalse();
    }

    @Test
    void persistsAllRuleTypesBothSeveritiesAndEnabledValues() {
      ValidationProfile profile = saveProfile("Default validation");

      for (ValidationRuleType ruleType : ValidationRuleType.values()) {
        validationRuleRepository.saveAndFlush(
            new ValidationRule(
                profile, ruleType.name(), ruleType, Map.of(), ValidationRuleSeverity.ERROR, true));
      }
      validationRuleRepository.saveAndFlush(
          new ValidationRule(
              profile,
              "warning",
              ValidationRuleType.REQUIRED_FIELD,
              Map.of(),
              ValidationRuleSeverity.WARNING,
              false));

      List<ValidationRule> persisted = validationRuleRepository.findAll();
      assertThat(persisted)
          .extracting(ValidationRule::getRuleType)
          .contains(ValidationRuleType.values());
      assertThat(persisted)
          .extracting(ValidationRule::getSeverity)
          .contains(ValidationRuleSeverity.ERROR, ValidationRuleSeverity.WARNING);
      assertThat(persisted).extracting(ValidationRule::isEnabled).contains(true, false);
    }

    @Test
    void permitsDuplicateRules() {
      ValidationProfile profile = saveProfile("Default validation");

      validationRuleRepository.saveAndFlush(
          new ValidationRule(
              profile,
              "email",
              ValidationRuleType.REQUIRED_FIELD,
              Map.of(),
              ValidationRuleSeverity.ERROR,
              true));
      validationRuleRepository.saveAndFlush(
          new ValidationRule(
              profile,
              "email",
              ValidationRuleType.REQUIRED_FIELD,
              Map.of(),
              ValidationRuleSeverity.ERROR,
              true));

      assertThat(validationRuleRepository.findAllByProfileOrderByIdAsc(profile))
          .hasSize(2)
          .extracting(ValidationRule::getFieldName)
          .containsOnly("email");
    }

    @Test
    void databaseRejectsBlankFieldNamesAndUnsupportedEnumValues() {
      ValidationProfile profile = saveProfile("Default validation");

      assertRuleInsertRejected(
          UUID.randomUUID(), profile.getId(), "   ", "REQUIRED_FIELD", "{}", "ERROR", true);
      assertRuleInsertRejected(
          UUID.randomUUID(), profile.getId(), "email", "UNKNOWN", "{}", "ERROR", true);
      assertRuleInsertRejected(
          UUID.randomUUID(), profile.getId(), "email", "REQUIRED_FIELD", "{}", "INFO", true);
    }

    @Test
    void databaseRejectsNonObjectParameters() {
      ValidationProfile profile = saveProfile("Default validation");

      assertRuleInsertRejected(
          UUID.randomUUID(), profile.getId(), "email", "REQUIRED_FIELD", "[]", "ERROR", true);
      assertRuleInsertRejected(
          UUID.randomUUID(),
          profile.getId(),
          "email",
          "REQUIRED_FIELD",
          "\"scalar\"",
          "ERROR",
          true);
    }

    @Test
    void databaseRejectsNullRequiredFields() {
      ValidationProfile profile = saveProfile("Default validation");
      UUID profileId = profile.getId();

      assertRuleInsertRejected(null, profileId, "email", "REQUIRED_FIELD", "{}", "ERROR", true);
      assertRuleInsertRejected(
          UUID.randomUUID(), null, "email", "REQUIRED_FIELD", "{}", "ERROR", true);
      assertRuleInsertRejected(
          UUID.randomUUID(), profileId, null, "REQUIRED_FIELD", "{}", "ERROR", true);
      assertRuleInsertRejected(UUID.randomUUID(), profileId, "email", null, "{}", "ERROR", true);
      assertRuleInsertRejected(
          UUID.randomUUID(), profileId, "email", "REQUIRED_FIELD", null, "ERROR", true);
      assertRuleInsertRejected(
          UUID.randomUUID(), profileId, "email", "REQUIRED_FIELD", "{}", null, true);
      assertRuleInsertRejected(
          UUID.randomUUID(), profileId, "email", "REQUIRED_FIELD", "{}", "ERROR", null);
    }

    @Test
    void databaseRejectsUnknownProfileId() {
      assertRuleInsertRejected(
          UUID.randomUUID(), UUID.randomUUID(), "email", "REQUIRED_FIELD", "{}", "ERROR", true);
    }

    @Test
    void databaseRestrictsProfileDeletionAndRetainsBothRows() {
      ValidationProfile profile = saveProfile("Default validation");
      ValidationRule rule =
          validationRuleRepository.saveAndFlush(
              new ValidationRule(
                  profile,
                  "email",
                  ValidationRuleType.REQUIRED_FIELD,
                  Map.of(),
                  ValidationRuleSeverity.ERROR,
                  true));

      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "delete from validation_profile where id = ?", profile.getId()))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(validationProfileRepository.existsById(profile.getId())).isTrue();
      assertThat(validationRuleRepository.existsById(rule.getId())).isTrue();
    }

    @Test
    void isolatesRulesByProfileAndOrdersByPostgresqlUuidOrder() {
      ValidationProfile selectedProfile = saveProfile("Selected profile");
      ValidationProfile otherProfile = saveProfile("Other profile");
      UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
      UUID otherId = UUID.fromString("00000000-0000-0000-0000-000000000003");

      insertRule(
          secondId,
          selectedProfile.getId(),
          "second",
          "DATA_TYPE",
          "{\"expected\":\"integer\"}",
          "WARNING",
          false);
      insertRule(otherId, otherProfile.getId(), "other", "UNIQUENESS", "{}", "ERROR", true);
      insertRule(firstId, selectedProfile.getId(), "first", "REQUIRED_FIELD", "{}", "ERROR", true);

      assertThat(validationRuleRepository.findAllByProfileOrderByIdAsc(selectedProfile))
          .extracting(ValidationRule::getId)
          .containsExactly(firstId, secondId);
    }
  }

  @Nested
  class ApiIntegration {

    @Test
    void createsAndPersistsValidationRule() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/profiles/{profileId}/rules", profile.getId())
                      .contentType(APPLICATION_JSON)
                      .content(
                          """
                          {
                            "fieldName": "amount",
                            "ruleType": "NUMERIC_RANGE",
                            "parameters": {
                              "minimum": 1,
                              "maximum": 10,
                              "options": {"inclusive": true}
                            },
                            "severity": "WARNING",
                            "enabled": false
                          }
                          """))
              .andExpect(status().isCreated())
              .andExpect(header().doesNotExist("Location"))
              .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
              .andExpect(jsonPath("$", aMapWithSize(7)))
              .andExpect(jsonPath("$.id").isString())
              .andExpect(jsonPath("$.profileId").value(profile.getId().toString()))
              .andExpect(jsonPath("$.fieldName").value("amount"))
              .andExpect(jsonPath("$.ruleType").value("NUMERIC_RANGE"))
              .andExpect(jsonPath("$.parameters.minimum").value(1))
              .andExpect(jsonPath("$.parameters.maximum").value(10))
              .andExpect(jsonPath("$.parameters.options.inclusive").value(true))
              .andExpect(jsonPath("$.severity").value("WARNING"))
              .andExpect(jsonPath("$.enabled").value(false))
              .andReturn();

      UUID ruleId =
          UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
      ValidationRule persisted = validationRuleRepository.findById(ruleId).orElseThrow();
      assertThat(
              jdbcTemplate.queryForObject(
                  "select profile_id from validation_rule where id = ?", UUID.class, ruleId))
          .isEqualTo(profile.getId());
      assertThat(persisted.getFieldName()).isEqualTo("amount");
      assertThat(persisted.getRuleType()).isEqualTo(ValidationRuleType.NUMERIC_RANGE);
      assertThat(persisted.getParameters())
          .containsEntry("minimum", 1)
          .containsEntry("maximum", 10);
      assertThat(persisted.getSeverity()).isEqualTo(ValidationRuleSeverity.WARNING);
      assertThat(persisted.isEnabled()).isFalse();
    }

    @Test
    void acceptsEveryRuleTypeBothSeveritiesAndDisabledRules() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      for (ValidationRuleType ruleType : ValidationRuleType.values()) {
        mockMvc
            .perform(
                post("/api/profiles/{profileId}/rules", profile.getId())
                    .contentType(APPLICATION_JSON)
                    .content(
                        validRequest(
                            ruleType.name(),
                            "ERROR",
                            true,
                            "{\"type\":\"" + ruleType.name() + "\"}")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ruleType").value(ruleType.name()));
      }

      mockMvc
          .perform(
              post("/api/profiles/{profileId}/rules", profile.getId())
                  .contentType(APPLICATION_JSON)
                  .content(validRequest("REQUIRED_FIELD", "WARNING", false, "{}")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.severity").value("WARNING"))
          .andExpect(jsonPath("$.enabled").value(false));

      assertThat(validationRuleRepository.count()).isEqualTo(6);
    }

    @Test
    void rejectsMissingNullBlankAndOversizedFieldNames() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      assertInvalidCreate(
          profile.getId(),
          "{\"ruleType\":\"REQUIRED_FIELD\",\"parameters\":{},\"severity\":\"ERROR\",\"enabled\":true}");
      assertInvalidCreate(
          profile.getId(),
          "{\"fieldName\":null,\"ruleType\":\"REQUIRED_FIELD\",\"parameters\":{},\"severity\":\"ERROR\",\"enabled\":true}");
      assertInvalidCreate(profile.getId(), validRequestWithFieldName(""));
      assertInvalidCreate(profile.getId(), validRequestWithFieldName("   "));
      assertInvalidCreate(profile.getId(), validRequestWithFieldName("f".repeat(256)));
    }

    @Test
    void rejectsMissingNullAndUnknownRuleTypes() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      assertInvalidCreate(
          profile.getId(),
          "{\"fieldName\":\"email\",\"parameters\":{},\"severity\":\"ERROR\",\"enabled\":true}");
      assertInvalidCreate(
          profile.getId(),
          "{\"fieldName\":\"email\",\"ruleType\":null,\"parameters\":{},\"severity\":\"ERROR\",\"enabled\":true}");
      assertInvalidCreate(profile.getId(), validRequest("UNKNOWN", "ERROR", true, "{}"));
    }

    @Test
    void rejectsMissingNullScalarAndArrayParameters() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      assertInvalidCreate(
          profile.getId(),
          "{\"fieldName\":\"email\",\"ruleType\":\"REQUIRED_FIELD\",\"severity\":\"ERROR\",\"enabled\":true}");
      assertInvalidCreate(profile.getId(), validRequest("REQUIRED_FIELD", "ERROR", true, "null"));
      assertInvalidCreate(
          profile.getId(), validRequest("REQUIRED_FIELD", "ERROR", true, "\"scalar\""));
      assertInvalidCreate(profile.getId(), validRequest("REQUIRED_FIELD", "ERROR", true, "[]"));
    }

    @Test
    void rejectsMissingNullAndUnknownSeverities() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      assertInvalidCreate(
          profile.getId(),
          "{\"fieldName\":\"email\",\"ruleType\":\"REQUIRED_FIELD\",\"parameters\":{},\"enabled\":true}");
      assertInvalidCreate(profile.getId(), validRequest("REQUIRED_FIELD", "null", true, "{}"));
      assertInvalidCreate(profile.getId(), validRequest("REQUIRED_FIELD", "INFO", true, "{}"));
    }

    @Test
    void rejectsMissingAndNullEnabledValues() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      assertInvalidCreate(
          profile.getId(),
          "{\"fieldName\":\"email\",\"ruleType\":\"REQUIRED_FIELD\",\"parameters\":{},\"severity\":\"ERROR\"}");
      assertInvalidCreate(
          profile.getId(),
          "{\"fieldName\":\"email\",\"ruleType\":\"REQUIRED_FIELD\",\"parameters\":{},\"severity\":\"ERROR\",\"enabled\":null}");
    }

    @Test
    void returnsProblemDetailWhenCreatingForUnknownProfile() throws Exception {
      UUID profileId = UUID.randomUUID();

      assertValidationProfileNotFound(
          mockMvc.perform(
              post("/api/profiles/{profileId}/rules", profileId)
                  .contentType(APPLICATION_JSON)
                  .content(validRequest("REQUIRED_FIELD", "ERROR", true, "{}"))),
          profileId);
      assertThat(validationRuleRepository.count()).isZero();
    }

    @Test
    void listsNoRulesForExistingProfileAsEmptyArray() throws Exception {
      ValidationProfile profile = saveProfile("Default validation");

      mockMvc
          .perform(get("/api/profiles/{profileId}/rules", profile.getId()))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(content().json("[]"));
    }

    @Test
    void listsOnlySelectedProfileRulesInPostgresqlUuidOrder() throws Exception {
      ValidationProfile selectedProfile = saveProfile("Selected profile");
      ValidationProfile otherProfile = saveProfile("Other profile");
      UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");

      insertRule(
          secondId,
          selectedProfile.getId(),
          "second",
          "DATA_TYPE",
          "{\"expected\":\"integer\"}",
          "WARNING",
          false);
      insertRule(
          UUID.fromString("00000000-0000-0000-0000-000000000003"),
          otherProfile.getId(),
          "other",
          "UNIQUENESS",
          "{}",
          "ERROR",
          true);
      insertRule(firstId, selectedProfile.getId(), "first", "REQUIRED_FIELD", "{}", "ERROR", true);

      mockMvc
          .perform(get("/api/profiles/{profileId}/rules", selectedProfile.getId()))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0]", aMapWithSize(7)))
          .andExpect(jsonPath("$[0].id").value(firstId.toString()))
          .andExpect(jsonPath("$[0].profileId").value(selectedProfile.getId().toString()))
          .andExpect(jsonPath("$[0].fieldName").value("first"))
          .andExpect(jsonPath("$[0].ruleType").value("REQUIRED_FIELD"))
          .andExpect(jsonPath("$[0].parameters", aMapWithSize(0)))
          .andExpect(jsonPath("$[0].severity").value("ERROR"))
          .andExpect(jsonPath("$[0].enabled").value(true))
          .andExpect(jsonPath("$[1]", aMapWithSize(7)))
          .andExpect(jsonPath("$[1].id").value(secondId.toString()))
          .andExpect(jsonPath("$[1].profileId").value(selectedProfile.getId().toString()))
          .andExpect(jsonPath("$[1].fieldName").value("second"))
          .andExpect(jsonPath("$[1].ruleType").value("DATA_TYPE"))
          .andExpect(jsonPath("$[1].parameters.expected").value("integer"))
          .andExpect(jsonPath("$[1].severity").value("WARNING"))
          .andExpect(jsonPath("$[1].enabled").value(false));
    }

    @Test
    void returnsProblemDetailWhenListingUnknownProfile() throws Exception {
      UUID profileId = UUID.randomUUID();

      assertValidationProfileNotFound(
          mockMvc.perform(get("/api/profiles/{profileId}/rules", profileId)), profileId);
    }

    @Test
    void rejectsMalformedProfileIds() throws Exception {
      mockMvc.perform(get("/api/profiles/not-a-uuid/rules")).andExpect(status().isBadRequest());
      mockMvc
          .perform(
              post("/api/profiles/not-a-uuid/rules")
                  .contentType(APPLICATION_JSON)
                  .content(validRequest("REQUIRED_FIELD", "ERROR", true, "{}")))
          .andExpect(status().isBadRequest());
    }

    private void assertInvalidCreate(UUID profileId, String requestBody) throws Exception {
      mockMvc
          .perform(
              post("/api/profiles/{profileId}/rules", profileId)
                  .contentType(APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest());

      assertThat(validationRuleRepository.count()).isZero();
    }
  }

  private Dataset saveDataset(String name) {
    return datasetRepository.saveAndFlush(
        new Dataset(name, null, Instant.parse("2026-07-21T12:00:00.123456Z")));
  }

  private ValidationProfile saveProfile(String name) {
    Dataset dataset = saveDataset(name + " dataset");
    return validationProfileRepository.saveAndFlush(
        new ValidationProfile(dataset, name, Instant.parse("2026-07-21T12:34:56.123456Z")));
  }

  private void insertRule(
      UUID id,
      UUID profileId,
      String fieldName,
      String ruleType,
      String parametersJson,
      String severity,
      Boolean enabled) {
    jdbcTemplate.update(
        """
        insert into validation_rule
          (id, profile_id, field_name, rule_type, parameters_json, severity, enabled)
        values (?, ?, ?, ?, ?::jsonb, ?, ?)
        """,
        id,
        profileId,
        fieldName,
        ruleType,
        parametersJson,
        severity,
        enabled);
  }

  private void assertRuleInsertRejected(
      UUID id,
      UUID profileId,
      String fieldName,
      String ruleType,
      String parametersJson,
      String severity,
      Boolean enabled) {
    assertThatThrownBy(
            () -> insertRule(id, profileId, fieldName, ruleType, parametersJson, severity, enabled))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String validRequestWithFieldName(String fieldName) {
    return """
        {
          "fieldName": "%s",
          "ruleType": "REQUIRED_FIELD",
          "parameters": {},
          "severity": "ERROR",
          "enabled": true
        }
        """
        .formatted(fieldName);
  }

  private String validRequest(
      String ruleType, String severity, boolean enabled, String parametersJson) {
    String renderedSeverity = "null".equals(severity) ? "null" : "\"" + severity + "\"";
    return """
        {
          "fieldName": "email",
          "ruleType": "%s",
          "parameters": %s,
          "severity": %s,
          "enabled": %s
        }
        """
        .formatted(ruleType, parametersJson, renderedSeverity, enabled);
  }

  private void assertValidationProfileNotFound(
      org.springframework.test.web.servlet.ResultActions resultActions, UUID profileId)
      throws Exception {
    resultActions
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$", aMapWithSize(4)))
        .andExpect(jsonPath("$.type").doesNotExist())
        .andExpect(jsonPath("$.title").value("Validation Profile not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(
            jsonPath("$.detail").value("Validation Profile '" + profileId + "' was not found."))
        .andExpect(jsonPath("$.instance").value("/api/profiles/" + profileId + "/rules"));
  }
}
