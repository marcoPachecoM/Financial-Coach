-- Recovery codes: the way to get back into an account without an email service. Eight single-use
-- codes are shown once at sign-up (and whenever the user generates new ones); "forgot password"
-- asks for one of them. Only a SHA-256 hash is stored: the codes have about 60 bits of entropy,
-- so a slow hash is not needed, and checking one is a single index lookup.

CREATE TABLE recovery_codes (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid        NOT NULL,
    code_hash  varchar(64) NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_recovery_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_recovery_codes_hash ON recovery_codes (code_hash);
CREATE INDEX ix_recovery_codes_user ON recovery_codes (user_id);

COMMENT ON COLUMN recovery_codes.code_hash IS 'SHA-256 (hex) of the normalized code; the code itself is never stored.';
COMMENT ON COLUMN recovery_codes.used_at IS 'Set when the code resets a password; a used code never works again.';
