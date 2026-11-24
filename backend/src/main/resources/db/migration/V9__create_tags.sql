-- Tags: free labels a user puts on transactions ("trip-cancun", "work", "gift for mom"), many per
-- transaction. Each user has their own list, so the same name is one tag per user. The list
-- feeds autocompletion and the filter chips; text search also looks at tag names.

CREATE TABLE tags (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid        NOT NULL,
    name       varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- Stored trimmed; ";" separates tags in the CSV, so it cannot be part of a name.
    CONSTRAINT ck_tags_name CHECK (name = btrim(name) AND name <> '' AND position(';' IN name) = 0)
);

-- One tag per name and user, whatever the capitalization: "Work" and "work" are the same tag.
CREATE UNIQUE INDEX ux_tags_user_name ON tags (user_id, lower(name));

CREATE TABLE transaction_tags (
    transaction_id uuid NOT NULL,
    tag_id         uuid NOT NULL,

    PRIMARY KEY (transaction_id, tag_id),
    CONSTRAINT fk_transaction_tags_transaction FOREIGN KEY (transaction_id)
        REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

-- Filtering by tag goes from the tag to its transactions.
CREATE INDEX ix_transaction_tags_tag ON transaction_tags (tag_id);

COMMENT ON TABLE tags IS 'Labels of a user, shared by their transactions. The application checks that a transaction only gets tags of its own user.';
COMMENT ON TABLE transaction_tags IS 'Tags of each transaction (many to many).';
