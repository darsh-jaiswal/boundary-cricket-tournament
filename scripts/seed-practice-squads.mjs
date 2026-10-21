import { randomUUID } from 'node:crypto';
import { neon } from '@neondatabase/serverless';
import { createClerkClient } from '@clerk/backend';

const [email, expectedUsername] = process.argv.slice(2);
if (!email || !expectedUsername) throw new Error('Usage: seed-practice-squads.mjs <Google email> <expected username>');
const clerk = createClerkClient({ secretKey: process.env.CLERK_SECRET_KEY });
const users = await clerk.users.getUserList({ emailAddress: [email] });
if (users.data.length !== 1) throw new Error('Expected exactly one account');
const owner = users.data[0].id;
const sql = neon(process.env.DATABASE_URL);
const profiles = await sql`SELECT username FROM boundary_registered_players WHERE owner_id=${owner} AND auth_provider='google'`;
if (profiles[0]?.username !== expectedUsername) throw new Error('Target account username does not match');
const teams = await sql`SELECT DISTINCT ON (lower(team_name)) team_id,team_name
  FROM boundary_team_invitations WHERE owner_id=${owner} AND lower(team_name) IN ('mi','csk')
  ORDER BY lower(team_name),created DESC,id DESC`;
if (teams.length !== 2) throw new Error('Expected both MI and CSK saved teams');
const writes = [];
for (const team of teams) for (let slot = 1; slot <= 3; slot++) {
  const id = 'user_practice' + randomUUID().replaceAll('-', '');
  const name = team.team_name.toUpperCase() + ' Practice ' + slot;
  const username = 'practice_' + team.team_name.toLowerCase() + '_' + slot;
  writes.push(sql`INSERT INTO boundary_practice_players(id,owner_id,team_id,slot,name,username)
    VALUES(${id},${owner},${team.team_id},${slot},${name},${username})
    ON CONFLICT(owner_id,team_id,slot) DO NOTHING`);
}
await sql.transaction(writes);
for (const team of teams) {
  const players = await sql`SELECT name FROM boundary_practice_players WHERE owner_id=${owner} AND team_id=${team.team_id} ORDER BY slot`;
  console.log(JSON.stringify({ team: team.team_name, teamId: team.team_id, players: players.map(p => p.name) }));
}
