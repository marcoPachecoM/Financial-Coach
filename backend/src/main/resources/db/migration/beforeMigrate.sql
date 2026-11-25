-- Flyway callback: runs before every migrate (it is not a migration and is not in the history).
--
-- The migrations use uuidv7(), built into PostgreSQL 18. Production (Supabase) runs PostgreSQL 17,
-- so there an equivalent public.uuidv7() is created: a random UUID whose first 48 bits are the
-- Unix time in milliseconds, with version 7 (0111) and the RFC 9562 variant (10xx, already set by
-- gen_random_uuid()), so values still sort by creation time.
--
-- On PostgreSQL 18 nothing is created and the built-in function is used.
--
-- Never dropped here: column defaults created on 17 reference this function by OID, so they keep
-- using it after an upgrade to 18 (see "After upgrading PostgreSQL" in docs/deploy.md).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_proc
                   WHERE proname = 'uuidv7' AND pronamespace = 'pg_catalog'::regnamespace) THEN
        CREATE OR REPLACE FUNCTION public.uuidv7() RETURNS uuid
            LANGUAGE sql VOLATILE PARALLEL SAFE AS
        $f$
            SELECT encode(
                set_bit(set_bit(
                    overlay(uuid_send(gen_random_uuid())
                            PLACING substring(int8send(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3)
                            FROM 1 FOR 6),
                    52, 1), 53, 1),
                'hex')::uuid
        $f$;
    END IF;
END
$$;
