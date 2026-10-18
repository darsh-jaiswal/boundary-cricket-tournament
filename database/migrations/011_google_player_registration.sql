-- Preserve existing records and squad IDs. Legacy players re-register with
-- Google before joining new matches; old phone hashes are no longer used.
ALTER TABLE boundary_registered_players ALTER COLUMN phone_hash DROP NOT NULL;
ALTER TABLE boundary_registered_players ADD COLUMN email_hash TEXT UNIQUE;
ALTER TABLE boundary_registered_players ADD COLUMN auth_provider TEXT;
ALTER TABLE boundary_registered_players ADD CONSTRAINT boundary_google_registration
  CHECK ((auth_provider IS NULL AND email_hash IS NULL)
    OR (auth_provider IS NOT NULL AND auth_provider = 'google'
      AND email_hash IS NOT NULL AND email_hash ~ '^[a-f0-9]{64}$'));

ALTER TABLE boundary_registered_players ALTER COLUMN terms_version SET DEFAULT '2026-10-19';
ALTER TABLE boundary_shop_orders ALTER COLUMN terms_version SET DEFAULT '2026-10-19';
