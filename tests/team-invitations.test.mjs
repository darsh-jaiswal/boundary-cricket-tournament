import test from 'node:test';
import assert from 'node:assert/strict';
import {teamHandler} from '../api/teams.js';
import {HttpError} from '../server/auth.js';
const invite='a'.repeat(64),teamId='10000000-0000-4000-8000-000000000001';
function setup({rows=[],owner='user_owner',memberError,registrationError}={}){
  const queries=[],registrations=[];
  const sql=async(strings,...values)=>{queries.push({query:strings.join('?'),values});return rows;};
  const handler=teamHandler({database:()=>sql,member:async()=>{if(memberError)throw memberError;return{id:'user_player'};},
    deviceAccount:async()=>({owner_id:owner}),registered:async id=>{registrations.push(id);if(registrationError)throw registrationError;return{owner_id:id,name:'Test Player',username:'test_player'};},
    guard:async()=>{},limit:async()=>{}});
  return {queries,registrations,async request(body,method='POST',query={}){
    const res={code:0,value:null,setHeader(){},status(code){this.code=code;return this;},json(value){this.value=value;return this;}};
    await handler({method,body,headers:{},query},res);return res;
  }};
}
test('join requires a signed-in registered Google player before inserting requests',async()=>{
  for(const options of [{memberError:new HttpError(401,'Sign in')},{registrationError:new HttpError(403,'Register first')}]){
    const app=setup(options),res=await app.request({action:'join',invite});
    assert(res.code===401||res.code===403);assert.equal(app.queries.length,0);
  }
});
test('unapproved devices cannot create team invitations',async()=>{
  const app=setup({owner:null}),res=await app.request({action:'create',teamId,teamName:'XI'});
  assert.equal(res.code,401);assert.equal(app.queries.length,0);
});
test('join uses the invitation owner/team and authenticated player, ignoring spoofed IDs',async()=>{
  const app=setup({rows:[{status:'pending'}]});
  const res=await app.request({action:'join',invite,ownerId:'user_victim',teamId:'spoofed',playerId:'user_victim'});
  assert.equal(res.code,200);assert.deepEqual(app.registrations,['user_player']);
  assert.deepEqual(app.queries[0].values,['user_player',await hash(invite)]);
  assert.match(app.queries[0].query,/expires>now\(\)/);
});
test('owner-scoped requests and acceptance cannot target another organizer',async()=>{
  const app=setup(),list=await app.request({action:'requests',teamId,ownerId:'user_victim'});
  assert.equal(list.code,200);assert.deepEqual(app.queries[0].values,['user_owner',teamId,null,null,'']);
  const accept=await app.request({action:'accept',teamId,playerId:'user_player',ownerId:'user_victim'});
  assert.equal(accept.code,404);
  assert.deepEqual(app.queries[1].values,['user_owner',teamId,'user_player']);
});
test('expired and malformed invitations cannot be used',async()=>{
  const app=setup();
  assert.equal((await app.request(null,'GET',{invite:'bad'})).code,400);
  assert.equal(app.queries.length,0);
  assert.equal((await app.request(null,'GET',{invite})).code,404);
  assert.match(app.queries[0].query,/expires>now\(\)/);
  assert.equal((await app.request({action:'join',invite})).code,404);
});
async function hash(value){const{createHash}=await import('node:crypto');return createHash('sha256').update(value).digest('hex');}

test('request pages have a stable cursor and no pending request is hidden behind accepted history',async()=>{
  const rows=Array.from({length:101},(_,i)=>({id:'user_player'+i,name:'Player',username:'player'+i,status:'accepted',created:'2026-10-19T00:00:00.000123Z'}));
  const app=setup({rows}),res=await app.request({action:'requests',teamId});
  assert.equal(res.value.requests.length,100);assert.deepEqual(res.value.next,{created:'2026-10-19T00:00:00.000123Z',playerId:'user_player99'});
  await app.request({action:'requests',teamId,before:res.value.next});
  assert.deepEqual(app.queries[1].values,['user_owner',teamId,'2026-10-19T00:00:00.000123Z','2026-10-19T00:00:00.000123Z','user_player99']);
  assert.match(app.queries[1].query,/r.created DESC,r.player_id DESC/);
  assert.equal((await app.request({action:'requests',teamId,before:{created:'invalid',playerId:'user_player'}})).code,400);
});
