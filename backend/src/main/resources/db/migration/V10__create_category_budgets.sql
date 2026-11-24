-- Monthly budgets per category group of an account ("Food: 5,000 a month"). Opt-in: only the
-- groups the user wants to watch have a row. Like accounts.monthly_budget, the amount applies to
-- every calendar month; the spending is computed from the transactions, nothing else is stored.

CREATE TABLE category_budgets (
    id             uuid          PRIMARY KEY DEFAULT uuidv7(),
    account_id     uuid          NOT NULL,
    category_group varchar(50)   NOT NULL,
    monthly_amount numeric(14, 2) NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_category_budgets_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_category_budgets_category_group FOREIGN KEY (category_group)
        REFERENCES category_groups (name) ON UPDATE CASCADE,
    CONSTRAINT ck_category_budgets_amount CHECK (monthly_amount >= 0)
);

-- One budget per group and account; account_id leads, so it also serves the foreign key.
CREATE UNIQUE INDEX ux_category_budgets_account_group ON category_budgets (account_id, category_group);

COMMENT ON TABLE category_budgets IS 'Spending limit per calendar month of one category group of an account.';
