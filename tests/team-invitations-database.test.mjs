import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {neon} from '@neondatabase/serverless';
const enabled=process.env.BOUNDARY_DATABASE_TESTS==='1'&&Boolean(process.env.DATABASE_URL);
test('team request identity is unique per owner/team/player and acceptance is owner scoped',{skip:!enabled},async()=>{
  const sql=neon(process.env.DATABASE_URL);
  const migration=(await readFile(new URL('../database/migrations/015_team_invitations.sql',import.meta.url),'utf8'))
    .replaceAll('boundary_','team_test_').replaceAll('CREATE TABLE ','CREATE TEMP TABLE ')
    .replace(/(CREATE TEMP TABLE [\s\S]*?\n\));/g,'$1 ON COMMIT DROP;');
  const result=await sql.transaction([
    sql`CREATE TEMP TABLE team_test_registered_players(owner_id TEXT PRIMARY KEY) ON COMMIT DROP`,
    sql`INSERT INTO team_test_registered_players VALUES('user_owner'),('user_other'),('user_player')`,
    sql.query('DO $test$ BEGIN\n'+migration+'\nEND $test$'),
    sql`INSERT INTO team_test_team_join_requests(owner_id,team_id,player_id) VALUES('user_owner','10000000-0000-4000-8000-000000000001','user_player')`,
    sql`INSERT INTO team_test_team_join_requests(owner_id,team_id,player_id) VALUES('user_owner','10000000-0000-4000-8000-000000000001','user_player') ON CONFLICT(owner_id,team_id,player_id) DO UPDATE SET player_id=excluded.player_id RETURNING status`,
    sql`UPDATE team_test_team_join_requests SET status='accepted' WHERE owner_id='user_other' AND team_id='10000000-0000-4000-8000-000000000001' AND player_id='user_player' RETURNING player_id`,
    sql`SELECT count(*)::int n,min(status) status FROM team_test_team_join_requests`,
    sql`UPDATE team_test_team_join_requests SET status='accepted' WHERE owner_id='user_owner' AND team_id='10000000-0000-4000-8000-000000000001' AND player_id='user_player' RETURNING status`,
  ]);
  assert.deepEqual(result[4],[{status:'pending'}]);assert.equal(result[5].length,0);
  assert.deepEqual(result[6],[{n:1,status:'pending'}]);assert.deepEqual(result[7],[{status:'accepted'}]);
});
