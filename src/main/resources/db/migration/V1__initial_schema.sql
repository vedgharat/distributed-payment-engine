-- ============================================================
-- WALLET TABLE
-- The central account entity. Balances are updated atomically
-- only via the double-entry transaction process.
-- ============================================================
CREATE TABLE wallets (
                         id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                         owner_name      VARCHAR(255)    NOT NULL,
                         balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
                         currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
                         created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                         updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                         version         BIGINT          NOT NULL DEFAULT 0,  -- Optimistic lock version

                         CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- ============================================================
-- TRANSACTION TABLE (IMMUTABLE LEDGER)
-- Each money movement creates TWO records:
--   1. DEBIT  entry for the sender
--   2. CREDIT entry for the receiver
-- Records are NEVER updated. This is the audit trail.
-- ============================================================
CREATE TABLE transactions (
                              id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Correlation ID ties the debit and credit entries together
                              correlation_id      UUID            NOT NULL,
                              wallet_id           UUID            NOT NULL REFERENCES wallets(id),
    -- Amount is always positive; type (DEBIT/CREDIT) encodes direction
                              amount              NUMERIC(19, 4)  NOT NULL,
                              transaction_type    VARCHAR(10)     NOT NULL,   -- 'DEBIT' or 'CREDIT'
                              status              VARCHAR(20)     NOT NULL,   -- 'COMPLETED', 'FAILED'
                              description         VARCHAR(500),
                              idempotency_key     VARCHAR(255)    NOT NULL,
                              created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                              CONSTRAINT chk_amount_positive CHECK (amount > 0),
                              CONSTRAINT chk_transaction_type CHECK (transaction_type IN ('DEBIT', 'CREDIT')),
                              CONSTRAINT chk_status CHECK (status IN ('COMPLETED', 'FAILED', 'PENDING'))
);

-- Indexes for common query patterns
CREATE INDEX idx_transactions_wallet_id     ON transactions(wallet_id);
CREATE INDEX idx_transactions_correlation   ON transactions(correlation_id);
CREATE INDEX idx_transactions_idempotency   ON transactions(idempotency_key);
CREATE INDEX idx_wallets_currency           ON wallets(currency);

-- Auto-update updated_at on wallet changes
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallets_updated_at
    BEFORE UPDATE ON wallets
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();