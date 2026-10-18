import { guardRequest, rateLimit, securityError } from '../server/security.js';
import { neon } from '@neondatabase/serverless';
import { createClerkClient } from '@clerk/backend';
import { randomUUID } from 'node:crypto';

const categories = new Set(['players','opponents','umpires','scorers','grounds','coaches','updates']);
const uuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const text = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.trim().length <= max;
let sql, clerk;

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (!process.env.DATABASE_URL || !process.env.CLERK_SECRET_KEY || !process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY)
    return res.status(503).json({ error: 'Community service is not configured' });
  sql ??= neon(process.env.DATABASE_URL);
  clerk ??= createClerkClient({ secretKey: process.env.CLERK_SECRET_KEY,
    publishableKey: process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY });
  try {
    await guardRequest(req,res,'community',8000);
    if (req.method === 'GET') {
      if (req.query.config === '1') return res.status(200).json({ publishableKey: process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY });
      if (req.query.post) {
        if (!uuid(req.query.post)) return res.status(400).json({error:'Invalid post'});
        const replies = await sql`SELECT id,post_id,owner_id,author,body,created FROM boundary_community_replies WHERE post_id=${req.query.post} ORDER BY created,id LIMIT 100`;
        return res.status(200).json(replies);
      }
      const rows = await sql`SELECT id,owner_id,author,category,title,city,body,created FROM boundary_community_posts ORDER BY created DESC,id DESC LIMIT 100`;
      return res.status(200).json(rows);
    }
    if (!['POST','DELETE'].includes(req.method)) { res.setHeader('Allow','GET, POST, DELETE'); return res.status(405).json({error:'Method not allowed'}); }
    const origin = 'https://boundary-cricket-tournament.vercel.app';
    const headers = new Headers();
    for (const name of ['authorization','cookie','origin']) if (typeof req.headers[name] === 'string') headers.set(name,req.headers[name]);
    const authState = await clerk.authenticateRequest(new Request(origin + '/api/community', {headers}), {
      authorizedParties: [origin, ...(process.env.VERCEL_ENV !== 'production' ? ['http://localhost:3000'] : [])],
    });
    const auth = authState.toAuth();
    if (!auth?.userId) return res.status(401).json({error:'Sign in to post or reply'});
    await rateLimit(res,'community:account-write',auth.userId,20);
    const user = await clerk.users.getUser(auth.userId);
    if (!user.phoneNumbers?.some(phone => phone.verification?.status === 'verified'))
      return res.status(403).json({error:'Verify a phone number on your account before joining the player community'});
    let body;
    try { body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body; }
    catch { return res.status(400).json({error:'Invalid request'}); }
    if (!body || JSON.stringify(body).length > 8000) return res.status(400).json({error:'Invalid request'});
    if (req.method === 'DELETE') {
      if (!uuid(body.id)) return res.status(400).json({error:'Invalid post'});
      const rows = await sql`DELETE FROM boundary_community_posts WHERE id=${body.id} AND owner_id=${auth.userId} RETURNING id`;
      return res.status(rows.length ? 200 : 403).json(rows.length ? {removed:true} : {error:'Only the author can remove a post'});
    }
    const author = (user.fullName || user.username || 'Boundary member').slice(0,100);
    if (body.postId) {
      if (!uuid(body.postId) || !text(body.body,1000)) return res.status(400).json({error:'Enter a reply up to 1000 characters'});
      const result = await sql.transaction([
        sql`SELECT pg_advisory_xact_lock(hashtextextended(${auth.userId},0))`,
        sql`INSERT INTO boundary_community_replies(id,post_id,owner_id,author,body)
        SELECT ${randomUUID()}::uuid,${body.postId}::uuid,${auth.userId},${author},${body.body.trim()}
        WHERE EXISTS(SELECT 1 FROM boundary_community_posts WHERE id=${body.postId})
        AND (SELECT count(*) FROM boundary_community_replies WHERE owner_id=${auth.userId} AND created>now()-interval '1 minute')<10 RETURNING id`,
      ]);
      const rows = result[1];
      return res.status(rows.length ? 201 : 429).json(rows.length ? {created:true} : {error:'Post unavailable or reply limit reached. Please wait.'});
    }
    if (!categories.has(body.category) || !text(body.title,160) || !text(body.city,100) || !text(body.body,3000))
      return res.status(400).json({error:'Choose a category and complete the title, city and details'});
    const result = await sql.transaction([
      sql`SELECT pg_advisory_xact_lock(hashtextextended(${auth.userId},0))`,
      sql`INSERT INTO boundary_community_posts(id,owner_id,author,category,title,city,body)
      SELECT ${randomUUID()}::uuid,${auth.userId},${author},${body.category},${body.title.trim()},${body.city.trim()},${body.body.trim()}
      WHERE (SELECT count(*) FROM boundary_community_posts WHERE owner_id=${auth.userId} AND created>now()-interval '1 minute')<3 RETURNING id`,
    ]);
    const rows = result[1];
    return res.status(rows.length ? 201 : 429).json(rows.length ? {created:true} : {error:'Posting limit reached. Please wait a minute.'});
  } catch (error) {
    return securityError(res,error,'Community service unavailable. Please retry.');
  }
}
