import {account,verifiedGoogle} from './account.js';
const $=selector=>document.querySelector(selector),invite=new URLSearchParams(location.search).get('invite');
let clerk,busy=false,revision=0;
async function api(path,body){
  const headers=body?{'Content-Type':'application/json',Authorization:'Bearer '+await clerk.session.getToken()}:{};
  const response=await fetch(path,{method:body?'POST':'GET',headers,body:body?JSON.stringify(body):undefined,cache:'no-store',signal:AbortSignal.timeout(15000)});
  const result=await response.json();if(!response.ok)throw new Error(result.error||'Request failed');return result;
}
function message(text,error=false){$('#status').textContent=text;$('#status').classList.toggle('error',error);}
$('#join').onclick=async()=>{
  if(busy||!verifiedGoogle(clerk))return;
  const id=clerk.user.id,attempt=revision;busy=true;$('#join').disabled=true;
  try{
    const result=await api('/api/teams',{action:'join',invite});
    if(attempt!==revision||clerk.user?.id!==id)return;
    $('#result').textContent=result.status==='accepted'?'Your request was accepted. The organizer can add your account to the saved squad.':'Request sent. Ask the organizer to open Join requests in this team and add you.';
    $('#join').textContent='Request sent';
  }catch(error){if(attempt===revision){message(error.message,true);$('#join').disabled=false;}}
  finally{busy=false;}
};
try{
  if(!/^[a-f0-9]{64}$/.test(invite||''))throw new Error('Invalid invitation. Ask the organizer for the full team link.');
  const team=await api('/api/teams?invite='+invite);$('#team-name').textContent='Join '+team.teamName;
  const config=await api('/api/players?config=1');
  await account(config.publishableKey,$('#account'),async value=>{
    clerk=value;const attempt=++revision;busy=false;$('#join').disabled=true;$('#join').textContent='Request to join';$('#result').textContent='';$('#register').hidden=true;
    if(!verifiedGoogle(clerk)){message('Continue with Google to request a place in this team.');return;}
    const id=clerk.user.id;message('Checking your player registration…');
    try{
      const player=await api('/api/players',{action:'me'});
      if(attempt!==revision||clerk.user?.id!==id)return;
      if(!player.username){
        $('#register').href='/player.html?returnTo='+encodeURIComponent('/join.html?invite='+invite);
        $('#register').hidden=false;message('Choose your player name and permanent @username first.');return;
      }
      message('Ready as @'+player.username+'.');$('#join').disabled=false;
    }catch(error){if(attempt===revision)message(error.message,true);}
  });
}catch(error){message(error.message,true);}
