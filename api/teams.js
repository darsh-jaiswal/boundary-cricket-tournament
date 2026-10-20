import {createHash,randomBytes,randomUUID} from 'node:crypto';
import {db,verifiedMember,bodyOf,uuid,HttpError,APP_ORIGIN} from '../server/auth.js';
import {device,registeredUser} from '../server/player-access.js';
import {guardRequest,rateLimit,securityError} from '../server/security.js';

const hash=token=>createHash('sha256').update(token).digest('hex');
const validToken=token=>typeof token==='string'&&/^[a-f0-9]{64}$/.test(token);

// Dependencies are injectable for authentication/ownership tests; production uses the real services.
export function teamHandler({database=db,member=verifiedMember,deviceAccount=device,registered=registeredUser,guard=guardRequest,limit=rateLimit}={}) {
  return async(req,res)=>{
    try{
      await guard(req,res,'teams',6000);
      const sql=database();
      if(req.method==='GET') {
        if(!validToken(req.query.invite))throw new HttpError(400,'Invalid team invitation');
        const rows=await sql`SELECT team_name AS "teamName",expires FROM boundary_team_invitations WHERE token_hash=${hash(req.query.invite)} AND expires>now()`;
        if(!rows.length)throw new HttpError(404,'This invitation has expired. Ask the organizer for a new link.');
        return res.status(200).json(rows[0]);
      }
      if(req.method!=='POST'){res.setHeader('Allow','GET, POST');throw new HttpError(405,'Method not allowed');}
      const body=bodyOf(req,6000);
      if(body.action==='join') {
        if(!validToken(body.invite))throw new HttpError(400,'Invalid team invitation');
        const user=await member(req);
        await registered(user.id);
        await limit(res,'teams:join',user.id,10);
        // One statement checks expiry and creates an idempotent request; clients never choose owner/team IDs.
        const rows=await sql`INSERT INTO boundary_team_join_requests(owner_id,team_id,player_id)
          SELECT owner_id,team_id,${user.id} FROM boundary_team_invitations
          WHERE token_hash=${hash(body.invite)} AND expires>now()
          ON CONFLICT(owner_id,team_id,player_id) DO UPDATE SET player_id=excluded.player_id
          RETURNING status`;
        if(!rows.length)throw new HttpError(404,'This invitation has expired. Ask the organizer for a new link.');
        return res.status(200).json(rows[0]);
      }
      if(!['create','requests','accept'].includes(body.action))throw new HttpError(400,'Unknown action');
      if(!uuid(body.teamId))throw new HttpError(400,'Choose a saved team');
      const account=await deviceAccount(req);
      if(!account.owner_id)throw new HttpError(401,'Finish linking your Google player account');
      await registered(account.owner_id);
      await limit(res,'teams:manage',account.owner_id,30);
      if(body.action==='create') {
        if(typeof body.teamName!=='string'||body.teamName.trim().length<1||body.teamName.trim().length>100)throw new HttpError(400,'Enter a team name (1–100 characters)');
        const token=randomBytes(32).toString('hex');
        const rows=await sql`INSERT INTO boundary_team_invitations(id,owner_id,team_id,team_name,token_hash)
          VALUES(${randomUUID()},${account.owner_id},${body.teamId},${body.teamName.trim()},${hash(token)}) RETURNING expires`;
        return res.status(201).json({url:APP_ORIGIN+'/join.html?invite='+token,expiresAt:new Date(rows[0].expires).getTime()});
      }
      if(body.action==='requests') {
        const before=body.before;
        if(before!==undefined&&(!before||typeof before!=='object'||typeof before.created!=='string'
          ||!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3,6}Z$/.test(before.created)||!Number.isFinite(Date.parse(before.created))
          ||typeof before.playerId!=='string'||!/^user_[a-zA-Z0-9]+$/.test(before.playerId)))throw new HttpError(400,'Invalid request page');
        const created=before?.created||null,playerId=before?.playerId||'';
        const rows=await sql`SELECT r.player_id AS id,p.name,p.username,r.status,
          to_char(r.created AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS created FROM boundary_team_join_requests r
          JOIN boundary_registered_players p ON p.owner_id=r.player_id
          WHERE r.owner_id=${account.owner_id} AND r.team_id=${body.teamId}
          AND (${created}::timestamptz IS NULL OR (r.created,r.player_id)<(${created}::timestamptz,${playerId}))
          ORDER BY r.created DESC,r.player_id DESC LIMIT 101`;
        const more=rows.length>100,requests=rows.slice(0,100),last=requests.at(-1);
        return res.status(200).json({requests,next:more?{created:last.created,playerId:last.id}:null});
      }
      if(typeof body.playerId!=='string'||!/^user_[a-zA-Z0-9]+$/.test(body.playerId))throw new HttpError(400,'Invalid player');
      const player=await registered(body.playerId);
      const rows=await sql`UPDATE boundary_team_join_requests SET status='accepted'
        WHERE owner_id=${account.owner_id} AND team_id=${body.teamId} AND player_id=${body.playerId} RETURNING player_id`;
      if(!rows.length)throw new HttpError(404,'No join request found for this team');
      // The organizer imports this receipt into its local squad. Repeated imports are deduplicated by account ID.
      return res.status(200).json({id:player.owner_id,name:player.name,username:player.username,verified:true});
    }catch(error){return securityError(res,error,'Team invitations are temporarily unavailable');}
  };
}
export default teamHandler();
