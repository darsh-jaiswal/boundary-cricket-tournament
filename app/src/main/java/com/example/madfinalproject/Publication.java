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
  private static final Object UPLOAD_LOCK = new Object();
  private static final java.util.concurrent.atomic.AtomicBoolean foregroundRunning =
      new java.util.concurrent.atomic.AtomicBoolean();

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
      o.put("phase", m.activeIndex() < 2 ? "MAIN MATCH"
          : "SUPER OVER " + (m.activeIndex() / 2));
      o.put("probability", Cricket.probability(s, m.rules));
      o.put("commentary", new JSONArray(s.commentary));
      o.put("revision", m.revision);
      o.put("tournament", m.tournament);
      for (Cricket.Tournament t : db.tournaments())
        if (t.id.equals(m.tournament)) o.put("tournamentName", t.name);
      JSONArray cards = new JSONArray();
      for (int inningsIndex = 0; inningsIndex < m.innings.size(); inningsIndex++) {
        Cricket.Innings innings = m.innings.get(inningsIndex);
        Cricket.State state = m.stateAt(inningsIndex);
        JSONObject card = new JSONObject();
        card.put("team", innings.battingTeam);
        card.put("phase", inningsIndex < 2 ? "MAIN MATCH"
            : "SUPER OVER " + (inningsIndex / 2));
        card.put("runs", state.runs);
        card.put("wickets", state.wickets);
        card.put("overs", state.overs(m.rules));
        JSONArray batting = new JSONArray();
        for (Map.Entry<String, Cricket.Batting> entry : state.batting.entrySet()) {
          Cricket.Batting b = entry.getValue();
          JSONObject batter = new JSONObject();
          batter.put("player", entry.getKey());
          batter.put("runs", b.runs);
          batter.put("balls", b.balls);
          batter.put("fours", b.fours);
          batter.put("sixes", b.sixes);
          batter.put("out", b.out);
          batting.put(batter);
        }
        card.put("batting", batting);
        JSONArray bowling = new JSONArray();
        for (Map.Entry<String, Cricket.Bowling> entry : state.bowling.entrySet()) {
          Cricket.Bowling b = entry.getValue();
          JSONObject bowler = new JSONObject();
          bowler.put("player", entry.getKey());
          bowler.put(
              "overs", b.balls / m.rules.ballsPerOver + "." + b.balls % m.rules.ballsPerOver);
          bowler.put("runs", b.runs);
          bowler.put("wickets", b.wickets);
          bowling.put(bowler);
        }
        card.put("bowling", bowling);
        cards.put(card);
      }
      o.put("scorecards", cards);
      o.put("updated", System.currentTimeMillis());
      String key = c.getSharedPreferences("publication", 0).getString("key-" + m.id, "");
      if (key.isEmpty()) {
        key = UUID.randomUUID().toString() + UUID.randomUUID();
        if (!c.getSharedPreferences("publication", 0).edit().putString("key-" + m.id, key).commit())
          throw new IllegalStateException("Could not save publishing authority");
      }
      o.put("writeKey", key);
      o.put("privateJournal", com.example.madfinalproject.data.JournalCodec.encode(m, key));
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
    publishNow(c);
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

  private static class Transfer {
    volatile boolean stopped;
    volatile HttpURLConnection connection;
  }

  private static boolean upload(Context context, Repository db, Transfer transfer) {
    boolean retry = false;
    try {
      for (Map.Entry<String, String> item : db.pending().entrySet()) {
        if (transfer.stopped) break;
        HttpURLConnection con = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        transfer.connection = con;
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
          if (transfer.stopped) break;
          if (code >= 200 && code < 300) {
            db.acknowledge(item.getKey(), item.getValue());
            note(context, "Latest publication accepted by the server.");
          } else if (code == 409) {
            note(
                context,
                "Score revision conflict. Local journal retained. Review cloud recovery before"
                    + " publishing again.");
          } else {
            retry = true;
            note(context, "Server returned " + code + ". Local scores are preserved.");
          }
        } finally {
          con.disconnect();
          transfer.connection = null;
        }
      }
    } catch (Exception e) {
      retry = true;
      if (!transfer.stopped)
        note(context, "Waiting for connection: " + e.getClass().getSimpleName());
    }
    return retry;
  }

  private static void note(Context c, String message) {
    c.getSharedPreferences("publication", 0).edit().putString("status", message).apply();
  }

  private static void publishNow(Context c) {
    if (!foregroundRunning.compareAndSet(false, true)) return;
    Context context = c.getApplicationContext();
    new Thread(
            () -> {
              try (Repository db = new Repository(context)) {
                synchronized (UPLOAD_LOCK) {
                  upload(context, db, new Transfer());
                }
              } finally {
                foregroundRunning.set(false);
              }
            },
            "live-score-publisher")
        .start();
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    Worker worker = new Worker(params);
    workers.put(params, worker);
    new Thread(worker, "scoreboard-retry").start();
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

  private final class Worker extends Transfer implements Runnable {
    final JobParameters params;

    Worker(JobParameters p) {
      params = p;
    }

    public void run() {
      boolean retry = false;
      try (Repository db = new Repository(Publication.this)) {
        synchronized (UPLOAD_LOCK) {
          if (!stopped) retry = upload(Publication.this, db, this);
        }
      } finally {
        workers.remove(params);
        if (!stopped) jobFinished(params, retry);
      }
    }
  }
}
