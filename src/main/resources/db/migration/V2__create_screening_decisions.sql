CREATE TABLE screening_decisions
(
    id                        UUID PRIMARY KEY,
    candidate_id              VARCHAR(120)  NOT NULL,
    parsed_at                 TIMESTAMPTZ   NOT NULL,
    name                      VARCHAR(150)  NOT NULL,
    email                     VARCHAR(255)  NOT NULL,
    position                  VARCHAR(64)   NOT NULL,
    source_verdict            VARCHAR(16)   NOT NULL,
    decision                  VARCHAR(16)   NOT NULL,
    score                     INTEGER       NOT NULL,
    rule_set_version          VARCHAR(16)   NOT NULL,
    rule_results              JSONB         NOT NULL,
    semantic_catalog_version  VARCHAR(16)   NOT NULL,
    decided_at                TIMESTAMPTZ   NOT NULL,
    version                   INTEGER       NOT NULL DEFAULT 1,
    overridden                BOOLEAN       NOT NULL DEFAULT FALSE,
    override_reason           VARCHAR(1000),

    CONSTRAINT uq_screening_decisions_candidate_parsed UNIQUE (candidate_id, parsed_at),
    CONSTRAINT chk_screening_decisions_score CHECK (score BETWEEN 0 AND 100)
);

CREATE INDEX idx_decisions_position_decision_decided_at
    ON screening_decisions (position, decision, decided_at DESC);

CREATE INDEX idx_decisions_score ON screening_decisions (score);
