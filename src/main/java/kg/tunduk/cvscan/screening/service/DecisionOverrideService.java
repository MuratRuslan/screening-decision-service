package kg.tunduk.cvscan.screening.service;

import io.micrometer.tracing.Tracer;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionOverrideRequest;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionResponse;
import kg.tunduk.cvscan.screening.exception.NotFoundException;
import kg.tunduk.cvscan.screening.exception.VersionConflictException;
import kg.tunduk.cvscan.screening.mapper.DecisionMapper;
import kg.tunduk.cvscan.screening.model.AuditAction;
import kg.tunduk.cvscan.screening.model.DecisionAuditEntity;
import kg.tunduk.cvscan.screening.model.ScreeningDecisionEntity;
import kg.tunduk.cvscan.screening.observability.Spans;
import kg.tunduk.cvscan.screening.repository.DecisionAuditRepository;
import kg.tunduk.cvscan.screening.repository.ScreeningDecisionRepository;
import kg.tunduk.cvscan.screening.scoring.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Оптимистичная конкурентность для эндпоинта override проверяется дважды: сначала явное
 * сравнение с заголовком {@code expectedVersion} (ниже - покрывает частый случай, когда
 * клиент работает с устаревшими данными, с точным сообщением по формату контракта), затем
 * колонка {@code @Version} самого JPA на {@link ScreeningDecisionEntity}, из-за которой
 * UPDATE, выпускаемый Hibernate при коммите, становится настоящим compare-and-swap
 * {@code WHERE id = ? AND version = ?}. Именно второй уровень закрывает брешь, если два
 * запроса одновременно проходят первую проверку; возникающий при этом
 * {@code ObjectOptimisticLockingFailureException} маппится в тот же 409 через
 * {@link kg.tunduk.cvscan.screening.exception.GlobalExceptionHandler}.
 */
@Service
public class DecisionOverrideService {

    private static final Logger log = LoggerFactory.getLogger(DecisionOverrideService.class);

    /** Аутентификация намеренно вне рамок задачи; это фиксирует факт вместо угадывания личности. */
    private static final String ACTOR = "api-client";

    private final ScreeningDecisionRepository decisionRepository;
    private final DecisionAuditRepository auditRepository;
    private final Tracer tracer;

    public DecisionOverrideService(final ScreeningDecisionRepository decisionRepository, final DecisionAuditRepository auditRepository,
                                    final Tracer tracer) {
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
        this.tracer = tracer;
    }

    @Transactional
    public DecisionResponse override(final UUID id, final int expectedVersion, final DecisionOverrideRequest request) {
        Spans.tag(tracer, "decisionId", String.valueOf(id));
        final ScreeningDecisionEntity decision = decisionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Решение " + id + " не найдено"));

        if (decision.getVersion() != expectedVersion) {
            throw VersionConflictException.expectedButActual(expectedVersion, decision.getVersion());
        }

        final Decision previousDecision = decision.getDecision();
        final Decision newDecision = Decision.valueOf(request.getDecision().name());
        decision.applyOverride(newDecision, request.getReason());

        // Флашим сразу (не дожидаясь коммита), чтобы Hibernate выполнил UPDATE @Version и
        // увеличил decision.version в памяти до того, как мы прочитаем его ниже для DTO
        // ответа - иначе вернулась бы версия до override, хотя в БД (и при следующем GET)
        // она уже была бы верной.
        decisionRepository.saveAndFlush(decision);

        auditRepository.save(new DecisionAuditEntity(UUID.randomUUID(), decision.getId(), AuditAction.OVERRIDDEN, ACTOR,
                overridePayload(previousDecision, request, expectedVersion), Instant.now()));

        log.info("Decision manually overridden decisionId={} candidateId={} previousDecision={} newDecision={} actor={}",
                decision.getId(), decision.getCandidateId(), previousDecision, newDecision, ACTOR);

        return DecisionMapper.toResponse(decision);
    }

    private Map<String, Object> overridePayload(final Decision previousDecision, final DecisionOverrideRequest request, final int expectedVersion) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousDecision", previousDecision.name());
        payload.put("newDecision", request.getDecision().name());
        payload.put("expectedVersion", expectedVersion);
        payload.put("reason", request.getReason());
        return payload;
    }
}
