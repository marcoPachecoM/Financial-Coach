-- Savings goals of a user ("Trip to Japan: 40,000 by June 2027") and the money put into them.
-- Goals belong to the user, not to an account: savings usually span accounts. Progress comes
-- only from the contributions the user records (the app does not know account balances); the
-- projection is computed on every request, nothing derived is stored.

CREATE TABLE savings_goals (
    id            uuid           PRIMARY KEY DEFAULT uuidv7(),
    user_id       uuid           NOT NULL,
    name          varchar(100)   NOT NULL,
    target_amount numeric(14, 2) NOT NULL,
    target_date   date,
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_savings_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_savings_goals_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_savings_goals_target_amount CHECK (target_amount > 0)
);

-- Names are unique per user, case-insensitive; user_id leads, so it also serves the foreign key.
CREATE UNIQUE INDEX ux_savings_goals_user_name ON savings_goals (user_id, lower(name));

CREATE TABLE goal_contributions (
    id             uuid           PRIMARY KEY DEFAULT uuidv7(),
    goal_id        uuid           NOT NULL,
    amount         numeric(14, 2) NOT NULL,
    contributed_on date           NOT NULL,
    note           varchar(200),
    created_at     timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_goal_contributions_goal FOREIGN KEY (goal_id) REFERENCES savings_goals (id) ON DELETE CASCADE,
    -- Positive puts money in, negative takes it out (a withdrawal); zero means nothing.
    CONSTRAINT ck_goal_contributions_amount CHECK (amount <> 0)
);

-- The history and the pace read a goal's contributions by date.
CREATE INDEX ix_goal_contributions_goal_date ON goal_contributions (goal_id, contributed_on);

COMMENT ON COLUMN savings_goals.target_date IS 'Optional deadline. NULL: the goal has no date, only a projection.';
COMMENT ON TABLE goal_contributions IS 'Money the user put into (or took out of) a goal.';
