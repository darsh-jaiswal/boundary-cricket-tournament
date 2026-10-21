import { HttpError } from './auth.js';

export async function validateSquadPlayer(sql, registered, ownerId, playerId) {
  if (!playerId.startsWith('user_practice')) return registered(playerId);
  const rows = await sql`SELECT id,name,team_id FROM boundary_practice_players
    WHERE id=${playerId} AND owner_id=${ownerId}`;
  if (!rows.length) throw new HttpError(403, 'This practice player belongs to another account or was removed.');
  return { ...rows[0], practice: true };
}
