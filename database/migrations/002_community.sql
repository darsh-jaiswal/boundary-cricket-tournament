CREATE TABLE boundary_community_posts (
  id UUID PRIMARY KEY,
  owner_id TEXT NOT NULL CHECK (length(owner_id) BETWEEN 1 AND 200),
  author TEXT NOT NULL CHECK (length(author) BETWEEN 1 AND 100),
  category TEXT NOT NULL CHECK (category IN ('players','opponents','umpires','scorers','grounds','coaches','updates')),
  title TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  city TEXT NOT NULL CHECK (length(city) BETWEEN 1 AND 100),
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 3000),
  created TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX boundary_community_posts_created_idx ON boundary_community_posts(created DESC,id);
CREATE INDEX boundary_community_posts_owner_idx ON boundary_community_posts(owner_id,created DESC);
CREATE TABLE boundary_community_replies (
  id UUID PRIMARY KEY,
  post_id UUID NOT NULL REFERENCES boundary_community_posts(id) ON DELETE CASCADE,
  owner_id TEXT NOT NULL CHECK (length(owner_id) BETWEEN 1 AND 200),
  author TEXT NOT NULL CHECK (length(author) BETWEEN 1 AND 100),
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 1000),
  created TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX boundary_community_replies_post_idx ON boundary_community_replies(post_id,created);
