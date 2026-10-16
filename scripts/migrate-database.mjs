import { neon } from '@neondatabase/serverless';
import { createHash } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';

if (!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');
const sql = neon(process.env.DATABASE_URL);
const folder = new URL('../database/migrations/', import.meta.url);
const files = (await readdir(folder)).filter(name => /^\d+_[a-z0-9_]+\.sql$/.test(name)).sort();
for (const name of files) {
  const statement = (await readFile(new URL(name, folder), 'utf8')).replace(/\r\n/g, '\n');
  const checksum = createHash('sha256').update(statement).digest('hex');
  // Name is restricted by the filename allowlist; checksum is hex. SQL comes only
  // from reviewed repository migrations, never from API requests.
  const migration = `DO $boundary_runner$
    DECLARE existing_checksum TEXT;
    BEGIN
      CREATE TABLE IF NOT EXISTS boundary_schema_migrations (
        name TEXT PRIMARY KEY, checksum TEXT NOT NULL,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );
      SELECT checksum INTO existing_checksum FROM boundary_schema_migrations WHERE name='${name}';
      IF FOUND THEN
        IF existing_checksum <> '${checksum}' THEN
          RAISE EXCEPTION 'Applied migration was modified: ${name}';
        END IF;
      ELSE
        ${statement}
        INSERT INTO boundary_schema_migrations (name,checksum) VALUES ('${name}','${checksum}');
      END IF;
    END
    $boundary_runner$;`;
  // All statements use one database transaction. Lock serializes concurrent deploys.
  await sql.transaction([
    sql`SELECT pg_advisory_xact_lock(6820431001)`,
    sql.query(migration),
  ]);
  console.log(`Migration ready: ${name}`);
}
