-- Merchant -> category cache (formerly categories_cache). Shared by all users.
-- Used to categorize a transaction when the form does not send a category,
-- so the same merchant is not sent to the LLM twice.

CREATE TABLE merchant_category_cache (
    merchant_name varchar(255) PRIMARY KEY,
    category      varchar(50)  NOT NULL,
    source        varchar(10)  NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_merchant_category_cache_category FOREIGN KEY (category) REFERENCES categories (name) ON UPDATE CASCADE,
    CONSTRAINT ck_merchant_category_cache_source   CHECK (source IN ('RULE', 'LLM')),
    -- Keys are stored normalized so lookups are exact matches.
    CONSTRAINT ck_merchant_category_cache_normalized CHECK (merchant_name = lower(btrim(merchant_name)) AND merchant_name <> '')
);
