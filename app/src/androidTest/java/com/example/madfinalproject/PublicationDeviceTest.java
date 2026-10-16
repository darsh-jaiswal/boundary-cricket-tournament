package com.example.madfinalproject;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PublicationDeviceTest {
  @Test
  public void queuedDeviceScoreReachesPublicSpectatorAPI() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Repository db = new Repository(context);
    Match m = new Match("Verification A", "Verification B");
    m.innings.add(new Innings(m.teamA, m.teamB, "Rohan", "Dev", 0));
    Event b = new Event();
    b.kind = Kind.BOWLER;
    b.player = "Aman";
    m.add(b);
    Event ball = new Event();
    ball.bat = 4;
    ball.boundary = true;
    m.add(ball);
    context.getSharedPreferences("publication", 0).edit().putBoolean("paused", true).commit();
    Publication.queue(context, db, m);
    assertTrue(db.pending().containsKey(m.id));
    context.getSharedPreferences("publication", 0).edit().putBoolean("paused", false).commit();
    String key = context.getSharedPreferences("publication", 0).getString("key-" + m.id, "");
    try {
      Publication.sync(context, db);
      try (android.os.ParcelFileDescriptor ignored =
          InstrumentationRegistry.getInstrumentation()
              .getUiAutomation()
              .executeShellCommand(
                  "cmd jobscheduler run -f " + context.getPackageName() + " 814")) {
        Thread.sleep(500);
      }
      JSONObject published = null;
      for (int i = 0; i < 30; i++) {
        HttpURLConnection c =
            (HttpURLConnection)
                new URL("https://boundary-cricket-tournament.vercel.app/api/matches?id=" + m.id)
                    .openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        try {
          if (c.getResponseCode() == 200) {
            String body = read(c.getInputStream());
            if (!body.equals("null")) {
              published = new JSONObject(body);
              break;
            }
          }
        } finally {
          c.disconnect();
        }
        Thread.sleep(500);
      }
      assertNotNull("Device outbox was not published", published);
      assertEquals(4, published.getInt("runs"));
      assertFalse(published.has("writeKey"));
      assertFalse(published.has("privateJournal"));
      HttpURLConnection recovery =
          (HttpURLConnection)
              new URL(
                      "https://boundary-cricket-tournament.vercel.app/api/matches?id="
                          + m.id
                          + "&journal=1")
                  .openConnection();
      try {
        recovery.setRequestProperty("Authorization", "Bearer " + key);
        recovery.setConnectTimeout(10000);
        recovery.setReadTimeout(10000);
        assertEquals(200, recovery.getResponseCode());
        JSONObject response = new JSONObject(read(recovery.getInputStream()));
        Match restored =
            com.example.madfinalproject.data.JournalCodec.decode(
                response.getString("journal"), key);
        assertEquals(m.id, restored.id);
        assertEquals(4, restored.state().runs);
        assertEquals(2, restored.current().events.size());
      } finally {
        recovery.disconnect();
      }
      assertFalse(db.pending().containsKey(m.id));
    } finally {
      HttpURLConnection c =
          (HttpURLConnection)
              new URL("https://boundary-cricket-tournament.vercel.app/api/matches")
                  .openConnection();
      try {
        c.setRequestMethod("DELETE");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        body.put("id", m.id);
        body.put("writeKey", key);
        try (OutputStream out = c.getOutputStream()) {
          out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("Verification record cleanup failed", 200, c.getResponseCode());
      } finally {
        c.disconnect();
        db.getWritableDatabase().delete("records", "id=?", new String[] {m.id});
        db.getWritableDatabase().delete("outbox", "id=?", new String[] {m.id});
        db.close();
      }
    }
  }

  private String read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] bytes = new byte[8192];
    int n;
    while ((n = in.read(bytes)) > 0) out.write(bytes, 0, n);
    return out.toString("UTF-8");
  }
}
