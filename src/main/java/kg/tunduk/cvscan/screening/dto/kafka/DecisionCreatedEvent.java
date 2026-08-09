package kg.tunduk.cvscan.screening.dto.kafka;

import kg.tunduk.cvscan.screening.scoring.Decision;

import java.time.Instant;
import java.util.UUID;

/** Published to {@code screening.decision.created} after a decision is durably persisted. */
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
