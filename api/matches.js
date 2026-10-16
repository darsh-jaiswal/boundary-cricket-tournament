import { neon } from '@neondatabase/serverless';
import { createHash, timingSafeEqual } from 'node:crypto';
let connection;
const hash = key => createHash('sha256').update(key).digest('hex');
async function database() {
  if (!process.env.DATABASE_URL) throw new Error('Database is not configured');
  connection ??= neon(process.env.DATABASE_URL);
  return connection;
}
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  try {
    const sql = await database();
    if (req.method === 'GET') {
      const id = req.query.id;
      if(req.query.journal==='1'){
        const authority=(req.headers.authorization||'').replace(/^Bearer /,'');
        if(typeof id!=='string'||authority.length<50)return res.status(403).json({error:'Scoring authority required'});
        const rows=await sql`SELECT revision,private_journal FROM cricket_matches WHERE id=${id} AND owner_hash=${hash(authority)}`;
        if(!rows.length)return res.status(403).json({error:'Scoring authority required'});
        return res.status(200).json({id,revision:rows[0].revision,journal:rows[0].private_journal});
      }
      const rows = typeof id === 'string' ? await sql`SELECT data FROM cricket_matches WHERE id=${id}` : await sql`SELECT data FROM cricket_matches ORDER BY updated DESC, id DESC LIMIT 50`;
      return res.status(200).json(typeof id === 'string' ? (rows[0]?.data ?? null) : rows.map(row => row.data));
    }
    if(req.method==='DELETE') {
      const body=typeof req.body==='string'?JSON.parse(req.body):req.body;
      if(!body||typeof body.id!=='string'||typeof body.writeKey!=='string'||body.writeKey.length<50)return res.status(400).json({error:'Invalid match authority'});
      const rows=await sql`DELETE FROM cricket_matches WHERE id=${body.id} AND owner_hash=${hash(body.writeKey)} RETURNING id`;
      return res.status(rows.length?200:403).json({removed:rows.length>0});
    }
    if (req.method !== 'POST') { res.setHeader('Allow', 'GET, POST, DELETE'); return res.status(405).json({ error: 'Method not allowed' }); }
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
    if (!body || !/^[a-f0-9-]{36}$/i.test(body.id || '') || typeof body.writeKey !== 'string' || body.writeKey.length < 50 || !Number.isInteger(body.revision) || body.revision < 0 || !Number.isInteger(body.runs) || body.runs < 0 || !Number.isInteger(body.wickets) || body.wickets < 0 || typeof body.name !== 'string' || body.name.length > 300 || !Array.isArray(body.commentary) || JSON.stringify(body).length > 250000) return res.status(400).json({ error: 'Invalid match snapshot' });
    if(body.privateJournal!==undefined&&(typeof body.privateJournal!=='string'||body.privateJournal.length>200000))return res.status(400).json({error:'Invalid encrypted journal'});
    const { writeKey, privateJournal, ...data } = body;
    const owner = hash(writeKey);
    const rows = await sql`INSERT INTO cricket_matches (id,owner_hash,revision,data,private_journal) VALUES (${data.id},${owner},${data.revision},${JSON.stringify(data)}::jsonb,${privateJournal??null}) ON CONFLICT(id) DO UPDATE SET revision=EXCLUDED.revision,data=EXCLUDED.data,private_journal=COALESCE(EXCLUDED.private_journal,cricket_matches.private_journal),updated=now() WHERE cricket_matches.owner_hash=EXCLUDED.owner_hash AND (cricket_matches.revision<EXCLUDED.revision OR (cricket_matches.revision=EXCLUDED.revision AND cricket_matches.data=EXCLUDED.data)) RETURNING id`;
    if (rows.length) return res.status(200).json({ accepted: true, revision: data.revision });
    const existing = await sql`SELECT owner_hash,revision FROM cricket_matches WHERE id=${data.id}`;
    if (!existing.length || !timingSafeEqual(Buffer.from(existing[0].owner_hash),Buffer.from(owner))) return res.status(403).json({ error: 'This match belongs to another scorer' });
    return res.status(409).json({ error: 'A newer revision already exists' });
  } catch (error) { console.error('Match API:', error.message); return res.status(503).json({ error: 'Score service temporarily unavailable; device scores are preserved' }); }
}
