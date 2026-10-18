-- Owner-confirmed retail prices on 18 October 2026, in INR paise.
-- Stock and sale availability are configured separately after fulfilment is confirmed.
UPDATE boundary_product_variants v SET price_paise=CASE p.category
  WHEN 'full-sleeve' THEN 120000
  WHEN 'tshirt' THEN 89900
  WHEN 'cap' THEN 39900
  WHEN 'bottle' THEN 29900 END
FROM boundary_products p WHERE p.id=v.product_id;
