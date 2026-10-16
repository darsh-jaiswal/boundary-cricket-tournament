CREATE TABLE IF NOT EXISTS cricket_matches (
    id TEXT PRIMARY KEY,
    owner_hash TEXT NOT NULL,
    revision INTEGER NOT NULL,
    data JSONB NOT NULL,
    updated TIMESTAMPTZ NOT NULL DEFAULT now(),
    private_journal TEXT
);

-- Preserve matches created before versioned migrations were introduced.
ALTER TABLE cricket_matches ADD COLUMN IF NOT EXISTS private_journal TEXT;
ALTER TABLE cricket_matches ALTER COLUMN updated SET NOT NULL;

CREATE INDEX IF NOT EXISTS cricket_matches_updated_id_idx
    ON cricket_matches (updated DESC, id DESC);

DO $migration$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'cricket_matches_valid_snapshot' AND conrelid = 'cricket_matches'::regclass) THEN
        ALTER TABLE cricket_matches ADD CONSTRAINT cricket_matches_valid_snapshot CHECK (
            revision >= 0
            AND owner_hash ~ '^[a-f0-9]{64}$'
            AND jsonb_typeof(data) = 'object'
            AND data ? 'id'
            AND data->>'id' = id
            AND NOT (data ? 'writeKey')
            AND NOT (data ? 'privateJournal')
        );
    END IF;
END
$migration$;
