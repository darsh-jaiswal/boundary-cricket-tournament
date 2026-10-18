import {loadAccount} from './account.js';
try {
  const response=await fetch('/api/players?config=1');
  if(!response.ok)throw new Error('Account service unavailable');
  const config=await response.json();const clerk=await loadAccount(config.publishableKey);
  const requested=new URLSearchParams(location.search).get('returnTo')||'/player.html';
  const target=new URL(requested,location.origin);
  const destination=target.origin===location.origin&&['/player.html','/shop.html','/community.html'].includes(target.pathname)
    ?target.href:location.origin+'/player.html';
  if(new URLSearchParams(location.search).has('complete')) {
    clerk.mountSignUp(document.querySelector('#sign-up'),{forceRedirectUrl:destination});
    document.querySelector('#status').textContent='Complete your Google account setup.';
  } else await clerk.handleRedirectCallback({signInFallbackRedirectUrl:destination,
    signUpFallbackRedirectUrl:destination,signInForceRedirectUrl:destination,signUpForceRedirectUrl:destination,
    signInUrl:'/player.html',signUpUrl:'/oauth-callback.html?complete=1&returnTo='+encodeURIComponent(destination)});
} catch(error){document.querySelector('#status').textContent=error.message||'Sign-in could not finish. Return to player registration and retry.';}
