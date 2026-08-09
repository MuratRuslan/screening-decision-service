package kg.tunduk.cvscan.screening.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.tracing.Tracer;
import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import kg.tunduk.cvscan.screening.exception.NonRetryableEventException;
import kg.tunduk.cvscan.screening.model.RuleSetEntity;
import kg.tunduk.cvscan.screening.precheck.PrecheckOrchestrator;
import kg.tunduk.cvscan.screening.repository.RuleSetRepository;
import kg.tunduk.cvscan.screening.scoring.CriterionWeight;
import kg.tunduk.cvscan.screening.scoring.ScoreOutcome;
import kg.tunduk.cvscan.screening.semantic.CriteriaCatalog;
import kg.tunduk.cvscan.screening.semantic.NormalizationResult;
import kg.tunduk.cvscan.screening.semantic.UnknownKeyPolicy;
import kg.tunduk.cvscan.screening.validation.json.CvParsedJsonSchemaValidator;
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

    @Mock
    private PrecheckOrchestrator precheckOrchestrator;

    @Mock
    private Tracer tracer;

    private CvParsedJsonSchemaValidator schemaValidator;
    private CriteriaCatalog criteriaCatalog;
    private String validSampleJson;

    @BeforeEach
    void setUp() throws Exception {
        try (final InputStream in = getClass().getClassLoader().getResourceAsStream("contract/json-schema/cv-parsed.schema.json")) {
            schemaValidator = new CvParsedJsonSchemaValidator(CvParsedJsonSchemaValidator.loadSchema(in));
        }
        try (final InputStream in = getClass().getClassLoader().getResourceAsStream("semantic/criteria-catalog.json")) {
            criteriaCatalog = CriteriaCatalog.parse(in);
        }
        validSampleJson = Files.readString(Path.of("java-senior/test-events/cv-parsed-sample.json"));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private DecisionProcessingService service(final UnknownKeyPolicy policy) {
        return new DecisionProcessingService(schemaValidator, criteriaCatalog, ruleSetRepository,
                precheckOrchestrator, decisionPersistenceService, MAPPER, policy, tracer);
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
        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process("{not valid json"))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("MALFORMED_JSON"));

        verify(decisionPersistenceService, never()).persist(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void blankPayloadIsNonRetryable() {
        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process("   "))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("MALFORMED_JSON"));
    }

    @Test
    void schemaViolationIsNonRetryableWithJsonPointerDetails() throws Exception {
        final ObjectNode node = (ObjectNode) MAPPER.readTree(validSampleJson);
        ((ObjectNode) node.get("criteria").get(0)).put("key", "Invalid Key!");

        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process(MAPPER.writeValueAsString(node)))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> {
                    final NonRetryableEventException nre = (NonRetryableEventException) ex;
                    assertThat(nre.getErrorCode()).isEqualTo("SCHEMA_VALIDATION_ERROR");
                    assertThat(nre.getDetails()).anySatisfy(d -> assertThat(d.getPointer().get()).isEqualTo("/criteria/0/key"));
                });
        verify(decisionPersistenceService, never()).persist(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void unknownCriterionKeyUnderDlqPolicyIsNonRetryable() throws Exception {
        final ObjectNode node = (ObjectNode) MAPPER.readTree(validSampleJson);
        final var criteria = (com.fasterxml.jackson.databind.node.ArrayNode) node.get("criteria");
        final ObjectNode extra = MAPPER.createObjectNode();
        extra.put("key", "docker_kubernetes");
        extra.put("result", "OK");
        extra.put("comment", "K8s опыт");
        criteria.add(extra);

        final DecisionProcessingService service = service(UnknownKeyPolicy.DLQ);

        assertThatThrownBy(() -> service.process(MAPPER.writeValueAsString(node)))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("UNKNOWN_CRITERION_KEY"));
        verify(decisionPersistenceService, never()).persist(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void unknownCriterionKeyUnderAuditPolicyStillPersistsDecision() throws Exception {
        final ObjectNode node = (ObjectNode) MAPPER.readTree(validSampleJson);
        final var criteria = (com.fasterxml.jackson.databind.node.ArrayNode) node.get("criteria");
        final ObjectNode extra = MAPPER.createObjectNode();
        extra.put("key", "docker_kubernetes");
        extra.put("result", "OK");
        extra.put("comment", "K8s опыт");
        criteria.add(extra);

        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(javaSeniorRuleSet()));
        when(precheckOrchestrator.runAll(any())).thenReturn(List.of());

        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);
        service.process(MAPPER.writeValueAsString(node));

        final ArgumentCaptor<NormalizationResult> normalizationCaptor = ArgumentCaptor.forClass(NormalizationResult.class);
        verify(decisionPersistenceService).persist(any(CvParsedEvent.class), any(RuleSetEntity.class),
                any(ScoreOutcome.class), anyString(), normalizationCaptor.capture(), any());
        assertThat(normalizationCaptor.getValue().hasUnmapped()).isTrue();
    }

    @Test
    void missingRuleSetIsNonRetryable() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.empty());

        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        assertThatThrownBy(() -> service.process(validSampleJson))
                .isInstanceOf(NonRetryableEventException.class)
                .satisfies(ex -> assertThat(((NonRetryableEventException) ex).getErrorCode()).isEqualTo("RULE_SET_NOT_FOUND"));
    }

    @Test
    void validEventIsPersistedAndSetsMdc() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(javaSeniorRuleSet()));
        when(precheckOrchestrator.runAll(any())).thenReturn(List.of());

        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);
        service.process(validSampleJson);

        final ArgumentCaptor<CvParsedEvent> eventCaptor = ArgumentCaptor.forClass(CvParsedEvent.class);
        verify(decisionPersistenceService).persist(eventCaptor.capture(), any(RuleSetEntity.class),
                any(ScoreOutcome.class), eq(criteriaCatalog.version()), any(NormalizationResult.class), any());
        assertThat(eventCaptor.getValue().getCandidateId()).isEqualTo("senior-asanov-bakyt");
        assertThat(MDC.get("candidateId")).isEqualTo("senior-asanov-bakyt");
        assertThat(MDC.get("eventId")).isEqualTo("660e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void parseAndValidateThenProcessMirrorsWhatTheKafkaListenerDoes() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(javaSeniorRuleSet()));
        when(precheckOrchestrator.runAll(any())).thenReturn(List.of());

        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        // CvParsedListener больше не вызывает process(String) напрямую - сначала он разбирает
        // сырой payload в валидированный CvParsedEvent, затем обрабатывает его. Здесь
        // воспроизводится та же двухшаговая последовательность.
        final CvParsedEvent event = service.parseAndValidate(validSampleJson);
        assertThat(event.getCandidateId()).isEqualTo("senior-asanov-bakyt");

        service.process(event);

        verify(decisionPersistenceService).persist(eq(event), any(RuleSetEntity.class),
                any(ScoreOutcome.class), eq(criteriaCatalog.version()), any(NormalizationResult.class), any());
    }

    @Test
    void duplicateEventDoesNotPropagateException() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(javaSeniorRuleSet()));
        when(precheckOrchestrator.runAll(any())).thenReturn(List.of());
        when(decisionPersistenceService.persist(any(), any(), any(), anyString(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        final DecisionProcessingService service = service(UnknownKeyPolicy.AUDIT);

        // Не должно бросать исключение - дубликат это ожидаемое штатное поведение, а не ошибка обработки.
        service.process(validSampleJson);
    }
}
