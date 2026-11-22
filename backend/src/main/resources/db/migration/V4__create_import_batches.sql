-- One row per CSV upload. Used for auditing and, later, to undo a whole import.

CREATE TABLE import_batches (
    id                 uuid         PRIMARY KEY DEFAULT uuidv7(),
    user_id            uuid         NOT NULL,
    filename           varchar(255) NOT NULL,
    records_imported   integer      NOT NULL DEFAULT 0,
    records_duplicated integer      NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_import_batches_user       FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_import_batches_imported   CHECK (records_imported >= 0),
    CONSTRAINT ck_import_batches_duplicated CHECK (records_duplicated >= 0)
);

CREATE INDEX ix_import_batches_user_id ON import_batches (user_id);
