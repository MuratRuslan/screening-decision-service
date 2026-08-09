package kg.tunduk.test.senior.screeningdecisionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.tunduk.test.senior.screeningdecisionservice.generated.kafka.CvParsedEvent;
import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.DecisionCreatedEvent;
import kg.tunduk.test.senior.screeningdecisionservice.model.AuditAction;
import kg.tunduk.test.senior.screeningdecisionservice.model.DecisionAuditEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.ScreeningDecisionEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.SourceVerdict;
import kg.tunduk.test.senior.screeningdecisionservice.outbox.OutboxEvent;
import kg.tunduk.test.senior.screeningdecisionservice.outbox.OutboxRepository;
import kg.tunduk.test.senior.screeningdecisionservice.precheck.PrecheckResult;
import kg.tunduk.test.senior.screeningdecisionservice.repository.DecisionAuditRepository;
import kg.tunduk.test.senior.screeningdecisionservice.repository.ScreeningDecisionRepository;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.ScoreOutcome;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.NormalizationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A separate bean (not a method on {@link DecisionProcessingService}) so its
 * {@code @Transactional} boundary goes through the Spring proxy even when called from
 * another bean in the same package - self-invocation would silently bypass the proxy and
 * the transactional/flush-then-catch idempotency mechanism would not work.
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
     * Persists the decision, its CREATED audit entry and the outbox event atomically.
     * The decision insert is flushed immediately so a duplicate
     * {@code (candidate_id, parsed_at)} surfaces as a {@code DataIntegrityViolationException}
     * here rather than at commit - the caller is expected to catch it and treat it as
     * "duplicate ignored", not an error.
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
