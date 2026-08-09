package kg.tunduk.test.senior.screeningdecisionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import kg.tunduk.test.senior.screeningdecisionservice.generated.kafka.CvParsedEvent;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.ErrorResponseDetailsInner;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NonRetryableEventException;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import kg.tunduk.test.senior.screeningdecisionservice.observability.Spans;
import kg.tunduk.test.senior.screeningdecisionservice.precheck.PrecheckOrchestrator;
import kg.tunduk.test.senior.screeningdecisionservice.precheck.PrecheckResult;
import kg.tunduk.test.senior.screeningdecisionservice.repository.RuleSetRepository;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.RuleSet;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.ScoreCalculator;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.ScoreOutcome;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.CriteriaCatalog;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.NormalizationResult;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.SemanticNormalizer;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.UnknownKeyPolicy;
import kg.tunduk.test.senior.screeningdecisionservice.validation.json.CvParsedJsonSchemaValidator;
import kg.tunduk.test.senior.screeningdecisionservice.validation.json.JsonPointerError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full cv.parsed -> decision pipeline: JSON Schema validation, semantic
 * normalization, rule-set selection, scoring, and atomic persistence. Deliberately not
 * itself {@code @Transactional} - only {@link DecisionPersistenceService#persist} is,
 * so the duplicate-insert exception it can throw is catchable here after the transaction
 * has already unwound.
 */
@Service
public class DecisionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DecisionProcessingService.class);

    private final CvParsedJsonSchemaValidator schemaValidator;
    private final CriteriaCatalog criteriaCatalog;
    private final RuleSetRepository ruleSetRepository;
    private final PrecheckOrchestrator precheckOrchestrator;
    private final DecisionPersistenceService decisionPersistenceService;
    private final ObjectMapper objectMapper;
    private final UnknownKeyPolicy unknownKeyPolicy;
    private final Tracer tracer;

    public DecisionProcessingService(CvParsedJsonSchemaValidator schemaValidator,
                                      CriteriaCatalog criteriaCatalog,
                                      RuleSetRepository ruleSetRepository,
                                      PrecheckOrchestrator precheckOrchestrator,
                                      DecisionPersistenceService decisionPersistenceService,
                                      ObjectMapper objectMapper,
                                      @Value("${app.semantic.unknown-key-policy:AUDIT}") UnknownKeyPolicy unknownKeyPolicy,
                                      Tracer tracer) {
        this.schemaValidator = schemaValidator;
        this.criteriaCatalog = criteriaCatalog;
        this.ruleSetRepository = ruleSetRepository;
        this.precheckOrchestrator = precheckOrchestrator;
        this.decisionPersistenceService = decisionPersistenceService;
        this.objectMapper = objectMapper;
        this.unknownKeyPolicy = unknownKeyPolicy;
        this.tracer = tracer;
    }

    /**
     * Convenience entry point for callers that only have the raw Kafka payload (e.g. tests).
     * {@link kg.tunduk.test.senior.screeningdecisionservice.messaging.consumer.CvParsedListener}
     * calls {@link #parseAndValidate} and {@link #process(CvParsedEvent)} separately instead,
     * so it can log/inspect the typed event before handing it off.
     */
    public void process(String rawPayload) {
        process(parseAndValidate(rawPayload));
    }

    /**
     * Parses the raw payload and validates it against the JSON Schema - deliberately kept on
     * the raw {@link JsonNode}, before any databinding, since that is what gives schema
     * violations a precise JSON Pointer (e.g. {@code /criteria/0/key}); a generic Kafka
     * JsonDeserializer bound straight to {@link CvParsedEvent} would skip this check entirely
     * and only catch structural JSON errors, not contract violations like a bad email format
     * or an out-of-pattern criterion key.
     */
    public CvParsedEvent parseAndValidate(String rawPayload) {
        JsonNode node = parseJson(rawPayload);
        validateAgainstSchema(node);
        return databind(node);
    }

    public void process(CvParsedEvent event) {
        MDC.put("eventId", String.valueOf(event.getEventId()));
        MDC.put("candidateId", event.getCandidateId());
        Spans.tag(tracer, "candidateId", event.getCandidateId());
        Spans.tag(tracer, "eventId", String.valueOf(event.getEventId()));
        Spans.tag(tracer, "position", event.getPosition());
        log.info("Consumed cv.parsed event candidateId={} position={} parsedAt={}",
                event.getCandidateId(), event.getPosition(), event.getParsedAt());

        NormalizationResult normalization = SemanticNormalizer.normalize(criteriaCatalog, event.getCriteria());
        if (normalization.hasUnmapped() && unknownKeyPolicy == UnknownKeyPolicy.DLQ) {
            throw new NonRetryableEventException("UNKNOWN_CRITERION_KEY",
                    "Неизвестные ключи критериев: " + normalization.unmapped());
        }

        RuleSetEntity ruleSetEntity = ruleSetRepository
                .findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(event.getPosition(), Instant.now())
                .orElseThrow(() -> new NonRetryableEventException("RULE_SET_NOT_FOUND",
                        "Активный rule-set для позиции '" + event.getPosition() + "' не найден"));

        RuleSet ruleSet = toDomain(ruleSetEntity);
        ScoreOutcome outcome = ScoreCalculator.calculate(ruleSet, normalization.byCanonicalKey());

        List<PrecheckResult> precheckResults = precheckOrchestrator.runAll(event);
        precheckResults.forEach(r -> log.info(
                "Precheck completed candidateId={} check={} status={} durationMs={} detail={}",
                event.getCandidateId(), r.name(), r.status(), r.durationMs(), r.detail()));

        try {
            UUID decisionId = decisionPersistenceService.persist(event, ruleSetEntity, outcome,
                    criteriaCatalog.version(), normalization, precheckResults);
            Spans.tag(tracer, "decisionId", String.valueOf(decisionId));
            Spans.tag(tracer, "decision", outcome.decision().name());
            log.info("Decision created candidateId={} decision={} score={} ruleSetVersion={}",
                    event.getCandidateId(), outcome.decision(), outcome.score(), ruleSetEntity.getVersion());
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event ignored candidateId={} parsedAt={}", event.getCandidateId(), event.getParsedAt());
        }
    }

    private JsonNode parseJson(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new NonRetryableEventException("MALFORMED_JSON", "Пустое сообщение");
        }
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException("MALFORMED_JSON", "Невалидный JSON: " + e.getOriginalMessage());
        }
    }

    private void validateAgainstSchema(JsonNode node) {
        List<JsonPointerError> errors = schemaValidator.validate(node);
        if (!errors.isEmpty()) {
            List<ErrorResponseDetailsInner> details = errors.stream()
                    .map(e -> new ErrorResponseDetailsInner().message(e.message()).pointer(e.pointer()))
                    .toList();
            String message = errors.stream()
                    .map(e -> e.pointer() + ": " + e.message())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Ошибка валидации JSON Schema");
            throw new NonRetryableEventException("SCHEMA_VALIDATION_ERROR", message, details);
        }
    }

    private CvParsedEvent databind(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, CvParsedEvent.class);
        } catch (JsonProcessingException e) {
            // Should not happen once schema validation has passed, but guards against a
            // schema/DTO drift bug rather than crashing the consumer thread.
            throw new NonRetryableEventException("MALFORMED_JSON", "Не удалось разобрать событие: " + e.getOriginalMessage());
        }
    }

    private RuleSet toDomain(RuleSetEntity entity) {
        return new RuleSet(entity.getPosition(), entity.getVersion(), entity.getActiveFrom(),
                entity.getMinApproveScore(), entity.getMaxRejectScore(), entity.getWeights());
    }
}
