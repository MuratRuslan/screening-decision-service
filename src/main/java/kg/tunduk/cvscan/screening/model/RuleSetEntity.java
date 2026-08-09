package kg.tunduk.cvscan.screening.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.tunduk.cvscan.screening.scoring.CriterionWeight;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rule_sets")
public class RuleSetEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String position;

    @Column(nullable = false)
    private String version;

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom;

    @Column(name = "min_approve_score", nullable = false)
    private int minApproveScore;

    @Column(name = "max_reject_score", nullable = false)
    private int maxRejectScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<CriterionWeight> weights;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RuleSetEntity() {
        // JPA
    }

    public RuleSetEntity(UUID id, String position, String version, Instant activeFrom,
                          int minApproveScore, int maxRejectScore, List<CriterionWeight> weights, Instant createdAt) {
        this.id = id;
        this.position = position;
        this.version = version;
        this.activeFrom = activeFrom;
        this.minApproveScore = minApproveScore;
        this.maxRejectScore = maxRejectScore;
        this.weights = weights;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getPosition() {
        return position;
    }

    public String getVersion() {
        return version;
    }

    public Instant getActiveFrom() {
        return activeFrom;
    }

    public int getMinApproveScore() {
        return minApproveScore;
    }

    public int getMaxRejectScore() {
        return maxRejectScore;
    }

    public List<CriterionWeight> getWeights() {
        return weights;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
