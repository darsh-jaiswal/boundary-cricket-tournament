-- Player identity and team membership use owner_id, so usernames may safely change.
-- The existing unique constraint and format check remain authoritative.
DROP TRIGGER IF EXISTS boundary_username_permanent ON boundary_registered_players;
DROP FUNCTION IF EXISTS boundary_keep_player_username();
