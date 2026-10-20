CREATE TABLE boundary_team_invitations (
  id UUID PRIMARY KEY,
  owner_id TEXT NOT NULL REFERENCES boundary_registered_players(owner_id),
  team_id UUID NOT NULL,
  team_name TEXT NOT NULL CHECK (char_length(team_name) BETWEEN 1 AND 100),
  token_hash TEXT NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
  expires TIMESTAMPTZ NOT NULL DEFAULT now()+interval '7 days',
  created TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX boundary_team_invitations_owner ON boundary_team_invitations(owner_id,team_id);
CREATE TABLE boundary_team_join_requests (
  owner_id TEXT NOT NULL REFERENCES boundary_registered_players(owner_id),
  team_id UUID NOT NULL,
  player_id TEXT NOT NULL REFERENCES boundary_registered_players(owner_id),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted')),
  created TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (owner_id,team_id,player_id)
);
CREATE INDEX boundary_team_join_requests_player ON boundary_team_join_requests(player_id);
CREATE INDEX boundary_team_join_requests_page ON boundary_team_join_requests(owner_id,team_id,created DESC,player_id DESC);
