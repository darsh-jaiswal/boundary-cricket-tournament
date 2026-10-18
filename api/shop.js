import { randomUUID } from 'node:crypto';
import { db, verifiedMember, bodyOf, uuid, HttpError } from '../server/auth.js';
import { configured, keyId, razorpay, validCheckoutSignature } from '../server/razorpay.js';
import { reconcile, recoverGatewayOrder } from '../server/shop-orders.js';

const shippingFee = () => {
  const value = process.env.MERCH_SHIPPING_PAISE;
  return typeof value === 'string' && /^\d{1,7}$/.test(value) ? Number(value) : null;
};
const open = () => process.env.MERCH_SALES_ENABLED === 'true' && shippingFee() !== null && configured()
  && Boolean(process.env.MERCH_SUPPORT_EMAIL && process.env.MERCH_DISPATCH_DAYS);
const publicOrder = order => ({ id: order.id, status: order.status, amountPaise: order.amount_paise,
  shippingPaise: order.shipping_paise, items: order.items, created: order.created, paidAt: order.paid_at,
  razorpayOrderId: order.razorpay_order_id, refundStatus: order.refund_status, keyId: keyId() });

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  try {
    const sql = db();
    if (req.method === 'GET' && !req.query.orders && !req.query.order) {
      const products = await sql`SELECT p.id,p.name,p.category,p.description,
        jsonb_agg(jsonb_build_object('id',v.id,'material',v.material,'size',v.size,'colour',v.colour,
          'pricePaise',v.price_paise,'available',v.enabled AND v.stock>0 AND v.price_paise>0) ORDER BY v.material,v.size) variants
        FROM boundary_products p JOIN boundary_product_variants v ON p.id=v.product_id
        WHERE p.active GROUP BY p.id ORDER BY p.id`;
      return res.status(200).json({ products, salesEnabled: open(), shippingPaise: shippingFee(),
        supportEmail: process.env.MERCH_SUPPORT_EMAIL || null, dispatchDays: process.env.MERCH_DISPATCH_DAYS || null,
        publishableKey: process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY });
    }
    if (!['GET','POST'].includes(req.method)) { res.setHeader('Allow','GET, POST'); throw new HttpError(405,'Method not allowed'); }
    const user = await verifiedMember(req);
    if (req.method === 'GET') {
      if (req.query.order) {
        if (!uuid(req.query.order)) throw new HttpError(400,'Invalid order');
        const rows = await sql`SELECT * FROM boundary_shop_orders WHERE id=${req.query.order} AND owner_id=${user.id}`;
        if (!rows[0]) throw new HttpError(404,'Order not found');
        return res.status(200).json(publicOrder(await reconcile(sql, rows[0])));
      }
      const rows = await sql`SELECT * FROM boundary_shop_orders WHERE owner_id=${user.id} ORDER BY created DESC LIMIT 50`;
      return res.status(200).json(rows.map(publicOrder));
    }
    const body = bodyOf(req);
    if(body.action==='cancel') {
      if(!uuid(body.orderId))throw new HttpError(400,'Invalid order');
      const rows=await sql`SELECT * FROM boundary_shop_orders WHERE id=${body.orderId} AND owner_id=${user.id}`;
      if(!rows[0])throw new HttpError(404,'Order not found');
      const order=await reconcile(sql,rows[0]);
      if(['paid','refunded'].includes(order.status))throw new HttpError(409,'A captured payment cannot be cancelled here. Contact store support.');
      if(order.razorpay_order_id) {
        const payments=await razorpay('orders/'+encodeURIComponent(order.razorpay_order_id)+'/payments');
        if(payments.items?.some(p=>['authorized','captured'].includes(p.status)))throw new HttpError(409,'A payment is processing. Refresh its status before cancelling.');
      }
      const cancelled=await sql`SELECT * FROM boundary_release_shop_reservation(${order.id}::uuid,${user.id})`;
      return res.status(200).json(publicOrder(cancelled[0]));
    }
    if (body.action === 'verify') {
      if (!uuid(body.orderId)) throw new HttpError(400,'Invalid order');
      const rows = await sql`SELECT * FROM boundary_shop_orders WHERE id=${body.orderId} AND owner_id=${user.id}`;
      const order = rows[0];
      if (!order || body.razorpay_order_id !== order.razorpay_order_id
          || typeof body.razorpay_payment_id !== 'string' || !/^pay_[a-zA-Z0-9]+$/.test(body.razorpay_payment_id)
          || !validCheckoutSignature(order.razorpay_order_id, body.razorpay_payment_id, body.razorpay_signature))
        throw new HttpError(400,'Payment verification failed. Refresh My orders before paying again.');
      return res.status(200).json(publicOrder(await reconcile(sql, order)));
    }
    if (body.action !== 'checkout') throw new HttpError(400,'Unknown action');
    if(body.termsVersion!=='2026-10-18')throw new HttpError(400,'Please accept the current Terms & Conditions before ordering');
    if (!open()) throw new HttpError(503,'The store is preparing for launch. Checkout opens once stock and delivery are confirmed.');
    if (!uuid(body.requestId) || !Array.isArray(body.items) || body.items.length < 1 || body.items.length > 10
        || body.items.some(i => !i || typeof i.variantId !== 'string' || i.variantId.length > 100
          || !Number.isInteger(i.quantity) || i.quantity < 1 || i.quantity > 10)) throw new HttpError(400,'Invalid cart');
    const address = body.shipping;
    if (!address || ['name','address','city','state'].some(k => typeof address[k] !== 'string' || address[k].trim().length < 2 || address[k].length > 250)
        || !/^[1-9][0-9]{5}$/.test(address.pincode || '')) throw new HttpError(400,'Complete the Indian delivery address and six-digit PIN code');
    const shipping = { name: address.name.trim(), address: address.address.trim(), city: address.city.trim(),
      state: address.state.trim(), pincode: address.pincode, country: 'IN', phone: user.phone };
    const rows = await sql`SELECT * FROM boundary_reserve_shop_order(${randomUUID()}::uuid,${user.id},
      ${body.requestId}::uuid,${JSON.stringify(body.items)}::jsonb,${JSON.stringify(shipping)}::jsonb,${shippingFee()})`;
    let order = await recoverGatewayOrder(sql,rows[0]);
    if (!order.razorpay_order_id && !order.cancelled_at && ['reserving','review'].includes(order.status)) {
      // Only one request may create the gateway order for this durable request id.
      const claim = await sql`UPDATE boundary_shop_orders SET status='review',gateway_attempt_at=now() WHERE id=${order.id}
        AND cancelled_at IS NULL AND razorpay_order_id IS NULL AND (gateway_attempt_at IS NULL OR gateway_attempt_at<now()-interval '60 seconds') RETURNING id`;
      if (claim.length) {
        const created = await razorpay('orders', {amount: order.amount_paise, currency:'INR',receipt:order.id,
          notes:{application:'Boundary',boundary_order_id:order.id}});
        if (!created.id || Number(created.amount) !== order.amount_paise || created.currency !== 'INR')
          throw new HttpError(502,'Payment order could not be confirmed. Contact the store with your order number.');
        const saved = await sql`UPDATE boundary_shop_orders SET status=CASE WHEN cancelled_at IS NULL THEN 'checkout' ELSE status END,razorpay_order_id=${created.id}
          WHERE id=${order.id} RETURNING *`;
        order = saved[0];
      } else {
        const current = await sql`SELECT * FROM boundary_shop_orders WHERE id=${order.id}`; order = current[0];
      }
    }
    if (!order.razorpay_order_id) throw new HttpError(409,'This order is being checked. Open My orders before retrying.');
    return res.status(200).json({...publicOrder(order),prefill:{name:shipping.name,contact:user.phone}});
  } catch (error) {
    const businessError = /item_unavailable|invalid_cart|pending_order_limit|gateway_in_progress|already_paid/.exec(error.message || '')?.[0];
    const messages = {item_unavailable:'A selected size or material is unavailable. Refresh your cart.',invalid_cart:'Invalid cart',
      pending_order_limit:'You have three pending orders. Complete or cancel them before creating another.',
      gateway_in_progress:'Payment order is processing. Wait a minute, then refresh or cancel it.',already_paid:'Payment was captured. Contact store support.'};
    if (!error.status && !businessError) console.error('Shop request failed', error.code || error.name);
    return res.status(error.status || (businessError ? 409 : 503)).json({error:messages[businessError] ||
      (error.status ? error.message : 'Store service unavailable. Please retry.')});
  }
}
