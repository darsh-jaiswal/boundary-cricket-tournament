CREATE TABLE IF NOT EXISTS boundary_player_follows (
  follower_id TEXT NOT NULL REFERENCES boundary_registered_players(owner_id) ON DELETE CASCADE,
  followee_id TEXT NOT NULL REFERENCES boundary_registered_players(owner_id) ON DELETE CASCADE,
  created TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(follower_id,followee_id),
  CHECK(follower_id<>followee_id)
);
CREATE INDEX IF NOT EXISTS boundary_player_follows_followee_idx ON boundary_player_follows(followee_id,created DESC);
