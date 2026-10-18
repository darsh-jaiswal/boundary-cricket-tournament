ALTER TABLE boundary_shop_orders ADD COLUMN gateway_attempt_at TIMESTAMPTZ;
ALTER TABLE boundary_shop_orders ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE boundary_shop_orders ADD COLUMN refund_id TEXT;
ALTER TABLE boundary_shop_orders ADD COLUMN refund_status TEXT;

CREATE FUNCTION boundary_release_shop_reservation(p_id UUID,p_owner TEXT)
RETURNS boundary_shop_orders LANGUAGE plpgsql AS $$
DECLARE result boundary_shop_orders; line RECORD;
BEGIN
  PERFORM pg_advisory_xact_lock(6820431002);
  SELECT * INTO result FROM boundary_shop_orders WHERE id=p_id AND owner_id=p_owner FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'order_missing'; END IF;
  IF result.cancelled_at IS NOT NULL THEN RETURN result; END IF;
  IF result.status='paid' OR result.status='refunded' THEN RAISE EXCEPTION 'already_paid'; END IF;
  IF result.razorpay_order_id IS NULL AND result.gateway_attempt_at>now()-interval '60 seconds' THEN
    RAISE EXCEPTION 'gateway_in_progress';
  END IF;
  FOR line IN SELECT * FROM boundary_stock_reservations WHERE order_id=p_id LOOP
    UPDATE boundary_product_variants SET stock=stock+line.quantity WHERE id=line.variant_id;
  END LOOP;
  UPDATE boundary_shop_orders SET status='cancelled',cancelled_at=now() WHERE id=p_id RETURNING * INTO result;
  RETURN result;
END;
$$;
