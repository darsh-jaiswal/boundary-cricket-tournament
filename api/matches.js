import { neon } from '@neondatabase/serverless';
import { createHash, timingSafeEqual } from 'node:crypto';
let initialized;
const hash = key => createHash('sha256').update(key).digest('hex');
async function database() {
  if (!process.env.DATABASE_URL) throw new Error('Database is not configured');
  const sql = neon(process.env.DATABASE_URL);
  if (!initialized) initialized = sql`CREATE TABLE IF NOT EXISTS cricket_matches (id TEXT PRIMARY KEY, owner_hash TEXT NOT NULL, revision INTEGER NOT NULL, data JSONB NOT NULL, updated TIMESTAMPTZ DEFAULT now())`.catch(error => { initialized = undefined; throw error; });
  await initialized;
  return sql;
}
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  try {
    const sql = await database();
    if (req.method === 'GET') {
      const id = req.query.id;
      const rows = typeof id === 'string' ? await sql`SELECT data FROM cricket_matches WHERE id=${id}` : await sql`SELECT data FROM cricket_matches ORDER BY updated DESC LIMIT 50`;
      return res.status(200).json(typeof id === 'string' ? (rows[0]?.data ?? null) : rows.map(row => row.data));
    }
    if (req.method !== 'POST') { res.setHeader('Allow', 'GET, POST'); return res.status(405).json({ error: 'Method not allowed' }); }
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
    if (!body || !/^[a-f0-9-]{36}$/i.test(body.id || '') || typeof body.writeKey !== 'string' || body.writeKey.length < 50 || !Number.isInteger(body.revision) || body.revision < 0 || !Number.isInteger(body.runs) || body.runs < 0 || !Number.isInteger(body.wickets) || body.wickets < 0 || typeof body.name !== 'string' || body.name.length > 300 || !Array.isArray(body.commentary) || JSON.stringify(body).length > 250000) return res.status(400).json({ error: 'Invalid match snapshot' });
    const { writeKey, ...data } = body;
    const owner = hash(writeKey);
    const rows = await sql`INSERT INTO cricket_matches (id,owner_hash,revision,data) VALUES (${data.id},${owner},${data.revision},${JSON.stringify(data)}::jsonb) ON CONFLICT(id) DO UPDATE SET revision=EXCLUDED.revision,data=EXCLUDED.data,updated=now() WHERE cricket_matches.owner_hash=EXCLUDED.owner_hash AND cricket_matches.revision<=EXCLUDED.revision RETURNING id`;
    if (rows.length) return res.status(200).json({ accepted: true, revision: data.revision });
    const existing = await sql`SELECT owner_hash,revision FROM cricket_matches WHERE id=${data.id}`;
    if (!existing.length || !timingSafeEqual(Buffer.from(existing[0].owner_hash),Buffer.from(owner))) return res.status(403).json({ error: 'This match belongs to another scorer' });
    return res.status(409).json({ error: 'A newer revision already exists' });
  } catch (error) { console.error('Match API:', error.message); return res.status(503).json({ error: 'Score service temporarily unavailable; device scores are preserved' }); }
}
