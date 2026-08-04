CREATE TABLE outbox_events
(
    id              UUID          PRIMARY KEY,
    aggregate_type  VARCHAR(32)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    topic           VARCHAR(128)  NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'NEW',
    retry_count     INTEGER       NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,

    CONSTRAINT chk_outbox_events_status CHECK (status IN ('NEW', 'SENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_status_created_at ON outbox_events (status, created_at);
