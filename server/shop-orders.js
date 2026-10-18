import { razorpay } from './razorpay.js';

export async function recoverGatewayOrder(sql, order) {
  if(order.razorpay_order_id||!order.gateway_attempt_at)return order;
  const found=await razorpay('orders?receipt='+encodeURIComponent(order.id)+'&count=100');
  const remote=found.items?.find(o=>o.receipt===order.id&&Number(o.amount)===order.amount_paise&&o.currency==='INR');
  if(!remote)return order;
  const rows=await sql`UPDATE boundary_shop_orders SET razorpay_order_id=${remote.id},
    status=CASE WHEN cancelled_at IS NULL THEN 'checkout' ELSE status END WHERE id=${order.id} RETURNING *`;
  return rows[0];
}

export async function reconcile(sql, original) {
  let order=await recoverGatewayOrder(sql,original);
  if(!order.razorpay_order_id)return order;
  const payments=await razorpay('orders/'+encodeURIComponent(order.razorpay_order_id)+'/payments');
  const payment=payments.items?.find(p=>p.order_id===order.razorpay_order_id&&p.currency==='INR'
    &&Number(p.amount)===order.amount_paise&&['captured','refunded'].includes(p.status));
  if(!payment)return order;
  // Read current state again: cancellation may have released stock during the provider request.
  const current=await sql`SELECT * FROM boundary_shop_orders WHERE id=${order.id}`;order=current[0];
  if(order.cancelled_at) {
    let refund;
    if(order.refund_id)refund=await razorpay('refunds/'+encodeURIComponent(order.refund_id));
    else if(Number(payment.amount_refunded)===Number(payment.amount))return (await sql`UPDATE boundary_shop_orders SET status='refunded',refund_status='processed' WHERE id=${order.id} RETURNING *`)[0];
    else refund=await razorpay('payments/'+encodeURIComponent(payment.id)+'/refund',
      {amount:order.amount_paise},{'X-Refund-Idempotency':'boundary-cancel-'+order.id});
    return (await sql`UPDATE boundary_shop_orders SET status=${refund.status==='processed'?'refunded':'cancelled'},
      razorpay_payment_id=${payment.id},refund_id=${refund.id},refund_status=${refund.status},paid_at=coalesce(paid_at,now())
      WHERE id=${order.id} RETURNING *`)[0];
  }
  const status=payment.status==='refunded'||Number(payment.amount_refunded)===Number(payment.amount)?'refunded':'paid';
  const rows=await sql`UPDATE boundary_shop_orders SET status=${status},razorpay_payment_id=${payment.id},paid_at=coalesce(paid_at,now())
    WHERE id=${order.id} AND cancelled_at IS NULL RETURNING *`;
  // If cancellation won the race, retry using its released state so captured money is refunded.
  return rows[0] || reconcile(sql,(await sql`SELECT * FROM boundary_shop_orders WHERE id=${order.id}`)[0]);
}
