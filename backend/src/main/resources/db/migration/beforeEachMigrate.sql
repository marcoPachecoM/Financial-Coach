-- Flyway callback: runs before each migration script (it is not a migration and is not in the history).
--
-- PostgreSQL 18 records NOT NULL as named constraints (<table>_<column>_not_null), and
-- V7__category_group_is_the_closed_list.sql renames four of them. PostgreSQL 17 (production, on
-- Supabase) has no such constraints, so V7 would fail there. Before V7 runs on 17, this creates
-- them as equivalent CHECK (column IS NOT NULL) constraints with the same names; V7 then renames
-- them exactly as on 18. Already-applied migrations are never edited (Flyway checks their checksum).
--
-- It only acts on PostgreSQL < 18, while the pre-V7 tables and columns exist; afterwards it does nothing.
DO $$
DECLARE
    target record;
BEGIN
    IF current_setting('server_version_num')::int >= 180000 THEN
        RETURN;
    END IF;
    FOR target IN
        SELECT * FROM (VALUES
            ('categories', 'name'),
            ('transactions', 'category'),
            ('transactions', 'category_source'),
            ('merchant_category_cache', 'category')
        ) AS t (table_name, column_name)
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = target.table_name
                     AND column_name = target.column_name AND is_nullable = 'NO')
           AND NOT EXISTS (SELECT 1 FROM pg_constraint
                           WHERE conrelid = to_regclass(format('public.%I', target.table_name))
                             AND conname = format('%s_%s_not_null', target.table_name, target.column_name)) THEN
            EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I CHECK (%I IS NOT NULL)',
                           target.table_name,
                           format('%s_%s_not_null', target.table_name, target.column_name),
                           target.column_name);
        END IF;
    END LOOP;
END
$$;
