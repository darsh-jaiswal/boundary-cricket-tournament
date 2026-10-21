-- Practice identities have no login, email, public profile or Google registration.
CREATE TABLE boundary_practice_players (
  id text PRIMARY KEY CHECK (id ~ '^user_practice[a-f0-9]{32}$'),
  owner_id text NOT NULL REFERENCES boundary_registered_players(owner_id),
  team_id uuid NOT NULL,
  slot integer NOT NULL CHECK (slot BETWEEN 1 AND 15),
  name text NOT NULL CHECK (name LIKE '%Practice%'),
  username text NOT NULL,
  created timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id,team_id,slot)
);
