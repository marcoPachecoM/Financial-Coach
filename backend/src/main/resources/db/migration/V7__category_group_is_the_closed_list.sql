-- The closed list (26 values) classifies the CATEGORY GROUP; the category is free text,
-- e.g. group "Utilities", category "Gas". V3/V5/V6 had it the other way round.
--
-- Existing rows keep their meaning: the old "category" column already held values of the
-- list (they become the group) and the old "category_group" held free text (it becomes the
-- category). Only renames: no data is copied or rewritten.

ALTER TABLE categories RENAME TO category_groups;
ALTER TABLE category_groups RENAME CONSTRAINT categories_pkey TO category_groups_pkey;
-- PostgreSQL 18 names NOT NULL constraints too; they follow the column, so rename them as well.
ALTER TABLE category_groups RENAME CONSTRAINT categories_name_not_null TO category_groups_name_not_null;

-- Swap the two columns of transactions (a temporary name avoids the clash).
ALTER TABLE transactions RENAME COLUMN category TO category_group_swap;
ALTER TABLE transactions RENAME COLUMN category_group TO category;
ALTER TABLE transactions RENAME COLUMN category_group_swap TO category_group;
ALTER TABLE transactions RENAME CONSTRAINT fk_transactions_category TO fk_transactions_category_group;
ALTER TABLE transactions RENAME CONSTRAINT transactions_category_not_null TO transactions_category_group_not_null;

ALTER TABLE transactions RENAME COLUMN category_source TO category_group_source;
ALTER TABLE transactions RENAME CONSTRAINT ck_transactions_category_source TO ck_transactions_category_group_source;
ALTER TABLE transactions
    RENAME CONSTRAINT transactions_category_source_not_null TO transactions_category_group_source_not_null;

ALTER TABLE merchant_category_cache RENAME COLUMN category TO category_group;
ALTER TABLE merchant_category_cache
    RENAME CONSTRAINT fk_merchant_category_cache_category TO fk_merchant_category_cache_category_group;
ALTER TABLE merchant_category_cache
    RENAME CONSTRAINT merchant_category_cache_category_not_null TO merchant_category_cache_category_group_not_null;

COMMENT ON TABLE category_groups IS 'Allowed category groups (CategoryGroup enum of the API contract).';
COMMENT ON COLUMN transactions.category_group IS 'One of category_groups; assigned automatically when not given.';
COMMENT ON COLUMN transactions.category IS 'Free text, e.g. "Gas" inside the "Utilities" group. Optional.';
COMMENT ON COLUMN transactions.category_group_source IS 'Who assigned the category group: USER (form/edit), CSV, RULE or LLM.';
COMMENT ON COLUMN merchant_category_cache.category_group IS 'Category group assigned to the normalized merchant name.';
