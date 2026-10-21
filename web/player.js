import {account,verifiedGoogle} from './account.js';
const $=s=>document.querySelector(s),params=new URLSearchParams(location.search);
const app=params.get('app'),legacy=params.get('link'),returnTo=params.get('returnTo');
const invitation=typeof returnTo==='string'&&/^\/join\.html\?invite=[a-f0-9]{64}$/.test(returnTo)?returnTo:'';
let clerk,loadedUser='',revision=0,busy=false,registered=false;
const message=text=>{$('#status').textContent=text;};
async function api(body){const token=await clerk?.session?.getToken();if(!token)throw new Error('Sign in first');
 const response=await fetch('/api/players',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
 const value=await response.json();if(!response.ok)throw new Error(value.error||'Request failed');return value;}
async function finish(){
 const user=clerk?.user?.id,current=revision;
 try{
  if(app){
   $('#register').hidden=true;message('Opening Boundary…');
   const {code,alreadyLinked}=await api({action:'complete',id:app});
   if(current!==revision||user!==clerk?.user?.id)return;
   if(alreadyLinked){
    $('#open-app').hidden=true;$('#retry').hidden=true;
    message('Sign-in complete. Return to the Boundary app to continue.');return;
   }
   const url='intent://signin?id='+encodeURIComponent(app)+'&code='+encodeURIComponent(code)+'#Intent;scheme=boundary;package=com.example.madfinalproject;end';
   $('#open-app').href=url;$('#open-app').hidden=false;location.href=url;
   message('You are signed in. Tap Open Boundary if the app did not open.');
  }else if(invitation){location.replace(invitation);}
  else if(legacy){$('#legacy').hidden=false;message('Profile ready. Return to your Android app to finish sign-in.');}
  else{message('Profile saved. Your teammates can find you by @'+$('#register').elements.username.value+'.');$('#profile-links').hidden=false;}
 }catch(error){if(current===revision){message(error.message);$('#retry').hidden=false;}}
}
$('#retry').onclick=()=>{$('#retry').hidden=true;finish();};
$('#legacy').onclick=async()=>{try{await api({action:'approve',link:legacy});message('Signed in. Return to Boundary.');$('#legacy').hidden=true;}catch(error){message(error.message);}};
$('#register').onsubmit=async event=>{
 event.preventDefault();if(busy||!verifiedGoogle(clerk)||loadedUser!==clerk?.user?.id||$('#register').hidden)return;busy=true;$('#save').disabled=true;message('Saving your profile…');
 const current=revision,fields=event.currentTarget.elements;
 try{const player=await api({action:'register',termsVersion:'2026-10-19',name:fields.name.value,username:fields.username.value});
  if(current!==revision)return;registered=true;fields.username.value=player.username;await finish();
 }catch(error){if(current===revision)message(error.message);}
 finally{if(current===revision){busy=false;$('#save').disabled=false;}}
};
try{
 const response=await fetch('/api/players?config=1');if(!response.ok)throw new Error('Account service unavailable');
 const config=await response.json();
 await account(config.publishableKey,$('#account'),async value=>{
  clerk=value;
  if(!verifiedGoogle(clerk)){revision++;loadedUser='';busy=false;$('#save').disabled=false;$('#register').hidden=true;$('#register').reset();$('#open-app').hidden=true;
   message(clerk.isSignedIn?'Use your Google account to continue.':'Sign in with Google to continue.');
   if(!clerk.isSignedIn&&app)clerk.redirectToSignIn({signInForceRedirectUrl:location.href,signUpForceRedirectUrl:location.href});return;}
  if(loadedUser===clerk.user.id)return;loadedUser=clerk.user.id;busy=false;$('#register').hidden=true;$('#save').disabled=true;const current=++revision;
  try{const player=await api({action:'me'});if(current!==revision)return;
   registered=Boolean(player.username);const form=$('#register');form.elements.name.value=player.name||clerk.user.fullName||'';form.elements.username.value=player.username||'';
   $('#terms-row').hidden=registered;$('#accept-terms').required=!registered;
   if(registered&&app){await finish();return;}
   form.hidden=false;$('#save').disabled=false;$('#heading').textContent=registered?'Edit your player profile':'Choose your cricket identity';
   $('#save').textContent=registered?'Save profile':'Continue';message(registered?'Your username is how teammates find you.':'Choose your display name and a unique username.');
  }catch(error){if(current===revision){loadedUser='';message(error.message);}}
 });
}catch(error){message(error.message);}
