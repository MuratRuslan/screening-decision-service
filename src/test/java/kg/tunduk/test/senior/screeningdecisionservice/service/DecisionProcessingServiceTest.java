package kg.tunduk.test.senior.screeningdecisionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.CvParsedEvent;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NonRetryableEventException;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import kg.tunduk.test.senior.screeningdecisionservice.repository.RuleSetRepository;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionWeight;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.ScoreOutcome;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.CriteriaCatalog;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.NormalizationResult;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.UnknownKeyPolicy;
import kg.tunduk.test.senior.screeningdecisionservice.validation.json.CvParsedJsonSchemaValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionProcessingServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private RuleSetRepository ruleSetRepository;

    @Mock
    private DecisionPersistenceService decisionPersistenceService;

    private CvParsedJsonSchemaValidator schemaValidator;
    private CriteriaCatalog criteriaCatalog;
    private String validSampleJson;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("contract/json-schema/cv-parsed.schema.json")) {
            schemaValidator = new CvParsedJsonSchemaValidator(CvParsedJsonSchemaValidator.loadSchema(in));
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("semantic/criteria-catalog.json")) {
            criteriaCatalog = CriteriaCatalog.parse(in);
        }
        validSampleJson = Files.readString(Path.of("java-senior/test-events/cv-parsed-sample.json"));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private DecisionProcessingService service(UnknownKeyPolicy policy) {
        return new DecisionProcessingService(schemaValidator, criteriaCatalog, ruleSetRepository,
                decisionPersistenceService, MAPPER, policy);
    }

    private RuleSetEntity javaSeniorRuleSet() {
        return new RuleSetEntity(UUID.randomUUID(), "java-senior", "v1", Instant.parse("2026-06-04T00:00:00Z"),
                80, 45, List.of(
                new CriterionWeight("java_spring", 25),
                new CriterionWeight("postgres_acid", 20),
                new CriterionWeight("kafka_reliability", 25),
                new CriterionWeight("contracts", 15),
                new CriterionWeight("observability", 15)),
                Instant.now());
    }

    @Test
    void malformedJsonIsNonRetryable() {
        DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process("{not valid json"))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("MALFORMED_JSON"));

        verify(decisionPersistenceService, never()).persist(any(), any(), any(), anyString(), any());
    }

    @Test
    void blankPayloadIsNonRetryable() {
        DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process("   "))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("MALFORMED_JSON"));
    }

    @Test
    void schemaViolationIsNonRetryableWithJsonPointerDetails() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(validSampleJson);
        ((ObjectNode) node.get("criteria").get(0)).put("key", "Invalid Key!");

        DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process(MAPPER.writeValueAsString(node)))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> {
                    NonRetryableEventException nre = (NonRetryableEventException) ex;
                    assertThat(nre.getErrorCode()).isEqualTo("SCHEMA_VALIDATION_ERROR");
                    assertThat(nre.getDetails()).anySatisfy(d -> assertThat(d.pointer()).isEqualTo("/criteria/0/key"));
                });
        verify(decisionPersistenceService, never()).persist(any(), any(), any(), anyString(), any());
    }

    @Test
    void unknownCriterionKeyUnderDlqPolicyIsNonRetryable() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(validSampleJson);
        var criteria = (com.fasterxml.jackson.databind.node.ArrayNode) node.get("criteria");
        ObjectNode extra = MAPPER.createObjectNode();
        extra.put("key", "docker_kubernetes");
        extra.put("result", "OK");
        extra.put("comment", "K8s опыт");
        criteria.add(extra);

        DecisionProcessingService service = service(UnknownKeyPolicy.DLQ);

        assertThatThrownBy(() -> service.process(MAPPER.writeValueAsString(node)))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("UNKNOWN_CRITERION_KEY"));
        verify(decisionPersistenceService, never()).persist(any(), any(), any(), anyString(), any());
    }

    @Test
    void unknownCriterionKeyUnderAuditPolicyStillPersistsDecision() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(validSampleJson);
        var criteria = (com.fasterxml.jackson.databind.node.ArrayNode) node.get("criteria");
        ObjectNode extra = MAPPER.createObjectNode();
        extra.put("key", "docker_kubernetes");
        extra.put("result", "OK");
        extra.put("comment", "K8s опыт");
        criteria.add(extra);

        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(javaSeniorRuleSet()));

        DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);
        service.process(MAPPER.writeValueAsString(node));

        ArgumentCaptor<NormalizationResult> normalizationCaptor = ArgumentCaptor.forClass(NormalizationResult.class);
        verify(decisionPersistenceService).persist(any(CvParsedEvent.class), any(RuleSetEntity.class),
                any(ScoreOutcome.class), anyString(), normalizationCaptor.capture());
        assertThat(normalizationCaptor.getValue().hasUnmapped()).isTrue();
    }

    @Test
    void missingRuleSetIsNonRetryable() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.empty());

        DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process(validSampleJson))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("RULE_SET_NOT_FOUND"));
    }

    @Test
    void validEventIsPersistedAndSetsMdc() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(javaSeniorRuleSet()));

        DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);
        service.process(validSampleJson);

        ArgumentCaptor<CvParsedEvent> eventCaptor = ArgumentCaptor.forClass(CvParsedEvent.class);
        verify(decisionPersistenceService).persist(eventCaptor.capture(), any(RuleSetEntity.class),
                any(ScoreOutcome.class), eq(criteriaCatalog.version()), any(NormalizationResult.class));
        assertThat(eventCaptor.getValue().candidateId()).isEqualTo("senior-asanov-bakyt");
        assertThat(MDC.get("candidateId")).isEqualTo("senior-asanov-bakyt");
        assertThat(MDC.get("eventId")).isEqualTo("660e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void duplicateEventDoesNotPropagateException() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(javaSeniorRuleSet()));
        when(decisionPersistenceService.persist(any(), any(), any(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        // Must not throw - a duplicate is expected steady-state behavior, not a processing error.
        service.process(validSampleJson);
    }
}
