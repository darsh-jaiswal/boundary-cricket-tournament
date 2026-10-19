-- Existing players choose a handle on their next registration; stable account IDs remain intact.
ALTER TABLE boundary_registered_players ADD COLUMN username TEXT UNIQUE;
ALTER TABLE boundary_registered_players ADD CONSTRAINT boundary_player_username
  CHECK (username IS NULL OR (username ~ '^[a-z][a-z0-9_]{2,23}$' AND username !~ '^user_'));
