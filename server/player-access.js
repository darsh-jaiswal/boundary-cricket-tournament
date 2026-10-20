import { createHash, createHmac } from 'node:crypto';
import { db, clerk, HttpError, verifiedMember } from './auth.js';
import { googleEmail } from './player-identity.js';
const hash=value=>createHash('sha256').update(value).digest('hex');
const emailHash=email=>createHmac('sha256',process.env.CLERK_SECRET_KEY).update(email).digest('hex');
export async function device(req) {
  const token=req.headers.authorization?.replace(/^BoundaryDevice /,'');
  if(typeof token!=='string'||!/^[a-f0-9]{64}$/.test(token))throw new HttpError(401,'Link your player account first');
  const sql=db();const rows=await sql`SELECT * FROM boundary_device_accounts WHERE token_hash=${hash(token)} AND expires>now()`;
  if(!rows[0])throw new HttpError(401,'Player account link expired. Sign in again.');
  return rows[0];
}
export async function verifiedShopMember(req) {
  if (!req.headers.authorization?.startsWith('BoundaryDevice ')) return verifiedMember(req);
  const account=await device(req);
  if(!account.owner_id)throw new HttpError(401,'Sign in to continue');
  const player=await registeredUser(account.owner_id);
  return {id:player.owner_id,name:player.name,email:player.email};
}
export async function registeredUser(userId) {
  const user=await clerk().users.getUser(userId);
  const email=googleEmail(user);
  if(!email)throw new HttpError(403,'This player must sign in with Google before playing');
  const sql=db();const rows=await sql`SELECT owner_id,name,username,email_hash,auth_provider FROM boundary_registered_players WHERE owner_id=${user.id}`;
  if(!rows[0]||rows[0].auth_provider!=='google'||rows[0].email_hash!==emailHash(email))throw new HttpError(403,'Complete Google player registration first');
  return {...rows[0], email};
}
