import { createClerkClient } from '@clerk/backend';
import { neon } from '@neondatabase/serverless';

export const APP_ORIGIN = 'https://boundary-cricket-tournament.vercel.app';
let client, database;
export function db() {
  if (!process.env.DATABASE_URL) throw new Error('Database is not configured');
  return database ??= neon(process.env.DATABASE_URL);
}
export function clerk() {
  return client ??= createClerkClient({ secretKey: process.env.CLERK_SECRET_KEY,
    publishableKey: process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY });
}
export class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
export async function verifiedMember(req) {
  const headers = new Headers();
  for (const key of ['authorization', 'cookie', 'origin'])
    if (typeof req.headers[key] === 'string') headers.set(key, req.headers[key]);
  const state = await clerk().authenticateRequest(new Request(APP_ORIGIN + req.url, { headers }), {
    authorizedParties: [APP_ORIGIN, ...(process.env.VERCEL_ENV !== 'production' ? ['http://localhost:3000'] : [])],
  });
  const auth = state.toAuth();
  if (!auth?.userId) throw new HttpError(401, 'Sign in to continue');
  const user = await clerk().users.getUser(auth.userId);
  const phone = user.phoneNumbers?.find(p => p.id === user.primaryPhoneNumberId && p.verification?.status === 'verified')
    || user.phoneNumbers?.find(p => p.verification?.status === 'verified');
  if (!phone) throw new HttpError(403, 'Add and verify your phone number in your account before continuing');
  return { id: user.id, name: user.fullName || user.username || 'Boundary player', phone: phone.phoneNumber };
}
export function bodyOf(req, limit = 12000) {
  let value;
  try { value = typeof req.body === 'string' ? JSON.parse(req.body) : req.body; }
  catch { throw new HttpError(400, 'Invalid JSON'); }
  if (!value || typeof value !== 'object' || Array.isArray(value) || JSON.stringify(value).length > limit)
    throw new HttpError(400, 'Invalid request');
  return value;
}
export const uuid = value => typeof value === 'string' && /^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(value);
