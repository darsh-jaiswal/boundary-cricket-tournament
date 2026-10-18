import { createHmac } from 'node:crypto';
import { isIP } from 'node:net';
import { db, APP_ORIGIN, HttpError } from './auth.js';

export function clientIp(req) {
  // Vercel sets this at its ingress. Outside Vercel, do not trust client-supplied forwarding headers.
  const forwarded=process.env.VERCEL==='1' ? req.headers['x-forwarded-for'] : undefined;
  const raw=typeof forwarded==='string'?forwarded.split(',')[0].trim():req.socket?.remoteAddress;
  return typeof raw==='string'&&isIP(raw)?raw:'unknown';
}
function digest(subject) {
  const secret=process.env.RATE_LIMIT_SECRET||process.env.CLERK_SECRET_KEY;
  if(!secret)throw new HttpError(503,'Security service is not configured');
  return createHmac('sha256',secret).update(subject).digest('hex');
}
export async function rateLimit(res,scope,subject,limit,seconds=60) {
  const sql=db();const rows=await sql`SELECT * FROM boundary_take_rate_limit(${scope},${digest(subject)},${limit},${seconds})`;
  const result=rows[0];
  res.setHeader('RateLimit-Limit',String(limit));res.setHeader('RateLimit-Remaining',String(result.remaining));
  res.setHeader('RateLimit-Reset',String(result.retry_after));
  if(!result.allowed){res.setHeader('Retry-After',String(result.retry_after));throw new HttpError(429,'Too many requests. Please wait and retry.');}
}
export async function guardRequest(req,res,route,maxBytes=12000) {
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
  const method=req.method;
  if(!['GET','POST','DELETE'].includes(method)){res.setHeader('Allow','GET, POST, DELETE');throw new HttpError(405,'Method not allowed');}
  const origin=req.headers.origin;
  if(origin && origin!==APP_ORIGIN && !(process.env.VERCEL_ENV!=='production'&&origin==='http://localhost:3000'))
    throw new HttpError(403,'Request origin is not allowed');
  if(method!=='GET'){
    if(req.headers['sec-fetch-site']==='cross-site')throw new HttpError(403,'Cross-site writes are not allowed');
    const type=req.headers['content-type'];
    if(typeof type!=='string'||!/^application\/json(?:\s*;|$)/i.test(type))throw new HttpError(415,'Use application/json');
    const declared=req.headers['content-length'];
    if(declared!==undefined&&(!/^\d+$/.test(String(declared))||Number(declared)>maxBytes))throw new HttpError(413,'Request is too large');
    if(req.body!==undefined&&Buffer.byteLength(typeof req.body==='string'?req.body:JSON.stringify(req.body),'utf8')>maxBytes)
      throw new HttpError(413,'Request is too large');
  }
  const ip=clientIp(req);
  await rateLimit(res,route+':'+(method==='GET'?'read':'write'),'ip:'+ip,method==='GET'?240:60);
}
export function securityError(res,error,fallback) {
  if(!error.status)console.error('API request failed',error.code||error.name||'service_error');
  return res.status(error.status||503).json({error:error.status?error.message:fallback});
}
