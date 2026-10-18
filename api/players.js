import { guardRequest, rateLimit, clientIp } from '../server/security.js';
import { createHash, createHmac, randomBytes, randomUUID } from 'node:crypto';
import { db, clerk, verifiedMember, bodyOf, uuid, HttpError } from '../server/auth.js';

const hash = value => createHash('sha256').update(value).digest('hex');
const phoneHash = phone => createHmac('sha256',process.env.CLERK_SECRET_KEY).update(phone).digest('hex');
export const normalizePhone = value => {
  if(typeof value!=='string')throw new HttpError(400,'Enter a phone number');
  let phone=value.replace(/[\s()-]/g,'');
  if(/^[6-9][0-9]{9}$/.test(phone))phone='+91'+phone;
  if(!/^\+[1-9][0-9]{7,14}$/.test(phone))throw new HttpError(400,'Use a country code, for example +91 followed by your mobile number');
  return phone;
};
async function device(req) {
  const token=req.headers.authorization?.replace(/^BoundaryDevice /,'');
  if(typeof token!=='string'||!/^[a-f0-9]{64}$/.test(token))throw new HttpError(401,'Link your phone account first');
  const sql=db();const rows=await sql`SELECT * FROM boundary_device_accounts WHERE token_hash=${hash(token)} AND expires>now()`;
  if(!rows[0])throw new HttpError(401,'Phone account link expired. Sign in again.');
  return rows[0];
}
async function registeredUser(userId) {
  const user=await clerk().users.getUser(userId);
  const phones=user.phoneNumbers?.filter(p=>p.verification?.status==='verified')||[];
  if(!phones.length)throw new HttpError(403,'This player must verify a phone number before playing');
  const sql=db();const rows=await sql`SELECT owner_id,name,phone_hash FROM boundary_registered_players WHERE owner_id=${user.id}`;
  if(!rows[0]||!phones.some(phone=>phoneHash(phone.phoneNumber)===rows[0].phone_hash))throw new HttpError(403,'Complete player registration first');
  return rows[0];
}

