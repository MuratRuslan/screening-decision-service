package kg.tunduk.test.senior.screeningdecisionservice.mapper;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.AuditEntryResponse;
import kg.tunduk.test.senior.screeningdecisionservice.model.DecisionAuditEntity;

public final class AuditMapper {

    private AuditMapper() {
    }

    public static AuditEntryResponse toResponse(DecisionAuditEntity entity) {
        return new AuditEntryResponse(
                entity.getId(),
                entity.getDecisionId(),
                entity.getAction(),
                entity.getActor(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }
}
