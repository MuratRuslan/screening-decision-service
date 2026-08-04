package kg.tunduk.test.senior.screeningdecisionservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "decision_audit")
public class DecisionAuditEntity {

    @Id
    private UUID id;

    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(nullable = false)
    private String actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DecisionAuditEntity() {
        // JPA
    }

    public DecisionAuditEntity(UUID id, UUID decisionId, AuditAction action, String actor,
                                Map<String, Object> payload, Instant createdAt) {
        this.id = id;
        this.decisionId = decisionId;
        this.action = action;
        this.actor = actor;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
