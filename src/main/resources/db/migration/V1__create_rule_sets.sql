CREATE TABLE rule_sets
(
    id                 UUID PRIMARY KEY,
    position           VARCHAR(64)  NOT NULL,
    version            VARCHAR(16)  NOT NULL,
    active_from        TIMESTAMPTZ  NOT NULL,
    min_approve_score  INTEGER      NOT NULL,
    max_reject_score   INTEGER      NOT NULL,
    weights            JSONB        NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_rule_sets_position_version UNIQUE (position, version)
);

CREATE INDEX idx_rule_sets_position_active_from ON rule_sets (position, active_from DESC);
