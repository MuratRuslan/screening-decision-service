package kg.tunduk.test.senior.screeningdecisionservice.service;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionOverrideRequest;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionResponse;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NotFoundException;
import kg.tunduk.test.senior.screeningdecisionservice.exception.VersionConflictException;
import kg.tunduk.test.senior.screeningdecisionservice.mapper.DecisionMapper;
import kg.tunduk.test.senior.screeningdecisionservice.model.AuditAction;
import kg.tunduk.test.senior.screeningdecisionservice.model.DecisionAuditEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.ScreeningDecisionEntity;
import kg.tunduk.test.senior.screeningdecisionservice.repository.DecisionAuditRepository;
import kg.tunduk.test.senior.screeningdecisionservice.repository.ScreeningDecisionRepository;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optimistic concurrency for the override endpoint is enforced twice: first an explicit
 * comparison against the {@code expectedVersion} header (below - catches the common case,
 * a client working from a stale read, with the exact contract-format message), then JPA's
 * own {@code @Version} column on {@link ScreeningDecisionEntity}, which makes the UPDATE
 * Hibernate issues at commit a real {@code WHERE id = ? AND version = ?} compare-and-swap.
 * That second layer is what actually closes the gap if two requests both pass the first
 * check concurrently; a resulting {@code ObjectOptimisticLockingFailureException} is mapped
 * to the same 409 by {@link kg.tunduk.test.senior.screeningdecisionservice.exception.GlobalExceptionHandler}.
 */
@Service
public class DecisionOverrideService {

    /** Auth is explicitly out of scope for this task; this documents that instead of guessing an identity. */
    private static final String ACTOR = "api-client";

    private final ScreeningDecisionRepository decisionRepository;
    private final DecisionAuditRepository auditRepository;

    public DecisionOverrideService(ScreeningDecisionRepository decisionRepository, DecisionAuditRepository auditRepository) {
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public DecisionResponse override(UUID id, int expectedVersion, DecisionOverrideRequest request) {
        ScreeningDecisionEntity decision = decisionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Решение " + id + " не найдено"));

        if (decision.getVersion() != expectedVersion) {
            throw VersionConflictException.expectedButActual(expectedVersion, decision.getVersion());
        }

        Decision previousDecision = decision.getDecision();
        decision.applyOverride(request.decision(), request.reason());

        auditRepository.save(new DecisionAuditEntity(UUID.randomUUID(), decision.getId(), AuditAction.OVERRIDDEN, ACTOR,
                overridePayload(previousDecision, request, expectedVersion), Instant.now()));

        return DecisionMapper.toResponse(decision);
    }

    private Map<String, Object> overridePayload(Decision previousDecision, DecisionOverrideRequest request, int expectedVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousDecision", previousDecision.name());
        payload.put("newDecision", request.decision().name());
        payload.put("expectedVersion", expectedVersion);
        payload.put("reason", request.reason());
        return payload;
    }
}
