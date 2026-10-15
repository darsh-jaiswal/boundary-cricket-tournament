package com.example.madfinalproject;

import android.app.job.*;
import android.content.*;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Publication extends JobService {
    private static final String ENDPOINT="https://boundary-cricket-tournament.vercel.app/api/matches";
    public static void queue(Context c,Repository db,Cricket.Match m){try{Cricket.State s=m.state();JSONObject o=new JSONObject();o.put("id",m.id);o.put("name",m.name);o.put("battingTeam",m.current().battingTeam);o.put("runs",s.runs);o.put("wickets",s.wickets);o.put("overs",s.overs(m.rules));o.put("legal",s.legal);o.put("remaining",Math.max(0,s.maxBalls-s.legal));o.put("target",s.target);o.put("venue",m.venue);o.put("result",m.result());o.put("probability",s.target==0?JSONObject.NULL:Cricket.probability(s,m.rules));o.put("commentary",new JSONArray(s.commentary));o.put("revision",m.revision);o.put("updated",System.currentTimeMillis());String key=c.getSharedPreferences("publication",0).getString("key-"+m.id,"");if(key.isEmpty()){key=UUID.randomUUID().toString()+UUID.randomUUID();c.getSharedPreferences("publication",0).edit().putString("key-"+m.id,key).apply();}o.put("writeKey",key);db.enqueue(m.id,o.toString(),m.revision);sync(c,db);}catch(JSONException ex){throw new IllegalStateException("Could not queue scoreboard",ex);}}
    public static void sync(Context c,Repository db){JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);scheduler.schedule(new JobInfo.Builder(814,new ComponentName(c,Publication.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    public static String status(Context c,Repository db){return db.pending().size()+" match snapshots waiting for publication. "+c.getSharedPreferences("publication",0).getString("status","Scores stay on this phone until publication succeeds.");}
    @Override public boolean onStartJob(JobParameters params){new Thread(()->{Repository db=new Repository(this);boolean retry=false;try{for(Map.Entry<String,String> item:db.pending().entrySet()){HttpURLConnection con=(HttpURLConnection)new URL(ENDPOINT).openConnection();try{con.setRequestMethod("POST");con.setDoOutput(true);con.setConnectTimeout(12000);con.setReadTimeout(12000);con.setRequestProperty("Content-Type","application/json");try(OutputStream out=con.getOutputStream()){out.write(item.getValue().getBytes(StandardCharsets.UTF_8));}int code=con.getResponseCode();if(code>=200&&code<300){db.acknowledge(item.getKey(),item.getValue());getSharedPreferences("publication",0).edit().putString("status","Latest publication accepted by the server.").apply();}else{retry=true;getSharedPreferences("publication",0).edit().putString("status","Server returned "+code+". Local scores are preserved.").apply();}}finally{con.disconnect();}}}catch(Exception ex){retry=true;getSharedPreferences("publication",0).edit().putString("status","Waiting for connection: "+ex.getClass().getSimpleName()).apply();}finally{db.close();jobFinished(params,retry);}},"scoreboard-publisher").start();return true;}
    @Override public boolean onStopJob(JobParameters params){return true;}
}
