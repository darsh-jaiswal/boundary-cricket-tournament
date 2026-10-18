CREATE TABLE boundary_products (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  category TEXT NOT NULL CHECK(category IN ('tshirt','full-sleeve','cap','bottle')),
  description TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE boundary_product_variants (
  id TEXT PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES boundary_products(id),
  material TEXT NOT NULL,
  size TEXT NOT NULL,
  colour TEXT NOT NULL,
  price_paise INTEGER NOT NULL DEFAULT 0 CHECK(price_paise >= 0 AND price_paise <= 10000000),
  stock INTEGER NOT NULL DEFAULT 0 CHECK(stock >= 0),
  enabled BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE boundary_shop_orders (
  id UUID PRIMARY KEY,
  owner_id TEXT NOT NULL,
  request_id UUID NOT NULL,
  status TEXT NOT NULL DEFAULT 'reserving' CHECK(status IN ('reserving','checkout','paid','refunded','cancelled','review')),
  amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
  shipping_paise INTEGER NOT NULL CHECK(shipping_paise >= 0),
  items JSONB NOT NULL,
  shipping JSONB NOT NULL,
  razorpay_order_id TEXT UNIQUE,
  razorpay_payment_id TEXT UNIQUE,
  created TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at TIMESTAMPTZ,
  UNIQUE(owner_id, request_id)
);
CREATE INDEX boundary_shop_orders_owner ON boundary_shop_orders(owner_id, created DESC);
CREATE TABLE boundary_stock_reservations (
  order_id UUID NOT NULL REFERENCES boundary_shop_orders(id),
  variant_id TEXT NOT NULL REFERENCES boundary_product_variants(id),
  quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 10),
  PRIMARY KEY(order_id, variant_id)
);
-- Products exist in the real catalog. Sales require actual prices, stock and fulfilment configuration.
INSERT INTO boundary_products(id,name,category,description) VALUES
('club-tee','Boundary Club T-shirt','tshirt','Short-sleeve teamwear with the Boundary crest.'),
('match-jersey','Boundary Full-sleeve Jersey','full-sleeve','Long-sleeve cricket teamwear with the Boundary crest.'),
('club-cap','Boundary Club Cap','cap','Adjustable club cap with the Boundary crest.'),
('club-bottle','Boundary Training Bottle','bottle','Reusable bottle for practice and match day.');
INSERT INTO boundary_product_variants(id,product_id,material,size,colour)
SELECT p.id||'-'||m.code||'-'||lower(s.size),p.id,m.material,s.size,'Boundary Green'
FROM boundary_products p
CROSS JOIN (VALUES ('cotton','Cotton'),('performance','Performance polyester'),('premium','Premium cotton blend')) m(code,material)
CROSS JOIN (VALUES ('S'),('M'),('L'),('XL'),('XXL')) s(size)
WHERE p.category IN ('tshirt','full-sleeve');
INSERT INTO boundary_product_variants(id,product_id,material,size,colour) VALUES
('club-cap-cotton','club-cap','Cotton twill','Adjustable','Boundary Green'),
('club-cap-performance','club-cap','Performance polyester','Adjustable','Boundary Green'),
('club-bottle-steel','club-bottle','Stainless steel','750 ml','Forest Green'),
('club-bottle-polymer','club-bottle','Polymer','750 ml','Forest Green');

CREATE FUNCTION boundary_reserve_shop_order(p_id UUID,p_owner TEXT,p_request UUID,p_items JSONB,p_shipping JSONB,p_fee INTEGER)
RETURNS boundary_shop_orders LANGUAGE plpgsql AS $$
DECLARE result boundary_shop_orders; line RECORD; total INTEGER := 0; snapshot JSONB := '[]'::jsonb;
BEGIN
  PERFORM pg_advisory_xact_lock(6820431002);
  SELECT * INTO result FROM boundary_shop_orders WHERE owner_id=p_owner AND request_id=p_request;
  IF FOUND THEN RETURN result; END IF;
  IF (SELECT count(*) FROM boundary_shop_orders WHERE owner_id=p_owner AND status IN ('reserving','checkout','review')) >= 3 THEN
    RAISE EXCEPTION 'pending_order_limit';
  END IF;
  IF jsonb_typeof(p_items)<>'array' OR jsonb_array_length(p_items) NOT BETWEEN 1 AND 10 OR p_fee<0 THEN
    RAISE EXCEPTION 'invalid_cart';
  END IF;
  IF (SELECT count(DISTINCT item->>'variantId') FROM jsonb_array_elements(p_items) item) <> jsonb_array_length(p_items) THEN
    RAISE EXCEPTION 'invalid_cart';
  END IF;
  FOR line IN
    SELECT v.*,p.name,p.active,(item->>'quantity')::integer quantity FROM jsonb_array_elements(p_items) item
    LEFT JOIN boundary_product_variants v ON v.id=item->>'variantId'
    LEFT JOIN boundary_products p ON p.id=v.product_id
  LOOP
    IF line.id IS NULL OR NOT line.enabled OR NOT line.active OR line.price_paise<=0
       OR line.quantity IS NULL OR line.quantity NOT BETWEEN 1 AND 10 OR line.stock<line.quantity THEN
      RAISE EXCEPTION 'item_unavailable';
    END IF;
    total := total + line.price_paise * line.quantity;
    snapshot := snapshot || jsonb_build_array(jsonb_build_object('variantId',line.id,'name',line.name,
      'material',line.material,'size',line.size,'colour',line.colour,'pricePaise',line.price_paise,'quantity',line.quantity));
  END LOOP;
  INSERT INTO boundary_shop_orders(id,owner_id,request_id,amount_paise,shipping_paise,items,shipping)
    VALUES(p_id,p_owner,p_request,total+p_fee,p_fee,snapshot,p_shipping) RETURNING * INTO result;
  FOR line IN SELECT * FROM jsonb_to_recordset(snapshot) AS x("variantId" TEXT,quantity INTEGER) LOOP
    UPDATE boundary_product_variants SET stock=stock-line.quantity WHERE id=line."variantId";
    INSERT INTO boundary_stock_reservations(order_id,variant_id,quantity) VALUES(p_id,line."variantId",line.quantity);
  END LOOP;
  RETURN result;
END;
$$;
