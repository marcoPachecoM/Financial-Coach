-- Users and refresh-token state for JWT rotation and reuse detection.
-- IDs are UUIDv7 (time-ordered). The application generates them; uuidv7() is a fallback.

CREATE TABLE users (
    id            uuid         PRIMARY KEY DEFAULT uuidv7(),
    username      varchar(64)  NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role          varchar(20)  NOT NULL,
    is_active     boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'CLIENT'))
);

-- Case-insensitive uniqueness: "Client1@Example.local" and "client1@example.local" are the same user.
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email    ON users (lower(email));

COMMENT ON COLUMN users.id IS 'Public user id; used as the JWT sub claim.';
COMMENT ON COLUMN users.password_hash IS 'Argon2id hash in PHC string format.';


CREATE TABLE refresh_tokens (
    id                uuid        PRIMARY KEY DEFAULT uuidv7(),
    jti               varchar(64) NOT NULL,
    user_id           uuid        NOT NULL,
    expires_at        timestamptz NOT NULL,
    revoked_at        timestamptz,
    replaced_by_jti   varchar(64),
    reuse_detected_at timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_jti  UNIQUE (jti),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

COMMENT ON COLUMN refresh_tokens.revoked_at IS 'A token is revoked if and only if this is not null (replaces the old is_revoked flag).';
COMMENT ON COLUMN refresh_tokens.replaced_by_jti IS 'Set when the token is rotated; a revoked token with a replacement that is used again signals reuse.';