export default async function handler(req,res) {
  res.setHeader('Cache-Control','no-store');
  try {
    await guardRequest(req,res,'players',6000);
    const sql=db();
    if(req.method==='GET'&&req.query.config==='1')return res.status(200).json({publishableKey:process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY});
    if(req.method==='GET') {
      const link=await device(req);
      if(!link.owner_id)return res.status(200).json({status:'pending'});
      const user=await registeredUser(link.owner_id);
      return res.status(200).json({status:'linked',id:user.owner_id,name:user.name});
    }
    if(req.method!=='POST'){res.setHeader('Allow','GET, POST');throw new HttpError(405,'Method not allowed');}
    const body=bodyOf(req,6000);
    if(body.action==='begin') {
      await rateLimit(res,'players:begin','ip:'+clientIp(req),10,600);
      const ip=hash(clientIp(req));
      const token=randomBytes(32).toString('hex'),id=randomUUID();
      const rows=await sql`INSERT INTO boundary_device_accounts(id,token_hash,ip_hash)
        SELECT ${id},${hash(token)},${ip} WHERE (SELECT count(*) FROM boundary_device_accounts WHERE ip_hash=${ip} AND created>now()-interval '10 minutes')<15 RETURNING id`;
      if(!rows.length)throw new HttpError(429,'Too many linking requests. Please wait a few minutes.');
      return res.status(201).json({id,token,url:'https://boundary-cricket-tournament.vercel.app/player.html?link='+id});
    }
    if(body.action==='lookup'||body.action==='validate') {
      const link=await device(req);if(!link.owner_id)throw new HttpError(401,'Finish linking your phone account');
      await rateLimit(res,'players:lookup','account:'+link.owner_id,20);
      await registeredUser(link.owner_id);
      const limits=await sql`UPDATE boundary_device_accounts SET
        lookup_count=CASE WHEN lookup_window<now()-interval '1 minute' THEN 1 ELSE lookup_count+1 END,
        lookup_window=CASE WHEN lookup_window<now()-interval '1 minute' THEN now() ELSE lookup_window END
        WHERE id=${link.id} AND (lookup_window<now()-interval '1 minute' OR lookup_count<20) RETURNING id`;
      if(!limits.length)throw new HttpError(429,'Please wait a minute before searching for more players');
      if(body.action==='validate') {
        if(!Array.isArray(body.ids)||body.ids.length<4||body.ids.length>30||new Set(body.ids).size!==body.ids.length
            ||body.ids.some(id=>typeof id!=='string'||!/^user_[a-zA-Z0-9]+$/.test(id)))throw new HttpError(400,'Each selected player must have a distinct registered account');
        for(const id of body.ids)await registeredUser(id);
        return res.status(200).json({valid:true});
      }
      const phone=normalizePhone(body.phone);
      const rows=await sql`SELECT owner_id,name FROM boundary_registered_players WHERE phone_hash=${phoneHash(phone)}`;
      if(!rows[0])throw new HttpError(404,'This phone number has not registered as a player. Ask them to register at Boundary Player registration.');
      const user=await registeredUser(rows[0].owner_id);
      return res.status(200).json({id:user.owner_id,name:user.name,verified:true});
    }
    if(['follow','unfollow','connections','profile'].includes(body.action)) {
      const link=await device(req);if(!link.owner_id)throw new HttpError(401,'Finish linking your phone account');
      await rateLimit(res,'players:social','account:'+link.owner_id,30);
      const me=(await registeredUser(link.owner_id)).owner_id;
      if(body.action==='connections') {
        const followers=await sql`SELECT p.owner_id AS id,p.name FROM boundary_player_follows f JOIN boundary_registered_players p ON p.owner_id=f.follower_id
          WHERE f.followee_id=${me} ORDER BY f.created DESC LIMIT 500`;
        const following=await sql`SELECT p.owner_id AS id,p.name FROM boundary_player_follows f JOIN boundary_registered_players p ON p.owner_id=f.followee_id
          WHERE f.follower_id=${me} ORDER BY f.created DESC LIMIT 500`;
        return res.status(200).json({followers,following});
      }
      if(typeof body.id!=='string'||!/^user_[a-zA-Z0-9]+$/.test(body.id))throw new HttpError(400,'That is not a Boundary player code');
      if(body.action==='unfollow') {
        await sql`DELETE FROM boundary_player_follows WHERE follower_id=${me} AND followee_id=${body.id}`;
        return res.status(200).json({id:body.id,following:false});
      }
      if(body.action==='follow') {
        if(body.id===me)throw new HttpError(400,'You cannot follow yourself');
        const rows=await sql`INSERT INTO boundary_player_follows(follower_id,followee_id)
          SELECT ${me},owner_id FROM boundary_registered_players WHERE owner_id=${body.id}
          ON CONFLICT DO NOTHING RETURNING followee_id`;
        if(!rows.length&&!(await sql`SELECT 1 FROM boundary_registered_players WHERE owner_id=${body.id}`).length)
          throw new HttpError(404,'This player has not registered on Boundary yet');
        return res.status(200).json({id:body.id,following:true});
      }
      const rows=await sql`SELECT p.owner_id AS id,p.name,
        (SELECT count(*) FROM boundary_player_follows WHERE followee_id=p.owner_id)::int AS "followerCount",
        (SELECT count(*) FROM boundary_player_follows WHERE follower_id=p.owner_id)::int AS "followingCount",
        EXISTS(SELECT 1 FROM boundary_player_follows WHERE follower_id=${me} AND followee_id=p.owner_id) AS following,
        EXISTS(SELECT 1 FROM boundary_player_follows WHERE follower_id=p.owner_id AND followee_id=${me}) AS "followsYou"
        FROM boundary_registered_players p WHERE p.owner_id=${body.id}`;
      if(!rows[0])throw new HttpError(404,'This player has not registered on Boundary yet');
      return res.status(200).json(rows[0]);
    }
    if(body.action==='revoke') {const link=await device(req);await sql`UPDATE boundary_device_accounts SET expires=now() WHERE id=${link.id}`;return res.status(200).json({signedOut:true});}
    const user=await verifiedMember(req);
    await rateLimit(res,'players:account-write',user.id,15);
    if(body.action==='register') {
      if(body.termsVersion!=='2026-10-18')throw new HttpError(400,'Please accept the current Terms & Conditions before registering');
      if(typeof body.name!=='string'||body.name.trim().length<2||body.name.trim().length>100)
        throw new HttpError(400,'Enter your cricket display name (2–100 characters)');
      await sql`INSERT INTO boundary_registered_players(owner_id,name,phone_hash,terms_version) VALUES(${user.id},${body.name.trim()},${phoneHash(user.phone)},${body.termsVersion})
        ON CONFLICT(owner_id) DO UPDATE SET name=excluded.name,phone_hash=excluded.phone_hash,terms_version=excluded.terms_version`;
      return res.status(200).json({registered:true,id:user.id,name:body.name.trim()});
    }
    if(body.action==='approve') {
      await registeredUser(user.id);
      if(!uuid(body.link))throw new HttpError(400,'Invalid device link');
      const rows=await sql`UPDATE boundary_device_accounts SET owner_id=${user.id},expires=now()+interval '30 days'
        WHERE id=${body.link} AND owner_id IS NULL AND expires>now() RETURNING id`;
      if(!rows.length)throw new HttpError(409,'This device link has expired or was already used. Start again in the Android app.');
      return res.status(200).json({linked:true});
    }
    throw new HttpError(400,'Unknown action');
  }catch(error){if(!error.status)console.error('Player registration failed',error.code||error.name);
    return res.status(error.status||503).json({error:error.status?error.message:'Player registration is unavailable. Please retry.'});}
}
