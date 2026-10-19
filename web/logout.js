import {loadAccount} from './account.js';
const status=document.querySelector('#status'),retry=document.querySelector('#retry');
async function signOut(){
  retry.hidden=true;status.textContent='Finishing sign-out in this browser...';
  try{
    const response=await fetch('/api/players?config=1',{cache:'no-store',signal:AbortSignal.timeout(15000)});
    if(!response.ok)throw new Error('Account service unavailable');
    const config=await response.json(),clerk=await loadAccount(config.publishableKey);
    await clerk.signOut({redirectUrl:location.origin+'/signed-out.html'});
  }catch(error){status.textContent='Browser sign-out did not finish. '+(error.message||'Check your connection and retry.');retry.hidden=false;}
}
retry.onclick=signOut;
globalThis.addEventListener?.('pageshow',event=>{if(event.persisted)location.reload();});
await signOut();
