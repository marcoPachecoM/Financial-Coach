-- Transactions, persisted at creation/import time (the Flask backend re-read CSV files on every request).

CREATE TABLE transactions (
    id              uuid           PRIMARY KEY DEFAULT uuidv7(),
    user_id         uuid           NOT NULL,
    account_id      uuid           NOT NULL,
    type            varchar(10)    NOT NULL,
    datetime        timestamp      NOT NULL,
    name            varchar(255)   NOT NULL,
    amount          numeric(14, 2) NOT NULL,
    category        varchar(50)    NOT NULL,
    category_group  varchar(100),
    notes           varchar(1000),
    source          varchar(20)    NOT NULL,
    category_source varchar(20)    NOT NULL,
    import_batch_id uuid,
    fingerprint     char(64),
    created_at      timestamptz    NOT NULL DEFAULT now(),
    updated_at      timestamptz    NOT NULL DEFAULT now(),
    deleted_at      timestamptz,

    -- Composite key: the transaction's user must be the owner of its account.
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id, user_id)
        REFERENCES accounts (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_category FOREIGN KEY (category)
        REFERENCES categories (name) ON UPDATE CASCADE,
    CONSTRAINT fk_transactions_import_batch FOREIGN KEY (import_batch_id) 
        REFERENCES import_batches (id) ON DELETE SET NULL,
    CONSTRAINT ck_transactions_type CHECK (type IN ('EXPENSE', 'INCOME')),
    -- Spending perspective: expenses are positive, income is negative. Zero is not allowed.
    CONSTRAINT ck_transactions_amount_sign     CHECK ((type = 'EXPENSE' AND amount > 0) OR (type = 'INCOME' AND amount < 0)),
    CONSTRAINT ck_transactions_name_not_blank  CHECK (btrim(name) <> ''),
    CONSTRAINT ck_transactions_source          CHECK (source IN ('MANUAL', 'CSV')),
    CONSTRAINT ck_transactions_category_source CHECK (category_source IN ('USER', 'CSV', 'RULE', 'LLM')),
    -- Only CSV rows carry a fingerprint, and every CSV row must have one (needed for de-duplication).
    CONSTRAINT ck_transactions_fingerprint     CHECK ((source = 'CSV') = (fingerprint IS NOT NULL))
);

-- Listing, analytics and budgets: always by account and date range, excluding deleted rows.
CREATE INDEX ix_transactions_account_datetime
    ON transactions (account_id, datetime DESC)
    WHERE deleted_at IS NULL;

-- CSV de-duplication. Deleted rows are included on purpose: re-uploading a file
-- does not bring back transactions the user deleted.
CREATE UNIQUE INDEX ux_transactions_account_fingerprint
    ON transactions (account_id, fingerprint)
    WHERE fingerprint IS NOT NULL;

-- Foreign-key support (cascades and lookups). PostgreSQL does not index foreign keys automatically.
CREATE INDEX ix_transactions_account_user ON transactions (account_id, user_id);
CREATE INDEX ix_transactions_user_id      ON transactions (user_id);
CREATE INDEX ix_transactions_import_batch ON transactions (import_batch_id) WHERE import_batch_id IS NOT NULL;

COMMENT ON COLUMN transactions.datetime IS 'Local date-time of the transaction (receipt time), without timezone.';
COMMENT ON COLUMN transactions.amount IS 'Signed by type: EXPENSE > 0, INCOME < 0.';
COMMENT ON COLUMN transactions.source IS 'Channel that created the row (internal, not exposed by the API).';
COMMENT ON COLUMN transactions.category_source IS 'Who assigned the category: USER (form/edit), CSV, RULE or LLM.';
COMMENT ON COLUMN transactions.fingerprint IS 'SHA-256 hex of (account, datetime, name, amount, occurrence in file). CSV rows only.';
COMMENT ON COLUMN transactions.deleted_at IS 'Soft delete. NULL means active.';
