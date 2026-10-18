package com.example.madfinalproject;

import static org.junit.Assert.*;

import com.example.madfinalproject.domain.Career;
import com.example.madfinalproject.domain.Cricket.*;
import com.example.madfinalproject.domain.PlayerProfile;
import java.util.*;
import org.junit.Test;

public class CareerTest {
  private static Match match(String a, String b, String opener, String partner, String bowler) {
    Match m = new Match(a, b);
    m.rules.overs = 2;
    m.rules.bowlerOvers = 2;
    m.innings.add(new Innings(a, b, opener, partner, 0));
    bowler(m, bowler);
    return m;
  }

  private static void bowler(Match m, String name) {
    Event e = new Event();
    e.kind = Kind.BOWLER;
    e.player = name;
    m.add(e);
  }

  private static void run(Match m, int runs, int zone) {
    Event e = new Event();
    e.bat = runs;
    e.completed = runs % 2;
    e.boundary = runs == 4 || runs == 6;
    e.zone = zone;
    m.add(e);
  }

  private static void out(Match m, Dismissal how, String incoming, String fielder, boolean nonStriker) {
    Event e = new Event();
    e.dismissal = how;
    e.incoming = incoming;
    e.fielder = fielder;
    e.nonStrikerOut = nonStriker;
    m.add(e);
  }

  @Test
  public void battingAddsUpAcrossTeamIdentitiesAndKeepsOrderPositions() {
    Match first = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    run(first, 4, 1);   // Ravi 4 through point
    run(first, 0, -1);  // dot
    run(first, 1, 4);   // Ravi 1 to long on, Dev on strike
    out(first, Dismissal.CAUGHT, "Sam", "Arjun", false); // Dev caught for 0 (2nd in the order)

    Match second = match("Panthers", "Tigers", "Ravi K", "Om", "Kabir");
    for (int i = 0; i < 3; i++) run(second, 6, 4); // 18 off 3, all long on
    second.ballType = "LEATHER";

    Career.Who ravi = new Career.Who("Ravi").add("Lions", "Ravi").add("Panthers", "Ravi K");
    Career career = Career.of(Arrays.asList(first, second), ravi, null);

    Career.BatStats s = career.batStats();
    assertEquals(2, s.matches);
    assertEquals(2, s.innings);
    assertEquals(2, s.notOuts);
    assertEquals(23, s.runs);
    assertEquals(6, s.balls);
    assertEquals(1, s.fours);
    assertEquals(3, s.sixes);
    assertEquals(1, s.dots);
    assertEquals(18, s.highest);
    assertTrue(s.highestNotOut);
    assertTrue(Double.isNaN(s.average())); // never dismissed

    assertEquals("Long on", Career.bestZone(Career.zoneRuns(career.faced)));
    assertEquals(Integer.valueOf(1), career.preferredPositions().get(0));

    Career.Who dev = new Career.Who("Dev").add("Lions", "Dev");
    Career.BatStats d = Career.of(Arrays.asList(first, second), dev, null).batStats();
    assertEquals(1, d.ducks);
    assertEquals(1, d.innings);
    assertEquals(0, d.notOuts);
  }

  @Test
  public void filterKeepsOnlyMatchingBallType() {
    Match tennis = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    run(tennis, 2, -1);
    tennis.ballType = "TENNIS";
    Match leather = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    run(leather, 4, -1);
    leather.ballType = "LEATHER";
    Career.Filter onlyLeather = new Career.Filter();
    onlyLeather.ballType = "LEATHER";
    Career.Who ravi = new Career.Who("Ravi").add("Lions", "Ravi");
    assertEquals(4, Career.of(Arrays.asList(tennis, leather), ravi, onlyLeather).batStats().runs);
    assertEquals(6, Career.of(Arrays.asList(tennis, leather), ravi, null).batStats().runs);
  }

  @Test
  public void matchesSavedBeforeTheNewFieldsReadAsNotRecorded() {
    Match old = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    old.ballType = null; // what Java serialization leaves for fields added after a match was saved
    old.matchType = null;
    old.pitchType = null;
    assertEquals("", old.ballType());
    Career.Filter any = new Career.Filter();
    assertTrue(any.accepts(old));
    any.ballType = "TENNIS";
    assertFalse(any.accepts(old));
  }

  @Test
  public void bowlingCountsMaidensFiguresOversAndOrderOfWickets() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    for (int i = 0; i < 5; i++) run(m, 0, -1);
    out(m, Dismissal.BOWLED, "Sam", "", false); // maiden over with a wicket of the opener
    bowler(m, "Zaid");
    run(m, 4, 2);
    run(m, 1, -1);

