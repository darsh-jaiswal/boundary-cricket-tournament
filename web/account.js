// Shared hosted Clerk sign-in. Session tokens are requested only for API calls.
export async function account(publishableKey, target, onChange) {
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
  function render() {
    target.replaceChildren();
    if (clerk.isSignedIn) clerk.mountUserButton(target);
    else { const button=document.createElement('button');button.textContent='Sign in / register';button.onclick=()=>clerk.openSignIn();target.append(button); }
    onChange(clerk);
  }
  clerk.addListener(render);render();return clerk;
}
export const verifiedPhone = clerk => Boolean(clerk?.isSignedIn && clerk.user?.phoneNumbers?.some(p=>p.verification?.status==='verified'));
