package com.example.madfinalproject;

import static org.junit.Assert.*;

import com.example.madfinalproject.domain.Career;
import com.example.madfinalproject.domain.Cricket.*;
import com.example.madfinalproject.domain.PerformanceInsights;
import com.example.madfinalproject.domain.PlayerProfile;
import java.util.*;
import org.junit.Test;

public class PerformanceInsightsTest {
  private static Match match(String a, String b, String opener, String partner, String bowler, int overs) {
    Match m = new Match(a, b);
    m.rules.overs = overs;
    m.rules.bowlerOvers = overs;
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

  private static void dots(Match m, int count) { for (int i = 0; i < count; i++) run(m, 0, -1); }

  private static void extra(Match m, Extra extra) {
    Event e = new Event();
    e.extra = extra;
    m.add(e);
  }

  private static void out(Match m, Dismissal how, String incoming) {
    Event e = new Event();
    e.dismissal = how;
    e.incoming = incoming;
    e.fielder = how == Dismissal.CAUGHT ? "Arjun" : "";
    m.add(e);
  }

  private static void chase(Match m, String bowler) {
    m.innings.add(new Innings(m.teamB, m.teamA, "Ali", "Bo", 0));
    bowler(m, bowler);
  }

  private static Career career(String team, String name, Match... matches) {
    return Career.of(Arrays.asList(matches), new Career.Who(name).add(team, name), null);
  }

  private static PlayerProfile profile(String hand, String style) {
    PlayerProfile p = new PlayerProfile();
    p.battingHand = hand;
    p.bowlingStyle = style;
    return p;
  }

  @Test
  public void matchInningsSplitKeepsOnlyThatInningsOfBattingAndBowling() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 1);
    for (int i = 0; i < 6; i++) run(m, 1, -1);
    chase(m, "Ravi");
    dots(m, 6);
    Career ravi = career("Lions", "Ravi", m);
    assertFalse(ravi.partnerships.isEmpty());

    Career first = ravi.inMatchInnings(1);
    assertEquals(1, first.batting.size());
    assertEquals(0, first.bowling.size());
    assertEquals(3, first.faced.size());
    assertEquals(0, first.bowled.size());
    assertEquals(1, first.matches.size());
    assertTrue(first.partnerships.isEmpty());

    Career second = ravi.inMatchInnings(2);
    assertEquals(0, second.batting.size());
    assertEquals(1, second.bowling.size());
    assertEquals(6, second.bowled.size());
    assertEquals(0, second.faced.size());
    assertEquals(1, second.bowlStats().maidens);

