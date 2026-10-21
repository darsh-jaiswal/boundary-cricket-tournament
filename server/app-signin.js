import { randomBytes, createHash } from 'node:crypto';
import { HttpError } from './auth.js';

export async function completeAppSignIn(sql, ownerId, deviceId) {
  const code = randomBytes(32).toString('hex');
  const hash = createHash('sha256').update(code).digest('hex');
  const rows = await sql`INSERT INTO boundary_app_signin_codes(device_id,owner_id,code_hash)
    SELECT id,${ownerId},${hash} FROM boundary_device_accounts
    WHERE id=${deviceId} AND login_version=2 AND owner_id IS NULL AND expires>now()
    ON CONFLICT(device_id) DO UPDATE SET owner_id=excluded.owner_id,code_hash=excluded.code_hash,expires=now()+interval '2 minutes'
    RETURNING device_id`;
  if (rows.length) return { code };
  const linked = await sql`SELECT id FROM boundary_device_accounts
    WHERE id=${deviceId} AND login_version=2 AND owner_id=${ownerId} AND expires>now()`;
  if (linked.length) return { alreadyLinked: true };
  throw new HttpError(409, 'This sign-in expired. Start again in the Boundary app.');
}
