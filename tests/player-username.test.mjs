import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeUsername} from '../server/player-username.js';

test('handles have one case-insensitive public identity',()=>{
  assert.equal(normalizeUsername(' @Darsh_11 '),'darsh_11');
  assert.equal(normalizeUsername('Darsh_11'),'darsh_11');
});
test('rejects malformed, ambiguous and oversized usernames',()=>{
  for(const name of [null,{},'ab','1batter','a-batter','a b','a@example.com','@@darsh','a'.repeat(25),'दर्श','user_darsh','@User_Darsh'])
    assert.equal(normalizeUsername(name),null);
  assert.equal(normalizeUsername('a'.repeat(24)),'a'.repeat(24));
});
