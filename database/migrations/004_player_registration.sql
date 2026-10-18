CREATE TABLE boundary_device_accounts (
  id UUID PRIMARY KEY,
  token_hash TEXT NOT NULL UNIQUE,
  ip_hash TEXT NOT NULL,
  owner_id TEXT,
  created TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires TIMESTAMPTZ NOT NULL DEFAULT now()+interval '10 minutes',
  lookup_window TIMESTAMPTZ NOT NULL DEFAULT now(),
  lookup_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX boundary_device_accounts_ip ON boundary_device_accounts(ip_hash,created);
CREATE TABLE boundary_registered_players (
  owner_id TEXT PRIMARY KEY,
  name TEXT NOT NULL CHECK(length(name) BETWEEN 2 AND 100),
  phone_hash TEXT NOT NULL UNIQUE,
  registered TIMESTAMPTZ NOT NULL DEFAULT now()
);
