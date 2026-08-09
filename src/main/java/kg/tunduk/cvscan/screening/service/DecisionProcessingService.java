package kg.tunduk.cvscan.screening.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;
import kg.tunduk.cvscan.screening.exception.NonRetryableEventException;
import kg.tunduk.cvscan.screening.model.RuleSetEntity;
import kg.tunduk.cvscan.screening.observability.Spans;
import kg.tunduk.cvscan.screening.precheck.PrecheckOrchestrator;
import kg.tunduk.cvscan.screening.precheck.PrecheckResult;
import kg.tunduk.cvscan.screening.repository.RuleSetRepository;
import kg.tunduk.cvscan.screening.scoring.RuleSet;
import kg.tunduk.cvscan.screening.scoring.ScoreCalculator;
import kg.tunduk.cvscan.screening.scoring.ScoreOutcome;
import kg.tunduk.cvscan.screening.semantic.CriteriaCatalog;
import kg.tunduk.cvscan.screening.semantic.NormalizationResult;
import kg.tunduk.cvscan.screening.semantic.SemanticNormalizer;
import kg.tunduk.cvscan.screening.semantic.UnknownKeyPolicy;
import kg.tunduk.cvscan.screening.validation.json.CvParsedJsonSchemaValidator;
import kg.tunduk.cvscan.screening.validation.json.JsonPointerError;
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
 * Оркестрирует весь пайплайн cv.parsed -> decision: валидацию по JSON Schema, семантическую
 * нормализацию, выбор rule-set, скоринг и атомарное сохранение. Сам сознательно не
 * {@code @Transactional} - таковым является только {@link DecisionPersistenceService#persist},
 * поэтому исключение о дубликате вставки можно поймать здесь уже после отката транзакции.
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
     * Удобная точка входа для вызывающих, у которых есть только сырой payload Kafka
     * (например, тесты). {@link kg.tunduk.cvscan.screening.messaging.consumer.CvParsedListener}
     * вместо этого вызывает {@link #parseAndValidate} и {@link #process(CvParsedEvent)}
     * отдельно, чтобы залогировать/проверить типизированное событие перед обработкой.
     */
    public void process(String rawPayload) {
        process(parseAndValidate(rawPayload));
    }

    /**
     * Парсит сырой payload и валидирует его по JSON Schema - намеренно на уровне сырого
     * {@link JsonNode}, до databinding, потому что именно это даёт нарушениям схемы точный
     * JSON Pointer (например, {@code /criteria/0/key}); обычный Kafka JsonDeserializer,
     * привязанный напрямую к {@link CvParsedEvent}, полностью пропустил бы эту проверку и
     * ловил бы только структурные ошибки JSON, а не нарушения контракта вроде неверного
     * формата email или ключа критерия не по шаблону.
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
            // Не должно происходить после успешной валидации по схеме, но это подстраховка
            // от рассинхронизации схемы и DTO вместо падения потока консьюмера.
            throw new NonRetryableEventException("MALFORMED_JSON", "Не удалось разобрать событие: " + e.getOriginalMessage());
        }
    }

    private RuleSet toDomain(RuleSetEntity entity) {
        return new RuleSet(entity.getPosition(), entity.getVersion(), entity.getActiveFrom(),
                entity.getMinApproveScore(), entity.getMaxRejectScore(), entity.getWeights());
    }
}
