-- Replace the generic collection without deleting past orders or reservations.
-- The migration ledger runs this once. Move only unreserved stock; pending
-- orders keep their original variant IDs and immutable item snapshots.
DO $$ BEGIN PERFORM pg_advisory_xact_lock(6820431002); END $$;
LOCK TABLE boundary_products, boundary_product_variants IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO boundary_products(id,name,category,description) VALUES
('black-full-sleeve','Black Full-Sleeve Jersey','full-sleeve','Black long-sleeve jersey shown in the product photo. Trousers and shoes are not included.'),
('blue-jersey','Blue Jersey','tshirt','Blue short-sleeve jersey shown in the product photo. Trousers are not included.'),
('white-jersey','White Jersey','tshirt','White short-sleeve jersey shown in the product photo. Trousers are not included.'),
('maroon-cap','Maroon Cap','cap','Maroon adjustable cap shown in the product photo. Jersey is not included.');

INSERT INTO boundary_product_variants(id,product_id,material,size,colour,price_paise,stock,enabled)
SELECT p.id||'-'||lower(s.size),p.id,'Standard',s.size,p.colour,p.price,v.stock,v.enabled
FROM (VALUES
  ('black-full-sleeve','Black',120000,'match-jersey-performance-'),
  ('blue-jersey','Blue',89900,'club-tee-performance-'),
  ('white-jersey','White',89900,'club-tee-cotton-')
) p(id,colour,price,source)
CROSS JOIN (VALUES ('S'),('M'),('L'),('XL'),('XXL')) s(size)
JOIN boundary_product_variants v ON v.id=p.source||lower(s.size);

INSERT INTO boundary_product_variants(id,product_id,material,size,colour,price_paise,stock,enabled)
SELECT 'maroon-cap-adjustable','maroon-cap','Standard','Adjustable','Maroon',39900,stock,enabled
FROM boundary_product_variants WHERE id='club-cap-cotton';

-- Fail closed if the expected source inventory was not present.
DO $$ BEGIN
  IF (SELECT count(*) FROM boundary_product_variants
      WHERE product_id IN ('black-full-sleeve','blue-jersey','white-jersey','maroon-cap')) <> 16 THEN
    RAISE EXCEPTION 'Four-photo collection requires all 16 source variants';
  END IF;
END $$;

UPDATE boundary_product_variants SET stock=0
WHERE id LIKE 'match-jersey-performance-%' OR id LIKE 'club-tee-performance-%'
   OR id LIKE 'club-tee-cotton-%' OR id='club-cap-cotton';
UPDATE boundary_products SET active=false
WHERE id NOT IN ('black-full-sleeve','blue-jersey','white-jersey','maroon-cap');
UPDATE boundary_product_variants SET enabled=false
WHERE product_id NOT IN ('black-full-sleeve','blue-jersey','white-jersey','maroon-cap');

-- Unpaid pre-migration orders retain their IDs, but a cancellation returns
-- their units to the replacement SKU rather than stranding hidden inventory.
CREATE TABLE boundary_variant_replacements (
  source_id TEXT PRIMARY KEY REFERENCES boundary_product_variants(id),
  replacement_id TEXT NOT NULL REFERENCES boundary_product_variants(id),
  CHECK (source_id <> replacement_id)
);
INSERT INTO boundary_variant_replacements(source_id,replacement_id)
SELECT p.source||lower(s.size),p.id||'-'||lower(s.size)
FROM (VALUES ('black-full-sleeve','match-jersey-performance-'),
             ('blue-jersey','club-tee-performance-'),
             ('white-jersey','club-tee-cotton-')) p(id,source)
CROSS JOIN (VALUES ('S'),('M'),('L'),('XL'),('XXL')) s(size);
INSERT INTO boundary_variant_replacements VALUES ('club-cap-cotton','maroon-cap-adjustable');

-- A trigger also covers an old release-function invocation already waiting on
-- the checkout lock when this migration commits. Its UPDATE still reaches the
-- current table trigger, so no stock can be returned to the retired source ID.
CREATE FUNCTION boundary_redirect_retired_stock()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target_id TEXT;
BEGIN
  IF NEW.stock > OLD.stock THEN
    SELECT replacement_id INTO target_id FROM boundary_variant_replacements WHERE source_id=OLD.id;
    IF FOUND THEN
      UPDATE boundary_product_variants SET stock=stock+(NEW.stock-OLD.stock) WHERE id=target_id;
      NEW.stock := OLD.stock;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER boundary_redirect_retired_stock BEFORE UPDATE OF stock ON boundary_product_variants
FOR EACH ROW EXECUTE FUNCTION boundary_redirect_retired_stock();
