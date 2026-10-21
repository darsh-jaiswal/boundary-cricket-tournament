import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { completeAppSignIn } from '../server/app-signin.js';

test('pending sign-in issues only the hashed one-time code', async () => {
  let saved;
  const sql = async (strings, ...values) => { saved = values; return [{ device_id: 'device' }]; };
  const result = await completeAppSignIn(sql, 'user_owner', 'device');
  assert.match(result.code, /^[a-f0-9]{64}$/);
  assert.deepEqual(saved, ['user_owner', createHash('sha256').update(result.code).digest('hex'), 'device']);
});

test('retry after the Android exchange reports success only for the same active owner', async () => {
  const queries = [];
  const sql = async (strings, ...values) => {
    queries.push({ query: strings.join('?'), values });
    return queries.length === 1 ? [] : [{ id: 'device' }];
  };
  assert.deepEqual(await completeAppSignIn(sql, 'user_owner', 'device'), { alreadyLinked: true });
  assert.deepEqual(queries[1].values, ['device', 'user_owner']);
  assert.match(queries[1].query, /login_version=2 AND owner_id=\? AND expires>now\(\)/);
});

test('expired, revoked or different-owner requests cannot complete', async () => {
  await assert.rejects(completeAppSignIn(async () => [], 'user_other', 'device'), error => error.status === 409);
});
