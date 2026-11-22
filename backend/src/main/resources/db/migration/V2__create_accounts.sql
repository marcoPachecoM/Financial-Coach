-- Accounts (formerly "connections"): money containers of a user, with an optional monthly budget.

CREATE TABLE accounts (
    id             uuid          PRIMARY KEY DEFAULT uuidv7(),
    user_id        uuid          NOT NULL,
    name           varchar(100)  NOT NULL,
    monthly_budget numeric(14, 2),
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_accounts_user              FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_accounts_name_not_blank    CHECK (btrim(name) <> ''),
    CONSTRAINT ck_accounts_monthly_budget    CHECK (monthly_budget >= 0),
    -- Target of the composite foreign key in transactions (account_id, user_id).
    CONSTRAINT uq_accounts_id_user           UNIQUE (id, user_id)
);

-- Names are unique per user, case-insensitive. CSV rows are matched to accounts by name.
-- user_id is the leading column, so this index also serves the foreign key.
CREATE UNIQUE INDEX ux_accounts_user_name ON accounts (user_id, lower(name));

COMMENT ON COLUMN accounts.monthly_budget IS 'Spending limit per calendar month. NULL means no budget.';
