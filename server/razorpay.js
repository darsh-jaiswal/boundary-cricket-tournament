import { createHmac, timingSafeEqual } from 'node:crypto';
import { HttpError } from './auth.js';

export const keyId = () => process.env.NEXT_PUBLIC_RAZORPAY_KEY_ID;
export const configured = () => Boolean(keyId() && process.env.RAZORPAY_KEY_SECRET);
export async function razorpay(path, body, extraHeaders = {}) {
  if (!configured()) throw new HttpError(503, 'Payment service is not configured');
  const response = await fetch('https://api.razorpay.com/v1/' + path, {
    method: body ? 'POST' : 'GET',
    headers: { Authorization: 'Basic ' + Buffer.from(keyId() + ':' + process.env.RAZORPAY_KEY_SECRET).toString('base64'),
      'Content-Type': 'application/json', ...extraHeaders },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(12000),
  });
  if (!response.ok) throw new HttpError(502, 'Payment provider could not complete this request. Retry from My orders.');
  return response.json();
}
export function validCheckoutSignature(orderId, paymentId, signature) {
  if (!process.env.RAZORPAY_KEY_SECRET || typeof signature !== 'string' || !/^[a-f0-9]{64}$/i.test(signature)) return false;
  const expected = createHmac('sha256', process.env.RAZORPAY_KEY_SECRET).update(orderId + '|' + paymentId).digest();
  return timingSafeEqual(expected, Buffer.from(signature, 'hex'));
}
