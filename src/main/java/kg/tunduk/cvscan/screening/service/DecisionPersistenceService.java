package kg.tunduk.cvscan.screening.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import kg.tunduk.cvscan.screening.dto.kafka.DecisionCreatedEvent;
import kg.tunduk.cvscan.screening.model.AuditAction;
import kg.tunduk.cvscan.screening.model.DecisionAuditEntity;
import kg.tunduk.cvscan.screening.model.RuleSetEntity;
import kg.tunduk.cvscan.screening.model.ScreeningDecisionEntity;
import kg.tunduk.cvscan.screening.model.SourceVerdict;
import kg.tunduk.cvscan.screening.outbox.OutboxEvent;
import kg.tunduk.cvscan.screening.outbox.OutboxRepository;
import kg.tunduk.cvscan.screening.precheck.PrecheckResult;
import kg.tunduk.cvscan.screening.repository.DecisionAuditRepository;
import kg.tunduk.cvscan.screening.repository.ScreeningDecisionRepository;
import kg.tunduk.cvscan.screening.scoring.ScoreOutcome;
import kg.tunduk.cvscan.screening.semantic.NormalizationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Отдельный бин (не метод в {@link DecisionProcessingService}), чтобы его граница
 * {@code @Transactional} проходила через прокси Spring даже при вызове из другого бина
 * того же пакета - самовызов молча обошёл бы прокси, и механизм идемпотентности
 * flush-then-catch перестал бы работать.
 */
@Service
public class DecisionPersistenceService {

    private static final String ACTOR = "screening-decision-service";
    private static final String AGGREGATE_TYPE = "SCREENING_DECISION";

    private final ScreeningDecisionRepository decisionRepository;
    private final DecisionAuditRepository auditRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String decisionCreatedTopic;

    public DecisionPersistenceService(ScreeningDecisionRepository decisionRepository,
                                       DecisionAuditRepository auditRepository,
                                       OutboxRepository outboxRepository,
                                       ObjectMapper objectMapper,
                                       @Value("${app.kafka.topics.decision-created}") String decisionCreatedTopic) {
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.decisionCreatedTopic = decisionCreatedTopic;
    }

    /**
     * Атомарно сохраняет решение, его запись аудита CREATED и событие outbox.
     * Вставка решения флашится немедленно, чтобы дубликат
     * {@code (candidate_id, parsed_at)} проявился как {@code DataIntegrityViolationException}
     * здесь, а не при коммите - вызывающий код должен поймать это исключение и
     * трактовать как "дубликат проигнорирован", а не как ошибку.
     */
    @Transactional
    public UUID persist(CvParsedEvent event, RuleSetEntity ruleSet, ScoreOutcome outcome,
                         String semanticCatalogVersion, NormalizationResult normalization,
                         List<PrecheckResult> precheckResults) {
        Instant decidedAt = Instant.now();

        ScreeningDecisionEntity decision = new ScreeningDecisionEntity(
                UUID.randomUUID(),
                event.getCandidateId(),
                event.getParsedAt(),
                event.getName(),
                event.getEmail(),
                event.getPosition(),
                SourceVerdict.valueOf(event.getVerdict().name()),
                outcome.decision(),
                outcome.score(),
                ruleSet.getVersion(),
                outcome.ruleResults(),
                semanticCatalogVersion,
                decidedAt);

        decisionRepository.save(decision);
        decisionRepository.flush();

        auditRepository.save(new DecisionAuditEntity(
                UUID.randomUUID(), decision.getId(), AuditAction.CREATED, ACTOR,
                auditPayload(decision, normalization, precheckResults), decidedAt));

        outboxRepository.save(OutboxEvent.newEvent(
                decision.getId(), AGGREGATE_TYPE, decisionCreatedTopic, serializeDecisionCreated(decision)));

        return decision.getId();
    }

    private Map<String, Object> auditPayload(ScreeningDecisionEntity decision, NormalizationResult normalization,
                                              List<PrecheckResult> precheckResults) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("score", decision.getScore());
        payload.put("decision", decision.getDecision().name());
        payload.put("ruleSetVersion", decision.getRuleSetVersion());
        payload.put("semanticCatalogVersion", decision.getSemanticCatalogVersion());
        if (normalization.hasUnmapped()) {
            payload.put("unmappedCriteria", normalization.unmapped());
        }
        payload.put("checks", precheckResults);
        return payload;
    }

    private String serializeDecisionCreated(ScreeningDecisionEntity decision) {
        DecisionCreatedEvent event = new DecisionCreatedEvent(
                UUID.randomUUID(),
                decision.getId(),
                decision.getCandidateId(),
                decision.getPosition(),
                decision.getDecision(),
                decision.getScore(),
                decision.getRuleSetVersion(),
                decision.getDecidedAt());
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DecisionCreatedEvent", e);
        }
    }
}
