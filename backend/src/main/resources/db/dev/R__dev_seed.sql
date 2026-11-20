-- Demo data for local development only (loaded by the "dev" profile).
-- Repeatable migration: Flyway re-runs it when this file changes, so every
-- statement must be idempotent (ON CONFLICT DO NOTHING).
--
-- Demo logins (HTTP Basic until JWT login is implemented):
--   client1@example.local / ClientPass123!   (CLIENT)
--   admin@example.local   / AdminPass123!    (ADMIN)

INSERT INTO users (id, username, email, password_hash, role)
VALUES
    (
        '019a0000-0000-7000-8000-000000000001',
        'client1',
        'client1@example.local',
        '$argon2id$v=19$m=65536,t=3,p=4$3ukyRGtZ0bZEcqOlfZkipQ$q2+wYdOo1pubazJGmO/H1SkYiKXVRJmVU6lOsmQXynk',
        'CLIENT'
    ),
    (
        '019a0000-0000-7000-8000-000000000002',
        'admin',
        'admin@example.local',
        '$argon2id$v=19$m=65536,t=3,p=4$NYNR0YPxnuJNXzAcJy5pPw$WIELIsChcehPhl4wiiT+7UpShQy/ygonsSJhd2d2vME',
        'ADMIN'
    )
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, user_id, name, monthly_budget)
VALUES (
    '019a0000-0000-7000-8000-000000000101',
    '019a0000-0000-7000-8000-000000000001',
    'Debit card',
    5000.00
)
ON CONFLICT DO NOTHING;