    assertSame(ravi, ravi.inMatchInnings(0));
  }

  @Test
  public void battingFormCoversOnlyTheRecentInningsAndTheirZones() {
    Match older = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    run(older, 4, 1);  // point
    run(older, 0, -1);
    run(older, 6, 4);  // long on: 10* off 3
    Match newer = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    run(newer, 2, 2);  // cover
    run(newer, 2, 2);  // cover
    out(newer, Dismissal.CAUGHT, "Sam"); // 4 off 3, caught
    Career ravi = career("Lions", "Ravi", older, newer);

    PerformanceInsights.BatForm last = PerformanceInsights.batForm(ravi, 1);
    assertEquals(1, last.innings);
    assertEquals(4, last.runs);
    assertEquals(3, last.balls);
    assertEquals(1, last.outs);
    assertEquals(1, last.caught);
    assertEquals(0, last.notOuts);
    assertEquals("Cover", last.bestZone);
    assertEquals(4, last.bestZoneRuns);
    assertEquals(4.0, last.average(), 0.001);
    assertEquals(400.0 / 3, last.strikeRate(), 0.001);

    PerformanceInsights.BatForm both = PerformanceInsights.batForm(ravi, 5);
    assertEquals(2, both.innings);
    assertEquals(14, both.runs);
    assertEquals(6, both.balls);
    assertEquals(1, both.notOuts);
    assertEquals(1, both.fours);
    assertEquals(1, both.sixes);
    assertEquals("Long on", both.bestZone);
    assertEquals(6, both.bestZoneRuns);
    assertEquals(14.0, both.average(), 0.001);

    PerformanceInsights.BatForm none = PerformanceInsights.batForm(career("Lions", "Dev", older), 5);
    assertTrue(Double.isNaN(none.average())); // Dev never dismissed and never faced
    assertTrue(Double.isNaN(none.strikeRate()));
  }

  @Test
  public void runsHitByThePartnerOnARunOutAreNotTheirZone() {
    Match m = match("Lions", "Tigers", "Dev", "Ravi", "Kabir", 2);
    Event e = new Event();
    e.bat = 1;
    e.completed = 1;
    e.zone = 5;
    e.dismissal = Dismissal.RUN_OUT;
    e.nonStrikerOut = true; // Ravi run out backing up while Dev hits to midwicket
    e.incoming = "Sam";
    e.fielder = "Arjun";
    m.add(e);
    PerformanceInsights.BatForm ravi = PerformanceInsights.batForm(career("Lions", "Ravi", m), 1);
    assertEquals(1, ravi.outs);
    assertEquals(0, ravi.runs);
    assertEquals("", ravi.bestZone);
  }

  @Test
  public void bowlingFormCountsCatchesBoundariesAndRecordedHands() {
    Match first = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    run(first, 4, 0);
    run(first, 6, 0);
    out(first, Dismissal.CAUGHT, "Sam"); // Ravi caught
    run(first, 1, -1);                   // Sam 1, Dev on strike
    out(first, Dismissal.BOWLED, "Om");  // Dev bowled
    run(first, 0, -1);
    Match second = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    out(second, Dismissal.CAUGHT, "Sam"); // Ravi caught again
    Map<String, PlayerProfile> profiles = new HashMap<>();
    profiles.put(Career.key("Lions", "Ravi"), profile("LHB", ""));
    Career kabir = career("Tigers", "Kabir", first, second);

    PerformanceInsights.BowlForm last = PerformanceInsights.bowlForm(kabir, 1, profiles);
    assertEquals(1, last.innings);
    assertEquals(1, last.wickets);
    assertEquals(1, last.caught);
    assertEquals(0, last.boundaries());
    assertEquals(Integer.valueOf(1), last.wicketsByHand.get("LHB"));

    PerformanceInsights.BowlForm both = PerformanceInsights.bowlForm(kabir, 5, profiles);
    assertEquals(2, both.innings);
    assertEquals(3, both.wickets);
    assertEquals(2, both.caught);
    assertEquals(1, both.fours);
    assertEquals(1, both.sixes);
    assertEquals(Integer.valueOf(2), both.wicketsByHand.get("LHB"));
    assertNull(both.wicketsByHand.get("RHB")); // Dev's hand is not recorded, so his wicket is not guessed
  }

  @Test
  public void leaderPicksTheHighestPositiveValueAndKeepsTheFirstOnATie() {
    Map<String, int[]> rows = new LinkedHashMap<>();
    rows.put("Kabir", new int[] {5, 2, 0});
    rows.put("Zaid", new int[] {9, 0, 0});
    rows.put("Om", new int[] {9, 3, 0});
    assertEquals("Zaid", PerformanceInsights.leader(rows, 0));
    assertEquals("Om", PerformanceInsights.leader(rows, 1));
    assertEquals("", PerformanceInsights.leader(rows, 2));
  }

  @Test
  public void battingPositionsGroupIntoTopMiddleAndLowerOrder() {
    SortedMap<Integer, int[]> positions = new TreeMap<>();
    positions.put(1, new int[] {2, 30, 20});
    positions.put(3, new int[] {1, 10, 8});
    positions.put(4, new int[] {1, 4, 6});
    positions.put(7, new int[] {1, 1, 1});
    positions.put(8, new int[] {1, 2, 3});
    LinkedHashMap<String, int[]> slots = PerformanceInsights.orderSlots(positions);
    assertEquals(Arrays.asList(PerformanceInsights.ORDER_SLOTS), new ArrayList<>(slots.keySet()));
    assertArrayEquals(new int[] {3, 40, 28}, slots.get("Top (1-3)"));
    assertArrayEquals(new int[] {2, 5, 7}, slots.get("Middle (4-7)"));
    assertArrayEquals(new int[] {1, 2, 3}, slots.get("Lower (8+)"));
  }

  @Test
  public void overSlotsCountInningsRunsWicketsAndDotsPerFiveOvers() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 7);
    m.rules.bowlerOvers = 4;
    dots(m, 6);                           // over 1, Kabir
    bowler(m, "Zaid"); dots(m, 6);        // over 2
    bowler(m, "Kabir");
    out(m, Dismissal.BOWLED, "Sam");      // over 3: wicket (a dot) and five singles
    for (int i = 0; i < 5; i++) run(m, 1, -1);
    bowler(m, "Zaid"); dots(m, 6);        // over 4
    bowler(m, "Kabir");
    extra(m, Extra.WIDE); dots(m, 6);     // over 5: a wide, then six dots
    bowler(m, "Zaid"); dots(m, 6);        // over 6
    bowler(m, "Kabir");
    run(m, 4, 3); dots(m, 5);             // over 7
    Match other = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 1);
    dots(other, 6);
    Career kabir = career("Tigers", "Kabir", m, other);

    LinkedHashMap<String, int[]> slots = PerformanceInsights.overSlots(kabir.bowled);
    assertEquals(Arrays.asList("1-5", "6-10"), new ArrayList<>(slots.keySet()));
    assertArrayEquals(new int[] {2, 6, 1, 19}, slots.get("1-5"));
    assertArrayEquals(new int[] {1, 4, 0, 5}, slots.get("6-10"));
  }

  @Test
  public void losePercentUsesOnlyDecidedMatchesWhereTheConditionHeld() {
    Match won = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 1);
    for (int i = 0; i < 6; i++) run(won, 1, -1); // Ravi 3*, Lions 6
    chase(won, "Om");
    dots(won, 6);                                 // Tigers 0: Lions win
    Match lost = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 1);
    out(lost, Dismissal.BOWLED, "Sam");           // Ravi out for a duck
    dots(lost, 5);
    chase(lost, "Om");
    run(lost, 1, -1);                             // Tigers chase 1: Lions lose
    Career ravi = career("Lions", "Ravi", won, lost);
    assertEquals(100.0, PerformanceInsights.losePercent(ravi, b -> b.out && b.runs == 0), 0.001);
    assertEquals(50.0, PerformanceInsights.losePercent(ravi, b -> true), 0.001);
    assertTrue(Double.isNaN(PerformanceInsights.losePercent(ravi, b -> b.runs >= 30)));
  }

  @Test
  public void extrasAndOversUseEachMatchsBallsPerOver() {
    Match six = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 1);
    extra(six, Extra.WIDE);
    extra(six, Extra.NO_BALL);
    dots(six, 6);
    Match five = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 1);
    five.rules.ballsPerOver = 5;
    dots(five, 5);
    Career kabir = career("Tigers", "Kabir", six, five);
    assertArrayEquals(new int[] {1, 1}, PerformanceInsights.extras(kabir.bowled));
    assertEquals(2.0, PerformanceInsights.overs(kabir.bowled), 0.0001);
    assertEquals(2.0, PerformanceInsights.ratio(PerformanceInsights.overs(kabir.bowled), 1), 0.0001);
    assertTrue(Double.isNaN(PerformanceInsights.ratio(2.0, 0)));
  }

  @Test
  public void handSplitGivesShareStrikeRateAndAverageRightHandersFirst() {
    Map<String, int[]> byHand = new LinkedHashMap<>();
    byHand.put("LHB", new int[] {1, 10, 8});
    byHand.put("RHB", new int[] {3, 30, 45});
    LinkedHashMap<String, double[]> split = PerformanceInsights.handSplit(byHand);
    assertEquals(Arrays.asList("RHB", "LHB"), new ArrayList<>(split.keySet()));
    assertArrayEquals(new double[] {75.0, 10.0, 15.0}, split.get("RHB"), 0.001);
    assertArrayEquals(new double[] {25.0, 10.0, 8.0}, split.get("LHB"), 0.001);

    Map<String, int[]> wicketless = new LinkedHashMap<>();
    wicketless.put("LHB", new int[] {0, 12, 20});
    double[] lhb = PerformanceInsights.handSplit(wicketless).get("LHB");
    assertTrue(Double.isNaN(lhb[0]));
    assertTrue(Double.isNaN(lhb[1]));
    assertTrue(Double.isNaN(lhb[2]));
  }

  @Test
  public void topSixShareCountsWicketsByTheDismissedBattersPosition() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    String[] incoming = {"B3", "B4", "B5", "B6", "B7", "B8"};
    for (String next : incoming) out(m, Dismissal.BOWLED, next); // positions 1, 3, 4, 5, 6 and 7
    Career kabir = career("Tigers", "Kabir", m);
    assertEquals(500.0 / 6, PerformanceInsights.topSixPercent(kabir), 0.001);
    assertTrue(Double.isNaN(PerformanceInsights.topSixPercent(career("Lions", "Ravi", m))));
  }

  @Test
  public void outTypesAgainstAStyleUseOnlyRecordedBowlers() {
    Match first = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    out(first, Dismissal.CAUGHT, "Sam");
    Match second = match("Lions", "Tigers", "Ravi", "Dev", "Zaid", 2);
    out(second, Dismissal.BOWLED, "Sam");
    Map<String, PlayerProfile> profiles = new HashMap<>();
    profiles.put(Career.key("Tigers", "Kabir"), profile("", "Right-arm off break"));
    profiles.put(Career.key("Tigers", "Zaid"), profile("", "Right-arm fast"));
    Career ravi = career("Lions", "Ravi", first, second);
    assertEquals(Collections.singletonMap("Caught out", 1), PerformanceInsights.outTypesAgainst(ravi, profiles, "Right-arm off break"));
    assertEquals(Collections.singletonMap("Bowled", 1), PerformanceInsights.outTypesAgainst(ravi, profiles, "Right-arm fast"));
    assertTrue(PerformanceInsights.outTypesAgainst(ravi, profiles, "Left-arm fast").isEmpty());
  }

  @Test
  public void battersFacedAreOrderedByDeliveries() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    dots(m, 3);
    run(m, 1, -1); // Ravi 4 balls, now Dev on strike
    dots(m, 2);    // Dev 2 balls
    assertEquals(Arrays.asList("Ravi", "Dev"), PerformanceInsights.battersFaced(career("Tigers", "Kabir", m).bowled));
  }

  @Test
  public void sharesAndBestYearHandleTiesZeroAndUndefinedValues() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("Bowled", 1);
    counts.put("Caught out", 3);
    LinkedHashMap<String, Double> shares = PerformanceInsights.shares(counts);
    assertEquals(25.0, shares.get("Bowled"), 0.001);
    assertEquals(75.0, shares.get("Caught out"), 0.001);

    SortedMap<Integer, Double> years = new TreeMap<>();
    years.put(2024, 10.0);
    years.put(2025, Double.NaN);
    years.put(2026, 10.0);
    assertEquals(2024, PerformanceInsights.bestYear(years));
    SortedMap<Integer, Double> zero = new TreeMap<>();
    zero.put(2025, 0.0);
    assertEquals(-1, PerformanceInsights.bestYear(zero));
  }

  @Test
  public void yearOnYearKeepsUndefinedAveragesUndefined() {
    Match m = match("Lions", "Tigers", "Ravi", "Dev", "Kabir", 2);
    run(m, 4, -1); // 4*, never dismissed
    Career ravi = career("Lions", "Ravi", m);
    int year = Career.year(m.played());
    assertTrue(Double.isNaN(PerformanceInsights.battingByYear(ravi, "average").get(year)));
    assertEquals(4.0, PerformanceInsights.battingByYear(ravi, "runs").get(year), 0.001);
    assertEquals(400.0, PerformanceInsights.battingByYear(ravi, "strike rate").get(year), 0.001);
    Career kabir = career("Tigers", "Kabir", m);
    assertEquals(0.0, PerformanceInsights.bowlingByYear(kabir, "wickets").get(year), 0.001);
    assertEquals(4.0, PerformanceInsights.bowlingByYear(kabir, "runs").get(year), 0.001);
    assertEquals(24.0, PerformanceInsights.bowlingByYear(kabir, "economy").get(year), 0.001);
  }
}
