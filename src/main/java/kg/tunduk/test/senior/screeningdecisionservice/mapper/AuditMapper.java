package kg.tunduk.test.senior.screeningdecisionservice.mapper;

import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.AuditAction;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.AuditEntry;
import kg.tunduk.test.senior.screeningdecisionservice.model.DecisionAuditEntity;

import java.time.ZoneOffset;

public final class AuditMapper {

    private AuditMapper() {
    }

    public static AuditEntry toResponse(DecisionAuditEntity entity) {
        AuditEntry response = new AuditEntry(
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
