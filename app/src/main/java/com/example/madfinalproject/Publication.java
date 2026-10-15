package com.example.madfinalproject;

import android.app.job.*;
import android.content.*;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.json.*;

public final class Publication extends JobService {
  private static final String ENDPOINT =
      "https://boundary-cricket-tournament.vercel.app/api/matches";
  private final Map<JobParameters, Worker> workers = new ConcurrentHashMap<>();

  public static void queue(Context c, Repository db, Cricket.Match m) {
    try {
      Cricket.State s = m.state();
      JSONObject o = new JSONObject();
      o.put("id", m.id);
      o.put("name", m.name);
      o.put("battingTeam", m.current().battingTeam);
      o.put("runs", s.runs);
      o.put("wickets", s.wickets);
      o.put("overs", s.overs(m.rules));
      o.put("legal", s.legal);
      o.put("remaining", Math.max(0, s.maxBalls - s.legal));
      o.put("target", s.target);
      o.put("venue", m.venue);
      o.put("result", m.result());
      o.put("probability", Cricket.probability(s, m.rules));
      o.put("commentary", new JSONArray(s.commentary));
      o.put("revision", m.revision);
      o.put("updated", System.currentTimeMillis());
      String key = c.getSharedPreferences("publication", 0).getString("key-" + m.id, "");
      if (key.isEmpty()) {
        key = UUID.randomUUID().toString() + UUID.randomUUID();
        if (!c.getSharedPreferences("publication", 0).edit().putString("key-" + m.id, key).commit())
          throw new IllegalStateException("Could not save publishing authority");
      }
      o.put("writeKey", key);
      android.database.sqlite.SQLiteDatabase sql = db.getWritableDatabase();
      sql.beginTransaction();
      try {
        db.save(m);
        db.enqueue(m.id, o.toString(), m.revision);
        sql.setTransactionSuccessful();
      } finally {
        sql.endTransaction();
      }
      sync(c, db);
    } catch (JSONException ex) {
      throw new IllegalStateException("Could not queue scoreboard", ex);
    }
  }

  public static void sync(Context c, Repository db) {
    if (c.getSharedPreferences("publication", 0).getBoolean("paused", false)) return;
    JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    scheduler.schedule(
        new JobInfo.Builder(814, new ComponentName(c, Publication.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build());
  }

  public static String status(Context c, Repository db) {
    return db.pending().size()
        + " match snapshots waiting for publication. "
        + c.getSharedPreferences("publication", 0)
            .getString("status", "Scores stay on this phone until publication succeeds.");
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    Worker worker = new Worker(params);
    workers.put(params, worker);
    new Thread(worker, "scoreboard-publisher").start();
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    Worker worker = workers.remove(params);
    if (worker != null) {
      worker.stopped = true;
      HttpURLConnection connection = worker.connection;
      if (connection != null) connection.disconnect();
    }
    return true;
  }

  private final class Worker implements Runnable {
    final JobParameters params;
    volatile boolean stopped;
    volatile HttpURLConnection connection;

    Worker(JobParameters p) {
      params = p;
    }

    public void run() {
      Repository db = new Repository(Publication.this);
      boolean retry = false;
      try {
        for (Map.Entry<String, String> item : db.pending().entrySet()) {
          if (stopped) break;
          HttpURLConnection con = (HttpURLConnection) new URL(ENDPOINT).openConnection();
          connection = con;
          try {
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(12000);
            con.setReadTimeout(12000);
            con.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = con.getOutputStream()) {
              out.write(item.getValue().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            if (stopped) break;
            if ((code >= 200 && code < 300) || code == 409) {
              db.acknowledge(item.getKey(), item.getValue());
              note("Latest publication accepted by the server.");
            } else {
              retry = true;
              note("Server returned " + code + ". Local scores are preserved.");
            }
          } finally {
            con.disconnect();
            connection = null;
          }
        }
      } catch (Exception ex) {
        retry = true;
        if (!stopped) note("Waiting for connection: " + ex.getClass().getSimpleName());
      } finally {
        db.close();
        workers.remove(params);
        if (!stopped) jobFinished(params, retry);
      }
    }

    private void note(String s) {
      getSharedPreferences("publication", 0).edit().putString("status", s).apply();
    }
  }
}
