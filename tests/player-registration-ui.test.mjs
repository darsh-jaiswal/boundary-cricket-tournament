import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('a delayed saved-profile response cannot replace a newly registered WhatsApp username',async()=>{
  const nodes=new Map();
  const node=id=>{
    if(!nodes.has(id))nodes.set(id,{hidden:true,disabled:true,textContent:'',classList:{toggle(){}},elements:{name:{value:''},username:{value:''}},reset(){}});
    return nodes.get(id);
  };
  const clerk={user:{id:'user_test'},isSignedIn:true,session:{getToken:async()=> 'test-session'}};
  let resolveProfile;
  const profile=new Promise(resolve=>{resolveProfile=resolve;});
  const fetch=async(url,options)=>{
    if(url.includes('?config'))return {ok:true,json:async()=>({publishableKey:'test'})};
    const request=JSON.parse(options.body);
    if(request.action==='me')return profile;
    assert.equal(request.action,'register');
    return {ok:true,json:async()=>({id:'user_test',name:'New player',username:'new_handle'})};
  };
  const source=(await readFile(new URL('../web/player.js',import.meta.url),'utf8')).replace(/^import[^\n]*\n/,'');
  const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
  await new AsyncFunction('document','location','fetch','account','verifiedGoogle','FormData',source)(
    {querySelector:node},{search:'?team=Test%20XI'},fetch,
    async(_key,_target,onChange)=>onChange(clerk),()=>true,
    class {get(key){return key==='name'?'New player':'new_handle';}}
  );
  await node('#register').onsubmit({preventDefault(){},currentTarget:node('#register')});
  assert.equal(node('#register').elements.username.value,'new_handle');
  resolveProfile({ok:true,json:async()=>({id:'user_test',name:'Old player',username:'old_handle'})});
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(node('#register').elements.username.value,'new_handle');
  assert.match(decodeURIComponent(node('#share').href),/@new_handle/);
});
