-- The storefront sells three categories: jerseys, full-sleeve jerseys and caps.
-- Bottles leave the public catalog. Rows are kept for order history; re-running changes nothing.
UPDATE boundary_products SET active=false WHERE category='bottle' AND active;
