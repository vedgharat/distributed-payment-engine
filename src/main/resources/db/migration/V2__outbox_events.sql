CREATE TABLE outbox_events (
                               id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                               correlation_id  UUID            NOT NULL,
                               event_type      VARCHAR(100)    NOT NULL,
                               payload         TEXT            NOT NULL,   -- JSON blob of the event
                               status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
                               created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                               published_at    TIMESTAMPTZ,               -- set when Kafka confirms receipt
                               retry_count     INT             NOT NULL DEFAULT 0,

                               CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Relay job queries this index on every poll
CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at)
    WHERE status = 'PENDING';