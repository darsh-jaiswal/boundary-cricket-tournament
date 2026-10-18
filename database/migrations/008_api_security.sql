CREATE TABLE boundary_rate_limits (
  scope TEXT NOT NULL,
  subject_hash TEXT NOT NULL,
  window_start BIGINT NOT NULL,
  hits INTEGER NOT NULL CHECK(hits>0),
  expires TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(scope,subject_hash,window_start)
);
CREATE INDEX boundary_rate_limits_expiry ON boundary_rate_limits(expires);

CREATE FUNCTION boundary_take_rate_limit(p_scope TEXT,p_subject TEXT,p_limit INTEGER,p_seconds INTEGER)
RETURNS TABLE(allowed BOOLEAN,remaining INTEGER,retry_after INTEGER) LANGUAGE plpgsql AS $$
DECLARE moment BIGINT := floor(extract(epoch FROM clock_timestamp())); slot BIGINT; count_hits INTEGER;
BEGIN
  IF p_limit<1 OR p_seconds<1 OR p_seconds>86400 THEN RAISE EXCEPTION 'invalid_rate_policy'; END IF;
  slot := moment / p_seconds;
  INSERT INTO boundary_rate_limits(scope,subject_hash,window_start,hits,expires)
    VALUES(p_scope,p_subject,slot,1,to_timestamp((slot+2)*p_seconds))
  ON CONFLICT(scope,subject_hash,window_start) DO UPDATE
    SET hits=LEAST(boundary_rate_limits.hits+1,p_limit+1)
  RETURNING hits INTO count_hits;
  -- Bound cleanup work; no global lock and no unbounded deletion on the request path.
  DELETE FROM boundary_rate_limits WHERE ctid IN
    (SELECT ctid FROM boundary_rate_limits WHERE expires<now() LIMIT 50);
  RETURN QUERY SELECT count_hits<=p_limit,GREATEST(0,p_limit-count_hits),
    GREATEST(1,((slot+1)*p_seconds-moment)::integer);
END;
$$;
