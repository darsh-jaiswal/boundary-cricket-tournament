-- Existing handles stay unchanged. A legacy NULL handle may be selected once.
CREATE FUNCTION boundary_keep_player_username() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.username IS NOT NULL AND NEW.username IS DISTINCT FROM OLD.username THEN
    RAISE EXCEPTION 'Your username is permanent and cannot be changed'
      USING ERRCODE='23514', CONSTRAINT='boundary_username_permanent';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER boundary_username_permanent
  BEFORE UPDATE OF username ON boundary_registered_players
  FOR EACH ROW EXECUTE FUNCTION boundary_keep_player_username();
