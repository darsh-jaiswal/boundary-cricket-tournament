import { HttpError, APP_ORIGIN } from './auth.js';

// Resolve the portal from this Clerk instance, not from request input.
let portal;
export async function signInPortal(returnPath) {
  if (!portal) {
    const key = process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY || '';
    const domain = Buffer.from(key.split('_')[2] || '', 'base64').toString().replace(/\$$/, '');
    if (!/^[a-z0-9.-]+$/i.test(domain)) throw new HttpError(503, 'Sign-in is not configured');
    const response = await fetch(`https://${domain}/v1/environment`, {signal: AbortSignal.timeout(8000)});
    if (!response.ok) throw new HttpError(503, 'Sign-in is temporarily unavailable');
    const config = await response.json();
    const url = new URL(config.display_config.sign_in_url);
    if (url.protocol !== 'https:') throw new HttpError(503, 'Invalid sign-in configuration');
    portal = url.href;
  }
  const url = new URL(portal);
  const destination = APP_ORIGIN + returnPath;
  url.searchParams.set('redirect_url', destination);
  url.searchParams.set('sign_in_force_redirect_url', destination);
  url.searchParams.set('sign_up_force_redirect_url', destination);
  return url.href;
}
