package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import kg.tunduk.test.senior.screeningdecisionservice.model.AuditAction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        UUID decisionId,
        AuditAction action,
        String actor,
        Map<String, Object> payload,
        Instant createdAt
) {
}
