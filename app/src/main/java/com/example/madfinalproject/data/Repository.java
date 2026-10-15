package com.example.madfinalproject.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.madfinalproject.domain.Cricket;
import java.io.*;
import java.util.*;

/** Transactional SQLite snapshots plus a durable outbox for remote publication. */
public final class Repository extends SQLiteOpenHelper {
  public Repository(Context c) {
    super(c, "boundary.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE records(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload BLOB NOT NULL,updated"
            + " INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE outbox(id TEXT PRIMARY KEY,payload TEXT NOT NULL,revision INTEGER NOT NULL)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int next) {
    throw new IllegalStateException("Unsupported database version");
  }

  public synchronized void save(String id, String kind, Serializable value) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
        out.writeObject(value);
      }
      ContentValues cv = new ContentValues();
      cv.put("id", id);
      cv.put("kind", kind);
      cv.put("payload", bytes.toByteArray());
      cv.put("updated", System.currentTimeMillis());
      if (getWritableDatabase()
              .insertWithOnConflict("records", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
          == -1) throw new IllegalStateException("Device storage could not save the record");
    } catch (IOException ex) {
      throw new IllegalStateException("Could not save " + kind, ex);
    }
  }

  public void save(Cricket.Match m) {
    save(m.id, "match", m);
  }

  public void save(Cricket.Team t) {
    save(t.id, "team", t);
  }

  public void save(Cricket.Tournament t) {
    save(t.id, "tournament", t);
  }

  public <T> List<T> list(String kind, Class<T> type) {
    ArrayList<T> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "records",
                new String[] {"payload"},
                "kind=?",
                new String[] {kind},
                null,
                null,
                "updated DESC")) {
      while (c.moveToNext()) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(c.getBlob(0)))) {
          result.add(type.cast(in.readObject()));
        } catch (Exception ex) {
          throw new IllegalStateException(
              "Stored " + kind + " could not be read. Data has been preserved.", ex);
        }
      }
    }
    return result;
  }

  public List<Cricket.Match> matches() {
    return list("match", Cricket.Match.class);
  }

  public List<Cricket.Team> teams() {
    return list("team", Cricket.Team.class);
  }

  public List<Cricket.Tournament> tournaments() {
    return list("tournament", Cricket.Tournament.class);
  }

  public void enqueue(String id, String json, int revision) {
    ContentValues cv = new ContentValues();
    cv.put("id", id);
    cv.put("payload", json);
    cv.put("revision", revision);
    if (getWritableDatabase()
            .insertWithOnConflict("outbox", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        == -1) throw new IllegalStateException("Device storage could not queue the score");
  }

  public Map<String, String> pending() {
    Map<String, String> items = new LinkedHashMap<>();
    try (Cursor c = getReadableDatabase().rawQuery("SELECT id,payload FROM outbox", null)) {
      while (c.moveToNext()) items.put(c.getString(0), c.getString(1));
    }
    return items;
  }

  public void acknowledge(String id, String payload) {
    getWritableDatabase().delete("outbox", "id=? AND payload=?", new String[] {id, payload});
  }
}
