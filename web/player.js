import {account,verifiedGoogle} from './account.js';
const $=s=>document.querySelector(s);let clerk,profileRevision=0;
const link=new URLSearchParams(location.search).get('link');
const team=new URLSearchParams(location.search).get('team');
if(team){$('#invitation').hidden=false;$('#invitation').textContent='You have been invited to '+team.slice(0,100)+'. Register below, then send your @username to the team organizer so they can add you.';}
function showPlayer(player){
  if(!player.id)return;
  $('#register').elements.name.value=player.name||'';
  $('#register').elements.username.value=player.username||'';
  $('#player-code').textContent=player.username?'Your username: @'+player.username:'Choose your public username below.';
  $('#share').hidden=!player.username;
  if(player.username){const text='My Boundary cricket username is @'+player.username+'. Add me to your team on Boundary.';
    $('#share').href='https://wa.me/?text='+encodeURIComponent(text);}
  if(link)$('#approve').disabled=false;
}
if(link){$('#device').hidden=false;$('#link-code').textContent='Device request '+link.slice(0,8).toUpperCase();}
function message(text,error=false){$('#status').textContent=text;$('#status').classList.toggle('error',error);}
async function api(body){const token=await clerk?.session?.getToken();if(!token)throw new Error('Sign in first');
  const response=await fetch('/api/players',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const value=await response.json();if(!response.ok)throw new Error(value.error||'Request failed');return value;}
$('#register').onsubmit=async event=>{event.preventDefault();const revision=++profileRevision,userId=clerk?.user?.id;$('#save').disabled=true;try{
  const fields=new FormData(event.currentTarget);
  const player=await api({action:'register',termsVersion:'2026-10-19',name:fields.get('name'),username:fields.get('username')});
  if(clerk?.user?.id!==userId||profileRevision!==revision)return;
  showPlayer(player);message('Registered as @'+player.username+'. Send your username to your team organizer.');
}catch(error){if(clerk?.user?.id===userId&&profileRevision===revision)message(error.message,true);}finally{if(clerk?.user?.id===userId&&profileRevision===revision)$('#save').disabled=!verifiedGoogle(clerk);}};
$('#approve').onclick=async()=>{$('#approve').disabled=true;try{await api({action:'approve',link});message('Android app linked. Return to Boundary and tap Check account link.');$('#device').hidden=true;}catch(error){message(error.message,true);$('#approve').disabled=false;}};
$('#profile').onclick=()=>clerk.openUserProfile();
try{const response=await fetch('/api/players?config=1');if(!response.ok)throw new Error('Account service unavailable');const config=await response.json();
  let loadedUser='';
  await account(config.publishableKey,$('#account'),value=>{clerk=value;const verified=verifiedGoogle(clerk);$('#save').disabled=!verified;$('#profile').hidden=!clerk.isSignedIn;
    if(!verified){profileRevision++;loadedUser='';$('#approve').disabled=true;$('#share').hidden=true;$('#player-code').textContent='';$('#register').reset();}
    message(verified?'Google account connected. Choose your cricket name and unique username.':clerk.isSignedIn?'Connect Google from your account menu before registering.':'Continue with Google, then choose your cricket name and username.');
    const id=clerk.user?.id;
    if(verified&&id!==loadedUser){loadedUser=id;const revision=++profileRevision;api({action:'me'}).then(player=>{if(clerk.user?.id===id&&profileRevision===revision)showPlayer(player);}).catch(error=>{if(clerk.user?.id===id&&profileRevision===revision){loadedUser='';message(error.message,true);}});}
  });
}catch(error){message(error.message,true);}
