import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const flush=()=>new Promise(resolve=>setImmediate(resolve));
async function screen({profile,register,search='',complete}={}){
  const nodes=new Map();
  const node=id=>{
    if(!nodes.has(id))nodes.set(id,{hidden:true,disabled:true,textContent:'',classList:{toggle(){}},elements:{name:{value:''},username:{value:''}},reset(){this.elements.name.value='';this.elements.username.value='';}});
    return nodes.get(id);
  };
  const clerk={user:{id:'user_test'},isSignedIn:true,session:{getToken:async()=> 'test-session'}};
  let onChange;
  const requests=[];
  const fetch=async(url,options)=>{
    if(url.includes('?config'))return {ok:true,json:async()=>({publishableKey:'test'})};
    const body=JSON.parse(options.body);requests.push(body);
    if(body.action==='me')return profile||{ok:true,json:async()=>({registered:false})};
    if(body.action==='complete')return {ok:true,json:async()=>complete};
    assert.equal(body.action,'register');
    return register?register(body):{ok:true,json:async()=>({id:'user_test',name:body.name,username:body.username})};
  };
  const source=(await readFile(new URL('../web/player.js',import.meta.url),'utf8')).replace(/^import[^\n]*\n/,'');
  const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
  await new AsyncFunction('document','location','fetch','account','verifiedGoogle',source)(
    {querySelector:node},{search},fetch,
    async(_key,_target,callback)=>{onChange=callback;callback(clerk);},value=>Boolean(value?.isSignedIn)
  );
  return {node,clerk,requests,onChange,submit:()=>node('#register').onsubmit({preventDefault(){},currentTarget:node('#register')})};
}

test('reopening a completed app sign-in shows success without another callback',async()=>{
  const ui=await screen({search:'?app=10000000-0000-4000-8000-000000000001',
    profile:{ok:true,json:async()=>({name:'Player',username:'player'})},complete:{alreadyLinked:true}});
  await flush();await flush();
  assert.match(ui.node('#status').textContent,/Sign-in complete/);
  assert.equal(ui.node('#retry').hidden,true);
  assert.equal(ui.node('#open-app').hidden,true);
  assert.equal(ui.requests.filter(request=>request.action==='complete').length,1);
});

test('registration waits for saved-profile loading and allows editing an existing username',async()=>{
  let resolve;
  const profile=new Promise(r=>{resolve=r;});
  const ui=await screen({profile});
  assert.equal(ui.node('#save').disabled,true);
  await ui.submit();assert.equal(ui.requests.filter(r=>r.action==='register').length,0);
  resolve({ok:true,json:async()=>({id:'user_test',name:'Darsh',username:'darsh_11'})});
  await flush();
  assert.notEqual(ui.node('#register').elements.username.readOnly,true);
  assert.equal(ui.node('#save').textContent,'Save profile');
  assert.match(ui.node('#status').textContent,/teammates find you/);
});

test('new registration shows pending then visible success and preserves the chosen handle',async()=>{
  let resolve;
  const pending=new Promise(r=>{resolve=r;});
  const ui=await screen({register:()=>pending});await flush();
  ui.node('#register').elements.name.value='Darsh';ui.node('#register').elements.username.value='darsh_11';
  const saving=ui.submit();await flush();
  assert.equal(ui.node('#save').textContent,'Continue');
  ui.onChange(ui.clerk);assert.equal(ui.node('#save').disabled,true);
  resolve({ok:true,json:async()=>({id:'user_test',name:'Darsh',username:'darsh_11'})});await saving;
  assert.match(ui.node('#status').textContent,/Profile saved/);
  assert.notEqual(ui.node('#register').elements.username.readOnly,true);
  assert.equal(ui.node('#save').textContent,'Continue');
});

test('profile updates submit the edited username and display API errors beside the button',async()=>{
  const ui=await screen({profile:{ok:true,json:async()=>({id:'user_test',name:'Darsh',username:'darsh_11'})},
    register:async()=>({ok:false,json:async()=>({error:'Please retry later'})})});await flush();
  ui.node('#register').elements.username.value='tampered';
  await ui.submit();
  assert.equal(ui.requests.at(-1).username,'tampered');
  assert.equal(ui.node('#status').textContent,'Please retry later');
  assert.equal(ui.node('#save').disabled,false);
});

test('signing out during registration cannot restore the previous player profile',async()=>{
  let resolve;const pending=new Promise(r=>{resolve=r;});
  const ui=await screen({register:()=>pending});await flush();
  const saving=ui.submit();await flush();
  ui.clerk.isSignedIn=false;ui.clerk.user=null;ui.onChange(ui.clerk);
  resolve({ok:true,json:async()=>({id:'user_test',name:'Darsh',username:'darsh_11'})});await saving;
  assert.equal(ui.node('#register').elements.username.value,'');
  assert.equal(ui.node('#share').hidden,true);
  assert.equal(ui.node('#register').hidden,true);
});
