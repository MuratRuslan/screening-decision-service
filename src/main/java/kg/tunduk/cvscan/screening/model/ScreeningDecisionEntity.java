package kg.tunduk.cvscan.screening.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kg.tunduk.cvscan.screening.scoring.Decision;
import kg.tunduk.cvscan.screening.scoring.RuleEvaluation;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "screening_decisions")
public class ScreeningDecisionEntity {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @Column(name = "parsed_at", nullable = false)
    private Instant parsedAt;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_verdict", nullable = false)
    private SourceVerdict sourceVerdict;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(nullable = false)
    private int score;

    @Column(name = "rule_set_version", nullable = false)
    private String ruleSetVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_results", nullable = false)
    private List<RuleEvaluation> ruleResults;

    @Column(name = "semantic_catalog_version", nullable = false)
    private String semanticCatalogVersion;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    /**
     * Служит одновременно токеном optimistic-concurrency контракта (с ним сравнивается
     * заголовок {@code expectedVersion} в PATCH /override) и колонкой {@code @Version} самого
     * JPA, поэтому реальный UPDATE, который выполняет Hibernate, - это настоящий
     * {@code WHERE id = ? AND version = ?} compare-and-swap - см. DecisionOverrideService.
     */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean overridden;

    @Column(name = "override_reason")
    private String overrideReason;

    protected ScreeningDecisionEntity() {
        // JPA
    }

    public ScreeningDecisionEntity(final UUID id, final String candidateId, final Instant parsedAt, final String name, final String email,
                                    final String position, final SourceVerdict sourceVerdict, final Decision decision, final int score,
                                    final String ruleSetVersion, final List<RuleEvaluation> ruleResults,
                                    final String semanticCatalogVersion, final Instant decidedAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.parsedAt = parsedAt;
        this.name = name;
        this.email = email;
        this.position = position;
        this.sourceVerdict = sourceVerdict;
        this.decision = decision;
        this.score = score;
        this.ruleSetVersion = ruleSetVersion;
        this.ruleResults = ruleResults;
        this.semanticCatalogVersion = semanticCatalogVersion;
        this.decidedAt = decidedAt;
        // Hibernate записывает значение этого поля при INSERT как начальную
        // версию строки (он не заставляет числовой @Version начинаться с 0) - 1 здесь совпадает
        // с seed-миграциями (V6/V7), которые предполагают, что неизменённые строки начинаются с версии 1.
        this.version = 1;
        this.overridden = false;
    }

    public UUID getId() {
        return id;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public Instant getParsedAt() {
        return parsedAt;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPosition() {
        return position;
    }

    public SourceVerdict getSourceVerdict() {
        return sourceVerdict;
    }

    public Decision getDecision() {
        return decision;
    }

    public int getScore() {
        return score;
    }

    public String getRuleSetVersion() {
        return ruleSetVersion;
    }

    public List<RuleEvaluation> getRuleResults() {
        return ruleResults;
    }

    public String getSemanticCatalogVersion() {
        return semanticCatalogVersion;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public int getVersion() {
        return version;
    }

    public boolean isOverridden() {
        return overridden;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void applyOverride(final Decision newDecision, final String reason) {
        this.decision = newDecision;
        this.overridden = true;
        this.overrideReason = reason;
    }
}