    Career.Who kabir = new Career.Who("Kabir").add("Tigers", "Kabir");
    Career career = Career.of(Collections.singletonList(m), kabir, null);
    Career.BowlStats s = career.bowlStats();
    assertEquals(1, s.innings);
    assertEquals(6, s.balls);
    assertEquals("1.0", s.oversText());
    assertEquals(1, s.maidens);
    assertEquals(1, s.wickets);
    assertEquals(0, s.runs);
    assertEquals(6, s.dots);
    assertEquals("1/0", s.best());
    assertEquals("1.0-1-0-1", career.bowlingForm(1).get(0).figures());
    assertArrayEquals(new int[] {1, 0, 0}, career.wicketsByOrder());
    assertEquals("1-5", career.bestSlot(2));
    assertEquals(Integer.valueOf(1), career.wicketsPerInnings().get(1));
  }

  @Test
  public void aCompleteOverThatConcedesARunIsNotAMaiden() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    for (int i = 0; i < 5; i++) run(m, 0, -1);
    run(m, 1, -1);
    Career kabir = Career.of(Collections.singletonList(m), new Career.Who("Kabir").add("Tigers", "Kabir"), null);
    assertEquals(6, kabir.bowlStats().balls);
    assertEquals(0, kabir.bowlStats().maidens);
    assertEquals(5, kabir.bowlStats().dots);
  }

  @Test
  public void nonStrikerRunOutIsTheNonStrikersDismissalNotTheBowlers() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    out(m, Dismissal.RUN_OUT, "Sam", "Arjun", true); // Dev run out backing up
    Career dev = Career.of(Collections.singletonList(m), new Career.Who("Dev").add("Lions", "Dev"), null);
    assertEquals(1, dev.batStats().innings);
    assertEquals(0, dev.batStats().notOuts);
    assertEquals(Integer.valueOf(1), dev.outTypes().get("Run out"));
    Career ravi = Career.of(Collections.singletonList(m), new Career.Who("Ravi").add("Lions", "Ravi"), null);
    assertEquals(1, ravi.batStats().notOuts);
    Career kabir = Career.of(Collections.singletonList(m), new Career.Who("Kabir").add("Tigers", "Kabir"), null);
    assertEquals(0, kabir.bowlStats().wickets);
    assertTrue(kabir.wicketTypes(null).isEmpty());
    assertArrayEquals(new int[] {0, 0, 0}, kabir.wicketsByOrder());
    Career arjun = Career.of(Collections.singletonList(m), new Career.Who("Arjun").add("Tigers", "Arjun"), null);
    assertEquals(1, arjun.runOuts);
  }

  @Test
  public void partnershipsSplitRunsBetweenTheTwoBatters() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    run(m, 1, -1); // Ravi 1, Dev on strike
    run(m, 4, -1); // Dev 4
    run(m, 1, -1); // Dev 1, Ravi on strike
    out(m, Dismissal.CAUGHT, "Sam", "Arjun", false); // Ravi out, new pair Sam/Dev
    run(m, 2, -1); // Sam 2
    Career ravi = Career.of(Collections.singletonList(m), new Career.Who("Ravi").add("Lions", "Ravi"), null);
    Career.Partnership p = ravi.topPartnerships(5).get(0);
    assertEquals("Dev", p.partner);
    assertEquals(6, p.runs);
    assertEquals(1, p.myRuns);
    assertEquals(5, p.partnerRuns);
    assertEquals(4, p.balls);
    Career dev = Career.of(Collections.singletonList(m), new Career.Who("Dev").add("Lions", "Dev"), null);
    assertEquals(2, dev.partnerships.size());
  }

  @Test
  public void runTypesAndStylesUseRecordedAttributesOnly() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    run(m, 0, -1);
    run(m, 0, -1);
    run(m, 4, 3);
    run(m, 6, 4);
    Career ravi = Career.of(Collections.singletonList(m), new Career.Who("Ravi").add("Lions", "Ravi"), null);
    double[] types = Career.runTypes(ravi.faced);
    assertEquals(50.0, types[0], 0.001);
    assertEquals(25.0, types[4], 0.001);
    assertEquals(25.0, types[5], 0.001);

    Map<String, PlayerProfile> profiles = new HashMap<>();
    assertTrue(ravi.againstStyles(profiles, true).isEmpty()); // style unknown, nothing invented
    PlayerProfile kabir = new PlayerProfile();
    kabir.bowlingStyle = "Right-arm off break";
    profiles.put(Career.key("Tigers", "Kabir"), kabir);
    assertEquals(10, ravi.againstStyles(profiles, true).get("SPIN")[0]);
    assertEquals(10.0 * 100 / 4, ravi.strikeRateFirst(10), 0.001);
  }

  @Test
  public void teamsSummariseResultsForEachTeam() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir");
    m.rules.overs = 1;
    m.rules.bowlerOvers = 1;
    for (int i = 0; i < 6; i++) run(m, 1, -1);
    m.innings.add(new Innings("Tigers", "Lions", "Ali", "Bo", 0));
    bowler(m, "Ravi");
    for (int i = 0; i < 6; i++) run(m, 0, -1);
    Career ravi = Career.of(Collections.singletonList(m), new Career.Who("Ravi").add("Lions", "Ravi"), null);
    List<Career.TeamSummary> teams = ravi.teams();
    assertEquals(1, teams.size());
    assertEquals("Lions", teams.get(0).team);
    assertEquals(1, teams.get(0).won);
    assertEquals(100.0, ravi.winPercent(b -> true), 0.001);
    assertEquals(1, ravi.bowlStats().maidens);
  }
}
