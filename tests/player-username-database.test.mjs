import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {neon} from '@neondatabase/serverless';

const enabled=process.env.BOUNDARY_DATABASE_TESTS==='1'&&Boolean(process.env.DATABASE_URL);
test('username migration preserves IDs and enforces unique valid handles', {skip:!enabled}, async()=>{
  const sql=neon(process.env.DATABASE_URL);
  const migration=(await readFile(new URL('../database/migrations/012_player_usernames.sql',import.meta.url),'utf8'))
    .replaceAll('boundary_registered_players','boundary_username_migration_test');
  const setup=()=>sql`CREATE TEMP TABLE boundary_username_migration_test(owner_id TEXT PRIMARY KEY,name TEXT) ON COMMIT DROP`;
  const result=await sql.transaction([
    setup(),
    sql`INSERT INTO boundary_username_migration_test VALUES('user_legacy','Legacy player')`,
    sql.query('DO $test$ BEGIN\n'+migration+'\nEND $test$'),
    sql`INSERT INTO boundary_username_migration_test(owner_id,name,username) VALUES('user_new','New player','darsh_11')`,
    sql`INSERT INTO boundary_username_migration_test(owner_id,name,username) VALUES('user_other','Other player','darsh_11') ON CONFLICT(username) DO NOTHING RETURNING owner_id`,
    sql`SELECT owner_id,username FROM boundary_username_migration_test ORDER BY owner_id`,
  ]);
  assert.equal(result[4].length,0,'duplicate usernames must not be inserted');
  assert.deepEqual(result[5],[{owner_id:'user_legacy',username:null},{owner_id:'user_new',username:'darsh_11'}]);
  await assert.rejects(sql.transaction([setup(),sql.query('DO $test$ BEGIN\n'+migration+'\nEND $test$'),
    sql`INSERT INTO boundary_username_migration_test VALUES('user_invalid','Invalid','UPPERCASE')`
  ]),error=>error.code==='23514');
});
