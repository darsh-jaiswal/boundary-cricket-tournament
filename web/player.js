import {account,verifiedGoogle} from './account.js';
const $=s=>document.querySelector(s);let clerk,profileRevision=0,savedUsername='',loadingProfile=false,busy=false;
const link=new URLSearchParams(location.search).get('link');
const team=new URLSearchParams(location.search).get('team');
const returnTo=new URLSearchParams(location.search).get('returnTo');
const invitationReturn=typeof returnTo==='string'&&/^\/join\.html\?invite=[a-f0-9]{64}$/.test(returnTo)?returnTo:'';
if(team){$('#invitation').hidden=false;$('#invitation').textContent='You have been invited to '+team.slice(0,100)+'. Register below, then send your @username to the team organizer so they can add you.';}
function updateForm(){
  const enabled=verifiedGoogle(clerk)&&!loadingProfile&&!busy;
  $('#save').disabled=!enabled;
  $('#save').textContent=busy?'Saving...':loadingProfile?'Loading your player profile...':savedUsername?'Save display name':'Register player';
  const form=$('#register');
  form.elements.name.disabled=!enabled;
  form.elements.username.disabled=!enabled;
  form.elements.username.readOnly=Boolean(savedUsername);
  $('#username-help').textContent=savedUsername?'Your username is permanent. Your display name can still be updated.':'3-24 characters. Start with a letter; use letters, numbers or underscores. Choose carefully: your username cannot be changed later.';
  $('#terms-row').hidden=Boolean(savedUsername);
  $('#accept-terms').required=!savedUsername;
}
function showPlayer(player){
  if(!player.id)return;
  $('#register').elements.name.value=player.name||'';
  savedUsername=player.username||'';
  $('#register').elements.username.value=savedUsername;
  updateForm();
  $('#player-code').textContent=player.username?'Your username: @'+player.username:'Choose your public username below.';
  $('#share').hidden=!player.username;
  const back=$('#continue-invitation');back.hidden=!(invitationReturn&&player.username);if(!back.hidden)back.href=invitationReturn;
  if(player.username){const text='My Boundary cricket username is @'+player.username+'. Add me to your team on Boundary.';
    $('#share').href='https://wa.me/?text='+encodeURIComponent(text);}
  if(link)$('#approve').disabled=false;
}
if(link){$('#device').hidden=false;$('#link-code').textContent='Device request '+link.slice(0,8).toUpperCase();}
function message(text,error=false){for(const selector of ['#status','#form-status']){const target=$(selector);target.textContent=text;target.classList.toggle('error',error);}}
function actionMessage(text,error=false){message(text,error);$('#form-status').scrollIntoView?.({block:'nearest',behavior:'smooth'});}
async function api(body){const token=await clerk?.session?.getToken();if(!token)throw new Error('Sign in first');
  const response=await fetch('/api/players',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const value=await response.json();if(!response.ok)throw new Error(value.error||'Request failed');return value;}
$('#register').onsubmit=async event=>{
  event.preventDefault();if(busy||loadingProfile)return;
  const revision=++profileRevision,userId=clerk?.user?.id,wasRegistered=Boolean(savedUsername);
  busy=true;updateForm();actionMessage('Saving your player profile...');
  try{
    // Disabled form controls are omitted from FormData; read the editable display name directly.
    const fields=$('#register').elements;
    const player=await api({action:'register',termsVersion:'2026-10-19',name:fields.name.value,username:savedUsername||fields.username.value});
    if(clerk?.user?.id!==userId||profileRevision!==revision)return;
    showPlayer(player);
    actionMessage(wasRegistered?'Display name saved. Your username remains @'+player.username+'.':'Registration complete! Your permanent username is @'+player.username+'. Share it with your team organizer.');
  }catch(error){if(clerk?.user?.id===userId&&profileRevision===revision)actionMessage(error.message,true);}
  finally{if(clerk?.user?.id===userId&&profileRevision===revision){busy=false;updateForm();}}
};
$('#approve').onclick=async()=>{$('#approve').disabled=true;try{await api({action:'approve',link});actionMessage('Android app linked. Return to Boundary; your account status refreshes automatically.');$('#device').hidden=true;}catch(error){message(error.message,true);$('#approve').disabled=false;}};
$('#profile').onclick=()=>clerk.openUserProfile();
$('#sign-out').onclick=async()=>{
  $('#sign-out').disabled=true;
  try{await clerk.signOut({redirectUrl:location.origin+'/signed-out.html'});}
  catch(error){actionMessage('Browser sign-out did not finish. '+error.message,true);$('#sign-out').disabled=false;}
};
globalThis.addEventListener?.('pageshow',event=>{if(event.persisted)location.reload();});
try{const response=await fetch('/api/players?config=1');if(!response.ok)throw new Error('Account service unavailable');const config=await response.json();
  let loadedUser='';
  await account(config.publishableKey,$('#account'),value=>{
    clerk=value;const verified=verifiedGoogle(clerk),id=clerk.user?.id;
    $('#profile').hidden=!clerk.isSignedIn;$('#sign-out').hidden=!clerk.isSignedIn;
    if(!verified){
      profileRevision++;loadedUser='';savedUsername='';busy=false;loadingProfile=false;
      $('#approve').disabled=true;$('#share').hidden=true;$('#continue-invitation').hidden=true;$('#player-code').textContent='';$('#register').reset();
      updateForm();message(clerk.isSignedIn?'Connect Google from your account menu before registering.':'Continue with Google, then choose your cricket name and permanent username.');return;
    }
    if(id===loadedUser){updateForm();return;}
    loadedUser=id;savedUsername='';busy=false;loadingProfile=true;
    $('#approve').disabled=true;$('#share').hidden=true;$('#continue-invitation').hidden=true;$('#player-code').textContent='';$('#register').reset();
    const revision=++profileRevision;updateForm();message('Loading your player profile...');
    api({action:'me'}).then(player=>{
      if(clerk.user?.id!==id||profileRevision!==revision)return;
      loadingProfile=false;showPlayer(player);updateForm();
      message(savedUsername?'You are already registered as @'+savedUsername+'. Your username is permanent. Share it with your team organizer.':'Google account connected. Choose your cricket name and permanent username, then register below.');
    }).catch(error=>{
      if(clerk.user?.id===id&&profileRevision===revision){loadingProfile=false;loadedUser='';updateForm();message(error.message,true);}
    });
  });
}catch(error){message(error.message,true);}
