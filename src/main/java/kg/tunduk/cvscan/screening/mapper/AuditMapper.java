package kg.tunduk.cvscan.screening.mapper;

import kg.tunduk.cvscan.screening.generated.rest.model.AuditAction;
import kg.tunduk.cvscan.screening.generated.rest.model.AuditEntry;
import kg.tunduk.cvscan.screening.model.DecisionAuditEntity;

import java.time.ZoneOffset;

public final class AuditMapper {

    private AuditMapper() {
    }

    public static AuditEntry toResponse(final DecisionAuditEntity entity) {
        final AuditEntry response = new AuditEntry(
                entity.getId(),
                entity.getDecisionId(),
                AuditAction.valueOf(entity.getAction().name()),
                entity.getActor(),
                entity.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
        response.payload(entity.getPayload());
        return response;
    }
}
