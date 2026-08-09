package kg.tunduk.cvscan.screening.dto.kafka;

import kg.tunduk.cvscan.screening.scoring.Decision;

import java.time.Instant;
import java.util.UUID;

/** Публикуется в {@code screening.decision.created} после того, как решение сохранено. */
public record DecisionCreatedEvent(
        UUID eventId,
        UUID decisionId,
        String candidateId,
        String position,
        Decision decision,
        int score,
        String ruleSetVersion,
        Instant decidedAt
) {
}
