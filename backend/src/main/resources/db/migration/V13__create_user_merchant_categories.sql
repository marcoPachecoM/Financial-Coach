-- The category group a user chose for a merchant: their corrections, learned without tokens and
-- used before the shared cache and the LLM. Per user on purpose: if corrections went into the
-- shared merchant_category_cache, one user could change everyone's categorization.

CREATE TABLE user_merchant_categories (
    user_id        uuid         NOT NULL,
    merchant_name  varchar(255) NOT NULL,
    category_group varchar(50)  NOT NULL,
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, merchant_name),
    CONSTRAINT fk_user_merchant_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_merchant_categories_category_group FOREIGN KEY (category_group)
        REFERENCES category_groups (name) ON UPDATE CASCADE,
    -- Same normalized keys as merchant_category_cache (MerchantNames.normalize).
    CONSTRAINT ck_user_merchant_categories_normalized
        CHECK (merchant_name = lower(btrim(merchant_name)) AND merchant_name <> '')
);

COMMENT ON TABLE user_merchant_categories IS 'Category group a user chose for a merchant; applied to their future transactions without a group.';
