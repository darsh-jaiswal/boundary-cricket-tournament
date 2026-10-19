import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {neon} from '@neondatabase/serverless';

const enabled=process.env.BOUNDARY_DATABASE_TESTS==='1'&&Boolean(process.env.DATABASE_URL);
test('saved usernames cannot be changed or cleared; legacy players can choose once', {skip:!enabled}, async()=>{
  const sql=neon(process.env.DATABASE_URL);
  const migration=(await readFile(new URL('../database/migrations/013_permanent_player_usernames.sql',import.meta.url),'utf8'))
    .replaceAll('boundary_registered_players','boundary_permanent_username_test')
    .replaceAll('boundary_keep_player_username','pg_temp.boundary_keep_player_username');
  const setup=()=>[
    sql`CREATE TEMP TABLE boundary_permanent_username_test(owner_id TEXT PRIMARY KEY,name TEXT,username TEXT UNIQUE) ON COMMIT DROP`,
    sql.query('DO $test$ BEGIN\n'+migration+'\nEND $test$'),
    sql`INSERT INTO boundary_permanent_username_test VALUES('user_existing','Darsh','darsh_11'),('user_legacy','Legacy',NULL)`,
  ];
  const result=await sql.transaction([...setup(),
    sql`UPDATE boundary_permanent_username_test SET name='Darsh Jaiswal' WHERE owner_id='user_existing' RETURNING username`,
    sql`UPDATE boundary_permanent_username_test SET username='legacy_11' WHERE owner_id='user_legacy' RETURNING username`,
  ]);
  assert.equal(result[3][0].username,'darsh_11');
  assert.equal(result[4][0].username,'legacy_11');
  for(const next of ['changed_handle',null])await assert.rejects(sql.transaction([...setup(),
    sql`UPDATE boundary_permanent_username_test SET username=${next} WHERE owner_id='user_existing'`
  ]),error=>error.code==='23514'&&error.constraint==='boundary_username_permanent');
  await assert.rejects(sql.transaction([...setup(),
    sql`UPDATE boundary_permanent_username_test SET username='legacy_11' WHERE owner_id='user_legacy'`,
    sql`UPDATE boundary_permanent_username_test SET username='second_choice' WHERE owner_id='user_legacy'`
  ]),error=>error.code==='23514');
});
