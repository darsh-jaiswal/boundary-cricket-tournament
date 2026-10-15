import { neon } from '@neondatabase/serverless';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
const sql=neon(process.env.DATABASE_URL);
const base='https://boundary-cricket-tournament.vercel.app/api/matches';
const id=randomUUID(), writeKey=randomUUID()+randomUUID();
const snapshot={id,writeKey,revision:1,name:'Verification fixture',battingTeam:'Verification A',runs:4,wickets:0,overs:'0.1',legal:1,remaining:59,target:0,venue:'Automated verification',result:'Live',probability:null,commentary:['0.1 ? Four runs'],updated:Date.now()};
try {
 let r=await fetch(base,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(snapshot)});assert.equal(r.status,200);assert.equal((await r.json()).accepted,true);
 r=await fetch(base+'?id='+id);let data=await r.json();assert.equal(data.runs,4);assert.equal(data.writeKey,undefined);
 r=await fetch(base,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...snapshot,writeKey:randomUUID()+randomUUID(),revision:2,runs:99})});assert.equal(r.status,403);
 r=await fetch(base,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...snapshot,revision:2,runs:6})});assert.equal(r.status,200);
 r=await fetch(base,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(snapshot)});assert.equal(r.status,409);
 r=await fetch(base+'?id='+id);data=await r.json();assert.equal(data.runs,6);
 console.log('PASS: publication, public read, owner authorization, newer revision, stale revision rejection');
} finally {
 await sql`DELETE FROM cricket_matches WHERE id=${id}`;
 console.log('Verification fixtures removed');
}
