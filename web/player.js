import {account,verifiedGoogle} from './account.js';
const $=s=>document.querySelector(s);let clerk;
const link=new URLSearchParams(location.search).get('link');
if(link){$('#device').hidden=false;$('#link-code').textContent='Device request '+link.slice(0,8).toUpperCase();}
function message(text,error=false){$('#status').textContent=text;$('#status').classList.toggle('error',error);}
async function api(body){const token=await clerk?.session?.getToken();if(!token)throw new Error('Sign in first');
  const response=await fetch('/api/players',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const value=await response.json();if(!response.ok)throw new Error(value.error||'Request failed');return value;}
$('#register').onsubmit=async event=>{event.preventDefault();$('#save').disabled=true;try{
  const player=await api({action:'register',termsVersion:'2026-10-19',name:new FormData(event.currentTarget).get('name')});$('#player-code').textContent='Your player code: '+player.id;message('Your player account is registered. Share your Google email or player code with your team.');if(link)$('#approve').disabled=false;
}catch(error){message(error.message,true);}finally{$('#save').disabled=!verifiedGoogle(clerk);}};
$('#approve').onclick=async()=>{$('#approve').disabled=true;try{await api({action:'approve',link});message('Android app linked. Return to Boundary and tap Check account link.');$('#device').hidden=true;}catch(error){message(error.message,true);$('#approve').disabled=false;}};
$('#profile').onclick=()=>clerk.openUserProfile();
try{const response=await fetch('/api/players?config=1');if(!response.ok)throw new Error('Account service unavailable');const config=await response.json();
  await account(config.publishableKey,$('#account'),value=>{clerk=value;const verified=verifiedGoogle(clerk);$('#save').disabled=!verified;$('#profile').hidden=!clerk.isSignedIn;
    message(verified?'Google account connected. Enter your display name to register as a player.':clerk.isSignedIn?'Connect Google from your account menu before registering.':'Continue with Google, then register your cricket display name.');});
}catch(error){message(error.message,true);}
