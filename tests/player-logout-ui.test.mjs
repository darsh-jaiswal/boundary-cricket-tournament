import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

async function page(signOut){
  const nodes=new Map();const node=id=>{if(!nodes.has(id))nodes.set(id,{textContent:'',hidden:true});return nodes.get(id);};
  let attempts=0;
  const source=(await readFile(new URL('../web/logout.js',import.meta.url),'utf8')).replace(/^import[^\n]*\n/,'');
  const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
  await new AsyncFunction('document','location','fetch','loadAccount',source)(
    {querySelector:node},{origin:'https://boundary.example'},
    async()=>({ok:true,json:async()=>({publishableKey:'test'})}),
    async()=>({signOut:async options=>{attempts++;return signOut(options);}})
  );
  return {node,attempts:()=>attempts};
}
test('browser logout invokes Clerk signOut before redirecting to confirmation',async()=>{
  let target;
  const ui=await page(async options=>{target=options.redirectUrl;});
  assert.equal(ui.attempts(),1);assert.equal(target,'https://boundary.example/signed-out.html');
  assert.equal(ui.node('#retry').hidden,true);
});
test('failed browser logout never claims success and can be retried',async()=>{
  const ui=await page(async()=>{throw new Error('Offline');});
  assert.match(ui.node('#status').textContent,/did not finish.*Offline/);
  assert.equal(ui.node('#retry').hidden,false);
  await ui.node('#retry').onclick();assert.equal(ui.attempts(),2);
});
