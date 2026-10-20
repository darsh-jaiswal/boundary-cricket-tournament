ALTER TABLE boundary_device_accounts ADD COLUMN IF NOT EXISTS login_version integer NOT NULL DEFAULT 1;
CREATE TABLE IF NOT EXISTS boundary_app_signin_codes (
  device_id uuid PRIMARY KEY REFERENCES boundary_device_accounts(id) ON DELETE CASCADE,
  owner_id text NOT NULL REFERENCES boundary_registered_players(owner_id),
  code_hash text NOT NULL,
  expires timestamptz NOT NULL DEFAULT now() + interval '2 minutes'
);
