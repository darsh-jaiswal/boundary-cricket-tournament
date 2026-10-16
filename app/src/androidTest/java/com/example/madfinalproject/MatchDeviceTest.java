package com.example.madfinalproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MatchDeviceTest {
  private Context context;
  private Repository db;
  private Match match;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.getSharedPreferences("official-access", 0).edit().clear().commit();
    OfficialAccess.lock();
    context.getSharedPreferences("publication", 0).edit().putBoolean("paused", true).commit();
    db = new Repository(context);
    Team a = new Team("Device Test A"), b = new Team("Device Test B");
    a.players.add("Rohan");
    a.players.add("Dev");
    a.players.add("Sam");
    b.players.add("Aman");
    b.players.add("Kabir");
    b.players.add("Rahul");
    db.save(a);
    db.save(b);
    match = new Match(a.name, b.name);
    match.innings.add(new Innings(a.name, b.name, "Rohan", "Dev", 0));
    Event bowler = new Event();
    bowler.kind = Kind.BOWLER;
    bowler.player = "Aman";
    match.add(bowler);
    db.save(match);
    context
        .getSharedPreferences("MainActivity", 0)
        .edit()
        .putString("active", match.id)
        .putString("role", "Scorer")
        .commit();
  }

  @After
  public void close() {
    db.getWritableDatabase()
        .delete(
            "records",
            "id=? OR id=? OR id=?",
            new String[] {match.id, findTeam("Device Test A"), findTeam("Device Test B")});
    db.getWritableDatabase().delete("outbox", "id=?", new String[] {match.id});
    db.close();
    context.getSharedPreferences("official-access", 0).edit().clear().commit();
    OfficialAccess.lock();
    context.getSharedPreferences("publication", 0).edit().putBoolean("paused", false).commit();
  }

  private String findTeam(String name) {
    for (Team t : db.teams()) if (t.name.equals(name)) return t.id;
    return "";
  }

  @Test
  public void scoreAndRestartRestoresSQLiteState() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      onView(withText("Score")).perform(click());
      onView(withText("4")).perform(scrollTo(), click());
      onView(withHint("PIN")).perform(typeText("123456"), closeSoftKeyboard());
      onView(withText("Set PIN")).perform(click());
      onView(withText("4")).perform(scrollTo(), click());
      onView(withText("4 / 0")).check(matches(isDisplayed()));
      scenario.recreate();
      onView(withText("Score")).perform(click());
      onView(withText("4 / 0")).check(matches(isDisplayed()));
    }
    Match saved = db.matches().stream().filter(m -> m.id.equals(match.id)).findFirst().get();
    assertEquals(4, saved.state().runs);
    assertEquals(2, saved.current().events.size());
  }

  @Test
  public void databaseRoundTripPreservesFreeHit() {
    Event noBall = new Event();
    noBall.extra = Extra.NO_BALL;
    match.add(noBall);
    db.save(match);
    Match saved = db.matches().stream().filter(m -> m.id.equals(match.id)).findFirst().get();
    assertTrue(saved.state().freeHit);
    assertEquals(0, saved.state().legal);
    assertEquals(1, saved.state().runs);
  }
}
