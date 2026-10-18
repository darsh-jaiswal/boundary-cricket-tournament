package com.example.madfinalproject;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket.*;
import com.example.madfinalproject.domain.PlayerProfile;
import java.util.*;

/**
 * Debug builds only: fills an emulator with simulated matches for checking screens.
 * adb shell am start -n com.example.madfinalproject/.SampleDataSeeder
 */
public class SampleDataSeeder extends Activity {
  private final Random random = new Random(20261018);

  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    seed(this);
    Toast.makeText(this, "Sample data saved", Toast.LENGTH_SHORT).show();
    finish();
  }

  private void seed(Context context) {
    Repository repo = new Repository(context);
    Team shirpur = team(repo, "Shirpur", "Ritul Jaiswal", "Kunal Girase", "Sawan Londhe", "Anand Jagtap", "Shlok Borase",
        "Krishna Nerkar", "Bhushan Salkar", "Panav Agrawal", "Rugved Shirsath", "Sai Patil", "Sandip Deshmukh");
    Team nmims = team(repo, "NMIMS-A", "Tanay Amarnani", "Shaurya Singh", "Dev Mehta", "Vedant Patil", "Aryan Pawar",
        "Sushil Pawar", "Nayan Jain", "Om Dhangar", "Akshay Kadam", "Himanshu Rathod", "Lokesh Marathe");
    Team boundary = team(repo, "Boundary XI", "Ritul J", "Darsh Jaiswal", "Vishwesh", "Kartik Kaushik", "Mehul Shah",
        "Arjun Rao", "Ishaan Gill", "Nikhil Das", "Rohan Iyer", "Pranav Joshi", "Yash Kulkarni");
    Team kkr = team(repo, "KKR", "Rahul Verma", "Sameer Khan", "Aditya Rane", "Varun Nair", "Harsh Patel",
        "Zaid Shaikh", "Kabir Mehta", "Omkar Pawar", "Tejas More", "Siddhant Rao", "Neel Desai");

    long day = 86_400_000L;
    long start = new GregorianCalendar(2025, Calendar.MARCH, 20, 15, 30).getTimeInMillis();
    String[][] schedule = {
        {"Shirpur", "NMIMS-A", "20", "LEATHER", "LIMITED_OVERS", "TURF", "Nmims Ground, Shirpur"},
        {"Boundary XI", "KKR", "15", "TENNIS", "LIMITED_OVERS", "CEMENT", "Kalyan Turf"},
        {"Shirpur", "KKR", "10", "TENNIS", "BOX", "ASTROTURF", "Box Arena, Nashik"},
        {"Boundary XI", "NMIMS-A", "20", "LEATHER", "LIMITED_OVERS", "MATTING", "College Ground"},
        {"Shirpur", "NMIMS-A", "20", "TENNIS", "LIMITED_OVERS", "TURF", "Nmims Ground, Shirpur"},
        {"KKR", "Boundary XI", "20", "TENNIS", "HUNDRED", "TURF", "Shivaji Park"},
        {"NMIMS-A", "Shirpur", "20", "LEATHER", "LIMITED_OVERS", "TURF", "Nmims Ground, Shirpur"},
        {"Boundary XI", "KKR", "10", "TENNIS", "BOX", "ASTROTURF", "Box Arena, Nashik"}};
    Map<String, Team> teams = new HashMap<>();
    for (Team t : Arrays.asList(shirpur, nmims, boundary, kkr)) teams.put(t.name, t);
    long when = start;
    for (String[] s : schedule) {
      when += (40 + random.nextInt(50)) * day;
      if (when > System.currentTimeMillis() - day) when = System.currentTimeMillis() - (2 + random.nextInt(20)) * day;
      Match m = play(teams.get(s[0]), teams.get(s[1]), Integer.parseInt(s[2]), s[4].equals("HUNDRED") ? 5 : s[4].equals("BOX") ? 4 : 6, when);
      m.ballType = s[3]; m.matchType = s[4]; m.pitchType = s[5]; m.venue = s[6];
      repo.save(m);
    }

    String[] hands = {"RHB", "RHB", "RHB", "LHB"};
    for (Team t : teams.values())
      for (int i = 0; i < t.players.size(); i++) {
        PlayerProfile p = new PlayerProfile();
        p.team = t.name; p.name = t.players.get(i);
        p.battingHand = hands[random.nextInt(hands.length)];
        if (i >= 5) p.bowlingStyle = PlayerProfile.BOWLING_STYLES[random.nextInt(PlayerProfile.BOWLING_STYLES.length)];
        ProfileStore.save(repo, p);
      }
    ProfileStore me = new ProfileStore(context);
    me.saveDetails("Ritul Jaiswal", "+91 98765 43210", "ritul@example.com");
    me.claim("Shirpur", "Ritul Jaiswal", true);
    me.claim("Boundary XI", "Ritul J", true);
    PlayerProfile mine = new PlayerProfile();
    mine.battingHand = "RHB"; mine.battingOrder = "Middle-order batter"; mine.bowlingStyle = "Right-arm leg break";
    mine.role = "All-rounder"; mine.city = "Mumbai";
    me.saveAttributes(mine);
    repo.close();
  }

  private Team team(Repository repo, String name, String... players) {
    for (Team t : repo.teams()) if (t.name.equals(name)) return t;
    Team t = new Team(name);
    t.players.addAll(Arrays.asList(players));
    repo.save(t);
    return t;
  }

  private Match play(Team a, Team b, int overs, int ballsPerOver, long when) {
    Match m = new Match(a.name, b.name);
    m.rules.overs = overs;
    m.rules.ballsPerOver = ballsPerOver;
    m.rules.players = 11;
    m.rules.bowlerOvers = Math.max(2, overs / 5 + (overs % 5 == 0 ? 0 : 1));
    m.rosterA.addAll(a.players);
    m.rosterB.addAll(b.players);
    m.tossWinner = a.name;
    m.tossDecision = "BAT";
    long[] clock = {when};
    innings(m, a, b, clock);
    innings(m, b, a, clock);
    m.updated = clock[0];
    return m;
  }

  private void innings(Match m, Team bat, Team bowl, long[] clock) {
    List<String> order = new ArrayList<>(bat.players);
    Collections.shuffle(order.subList(2, order.size()), random);
    m.innings.add(new Innings(bat.name, bowl.name, order.get(0), order.get(1), 0));
    int next = 2;
    List<String> bowlers = bowl.players.subList(5, 11);
    String last = "";
    for (int guard = 0; guard < 600; guard++) {
      State s = m.state();
      if (s.closed) break;
      if (s.bowler.isEmpty()) {
        String pick;
        int tries = 0;
        do { pick = bowlers.get(random.nextInt(bowlers.size())); tries++; }
        while (tries < 50 && (pick.equals(last) || s.bowling.containsKey(pick)
            && s.bowling.get(pick).balls >= m.rules.bowlerOvers * m.rules.ballsPerOver));
        Event e = new Event();
        e.kind = Kind.BOWLER; e.player = pick; e.created = clock[0];
        try { m.add(e); last = pick; } catch (IllegalArgumentException full) { break; }
        continue;
      }
      Event e = new Event();
      clock[0] += 25_000 + random.nextInt(20_000);
      e.created = clock[0];
      double r = random.nextDouble();
      if (r < 0.045 && !s.freeHit) {
        Dismissal[] how = {Dismissal.CAUGHT, Dismissal.CAUGHT, Dismissal.CAUGHT, Dismissal.BOWLED, Dismissal.BOWLED,
            Dismissal.LBW, Dismissal.RUN_OUT, Dismissal.STUMPED};
        e.dismissal = how[random.nextInt(how.length)];
        if (e.dismissal == Dismissal.CAUGHT || e.dismissal == Dismissal.RUN_OUT || e.dismissal == Dismissal.STUMPED)
          e.fielder = bowl.players.get(random.nextInt(bowl.players.size()));
        if (e.dismissal == Dismissal.RUN_OUT) { e.bat = random.nextInt(2); e.completed = e.bat; e.nonStrikerOut = random.nextBoolean(); }
        e.incoming = next < order.size() ? order.get(next) : "";
        next++;
      } else if (r < 0.085) {
        e.extra = Extra.WIDE;
      } else if (r < 0.1) {
        e.extra = Extra.NO_BALL;
        e.bat = random.nextInt(3);
        e.completed = e.bat;
      } else {
        double run = random.nextDouble();
        e.bat = run < 0.36 ? 0 : run < 0.68 ? 1 : run < 0.79 ? 2 : run < 0.81 ? 3 : run < 0.93 ? 4 : 6;
        e.completed = e.bat == 4 || e.bat == 6 ? 0 : e.bat;
        e.boundary = e.bat == 4 || e.bat == 6;
        if (e.bat > 0 && random.nextDouble() < 0.8) e.zone = random.nextInt(8);
      }
      try { m.add(e); } catch (IllegalArgumentException invalid) { if (e.dismissal != Dismissal.NONE) next--; }
    }
  }
}
