import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { db } from '../server/auth.js';
import { guardRequest, clientIp } from '../server/security.js';
import { validCheckoutSignature } from '../server/razorpay.js';

const sql=db(),subject=randomUUID(),scope='security-verification';
try {
  // All requests hit one shared database counter even when concurrent callers race.
  const results=await Promise.all(Array.from({length:12},()=>sql`SELECT * FROM boundary_take_rate_limit(${scope},${subject},5,60)`));
  assert.equal(results.filter(r=>r[0].allowed).length,5);
  assert.ok(results.filter(r=>!r[0].allowed).every(r=>r[0].remaining===0&&r[0].retry_after>=1));
  assert.equal((await sql`SELECT * FROM boundary_take_rate_limit(${scope},${randomUUID()},5,60)`)[0].allowed,true);
  const response={setHeader(){}};
  await assert.rejects(()=>guardRequest({method:'POST',headers:{origin:'https://evil.invalid','content-type':'application/json'},body:{}},response,'verify'),e=>e.status===403);
  await assert.rejects(()=>guardRequest({method:'POST',headers:{'content-type':'text/plain'},body:'x'},response,'verify'),e=>e.status===415);
  await assert.rejects(()=>guardRequest({method:'POST',headers:{'content-type':'application/json','content-length':'9999999'},body:{}},response,'verify'),e=>e.status===413);
  assert.equal(validCheckoutSignature('order_fake','pay_fake','0'.repeat(64)),false);
  const previous=process.env.VERCEL;delete process.env.VERCEL;
  assert.equal(clientIp({headers:{'x-forwarded-for':'1.2.3.4'},socket:{remoteAddress:'127.0.0.1'}}),'127.0.0.1');
  if(previous!==undefined)process.env.VERCEL=previous;
  console.log('PASS: concurrent shared limiter, subject isolation, origin rejection, content type, body limit, forged payment signature, local proxy spoofing');
} finally {
  // Remove only this run's unique verification scope; no match/account/order data is touched.
  await sql`DELETE FROM boundary_rate_limits WHERE scope=${scope}`;
}
