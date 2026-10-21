import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSquadPlayer } from '../server/practice-players.js';

test('real squad players still require Google registration',async()=>{
  const calls=[];
  const result=await validateSquadPlayer(()=>{throw Error('Unexpected practice lookup');},async id=>{calls.push(id);return {verified:true};},'owner','user_real');
  assert.deepEqual(calls,['user_real']);assert.equal(result.verified,true);
});
test('practice validation is scoped to the authenticated organizer',async()=>{
  const queries=[];
  const sql=async(strings,...values)=>{queries.push({query:strings.join('?'),values});return [{id:'user_practice'+'a'.repeat(32),name:'MI Practice 1'}];};
  const player=await validateSquadPlayer(sql,()=>{throw Error('Practice players cannot use Clerk');},'user_owner','user_practice'+'a'.repeat(32));
  assert.equal(player.practice,true);assert.deepEqual(queries[0].values,['user_practice'+'a'.repeat(32),'user_owner']);
  assert.match(queries[0].query,/id=\? AND owner_id=\?/);
});
test('another account cannot use a seeded practice identity',async()=>{
  await assert.rejects(validateSquadPlayer(async()=>[],()=>{},'user_other','user_practice'+'a'.repeat(32)),error=>error.status===403);
});
