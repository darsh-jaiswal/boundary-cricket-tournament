// Shared hosted Clerk sign-in. Session tokens are requested only for API calls.
export async function loadAccount(publishableKey) {
  if (!publishableKey) throw new Error('Account service is not configured');
  const domain = atob(publishableKey.split('_')[2]).replace(/\$$/, '');
  if (!/^[a-z0-9.-]+$/i.test(domain)) throw new Error('Invalid account configuration');
  const load = (url, key) => new Promise((resolve, reject) => {
    const script = document.createElement('script'); script.src=url; script.crossOrigin='anonymous';
    if (key) script.setAttribute('data-clerk-publishable-key',key);
    script.onload=resolve;script.onerror=()=>reject(new Error('Account service could not load'));document.head.append(script);
  });
  await load(`https://${domain}/npm/@clerk/ui@1/dist/ui.browser.js`);
  await load(`https://${domain}/npm/@clerk/clerk-js@6/dist/clerk.browser.js`,publishableKey);
  const clerk=window.Clerk;await clerk.load({ui:{ClerkUI:window.__internal_ClerkUICtor}});
  return clerk;
}
export async function account(publishableKey, target, onChange) {
  const clerk=await loadAccount(publishableKey);
  function render() {
    target.replaceChildren();
    if (clerk.isSignedIn) clerk.mountUserButton(target);
    else {
      const button=document.createElement('button');button.textContent='Continue with Google';
      button.onclick=async()=>{
        button.disabled=true;
        try {
          const destination=location.pathname+location.search;
          await clerk.client.signIn.authenticateWithRedirect({strategy:'oauth_google',
            redirectUrl:location.origin+'/oauth-callback.html?returnTo='+encodeURIComponent(destination),
            redirectUrlComplete:location.href});
        } catch(error) {button.disabled=false;button.textContent='Retry Google sign-in';
          const message=document.createElement('p');message.setAttribute('role','alert');message.textContent=error.errors?.[0]?.longMessage||error.message||'Google sign-in could not start';target.append(message);}
      };
      target.append(button);
    }
    onChange(clerk);
  }
  clerk.addListener(render);render();return clerk;
}
export const verifiedGoogle = clerk => Boolean(clerk?.isSignedIn && clerk.user?.externalAccounts?.some(account =>
  ['google','oauth_google'].includes(account.provider) && account.verification?.status==='verified'
  && clerk.user.emailAddresses?.some(email => email.verification?.status==='verified'
    && email.emailAddress.toLowerCase()===account.emailAddress?.toLowerCase())));
