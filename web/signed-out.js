import {loadAccount} from './account.js';
try{
  const response=await fetch('/api/players?config=1',{cache:'no-store',signal:AbortSignal.timeout(15000)});
  if(!response.ok)throw new Error('Account service unavailable');
  const clerk=await loadAccount((await response.json()).publishableKey);
  const status=document.querySelector('#status');
  status.textContent=clerk.isSignedIn?'This browser is still signed in. Use the link below to finish signing out.':'You are signed out of Boundary in this browser.';
  if(clerk.isSignedIn){const link=document.createElement('a');link.href='/logout.html';link.textContent='Finish signing out';status.append(' ',link);}
}catch{document.querySelector('#status').textContent='Could not verify browser sign-out. Check your connection and try again.';}
