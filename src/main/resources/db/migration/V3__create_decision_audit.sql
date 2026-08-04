CREATE TABLE decision_audit
(
    id           UUID PRIMARY KEY,
    decision_id  UUID         NOT NULL REFERENCES screening_decisions (id),
    action       VARCHAR(24)  NOT NULL,
    actor        VARCHAR(64)  NOT NULL,
    payload      JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_decision_audit_decision_id_created_at
    ON decision_audit (decision_id, created_at);
