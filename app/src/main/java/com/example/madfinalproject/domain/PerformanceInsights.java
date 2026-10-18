package com.example.madfinalproject.domain;

import com.example.madfinalproject.domain.Career.*;
import com.example.madfinalproject.domain.Cricket.Dismissal;
import com.example.madfinalproject.domain.Cricket.Extra;
import java.util.*;
import java.util.function.Predicate;

/** Numbers behind the My Performance screen that Career does not give directly. Undefined values are NaN. */
public final class PerformanceInsights {
  private PerformanceInsights() {}

  public static final String[] ORDER_SLOTS = {"Top (1-3)", "Middle (4-7)", "Lower (8+)"};

  /** True when the person was on strike for the delivery, not their partner (as on a non-striker run out). */
  public static boolean struck(Ball b) { return b.row.name.equals(b.batter); }

  public static double ratio(double value, double per) { return per == 0 ? Double.NaN : value / per; }

  private static String inningsKey(MatchRow row, int index) { return row.match.id + "\u0000" + row.team + "\u0000" + index; }

  // ---------------------------------------------------------------- recent form

  public static final class BatForm {
    public int innings, runs, balls, outs, caught, notOuts, fours, sixes, bestZoneRuns;
    public String bestZone = "";
    public double strikeRate() { return ratio(runs * 100.0, balls); }
    public double average() { return ratio(runs, outs); }
  }

  /** Totals over the {@code count} most recent batting innings, with the zone they scored most in. */
  public static BatForm batForm(Career c, int count) {
    BatForm f = new BatForm();
    Set<String> keys = new HashSet<>();
    for (BatInnings b : c.battingForm(count)) {
      f.innings++;
      f.runs += b.runs; f.balls += b.balls; f.fours += b.fours; f.sixes += b.sixes;
      if (!b.out) f.notOuts++;
      else { f.outs++; if (Dismissal.CAUGHT.name().equals(b.dismissal)) f.caught++; }
      keys.add(inningsKey(b.row, b.index));
    }
    List<Ball> balls = new ArrayList<>();
    for (Ball b : c.faced) if (struck(b) && keys.contains(inningsKey(b.row, b.index))) balls.add(b);
    int[] zones = Career.zoneRuns(balls);
    f.bestZone = Career.bestZone(zones);
    if (!f.bestZone.isEmpty()) f.bestZoneRuns = zones[Arrays.asList(Career.ZONES).indexOf(f.bestZone)];
    return f;
  }

  public static final class BowlForm {
    public int innings, wickets, caught, fours, sixes;
    /** Batting hand -> wickets, only for batters whose hand is recorded. */
    public final Map<String, Integer> wicketsByHand = new LinkedHashMap<>();
    public int boundaries() { return fours + sixes; }
  }

  /** Totals over the {@code count} most recent bowling innings. */
  public static BowlForm bowlForm(Career c, int count, Map<String, PlayerProfile> profiles) {
    BowlForm f = new BowlForm();
    Set<String> keys = new HashSet<>();
    for (BowlInnings b : c.bowlingForm(count)) {
      f.innings++;
      f.wickets += b.wickets; f.fours += b.fours; f.sixes += b.sixes;
      keys.add(inningsKey(b.row, b.index));
    }
    for (Ball b : c.bowled) {
      if (!b.bowlerWicket() || !keys.contains(inningsKey(b.row, b.index))) continue;
      if (b.dismissal == Dismissal.CAUGHT) f.caught++;
      PlayerProfile p = profiles == null ? null : profiles.get(Career.key(b.row.opponent, b.batter));
      if (p != null && !p.battingHand.isEmpty()) f.wicketsByHand.merge(p.battingHand, 1, Integer::sum);
    }
    return f;
  }

  // ---------------------------------------------------------------- rankings and groupings

  /** Key with the highest value in {@code column}, or "" when none is above zero. The first one wins a tie. */
  public static String leader(Map<String, int[]> rows, int column) {
    String best = "";
    int value = 0;
    for (Map.Entry<String, int[]> e : rows.entrySet())
      if (e.getValue()[column] > value) { best = e.getKey(); value = e.getValue()[column]; }
    return best;
  }

  /** Batting positions grouped into top, middle and lower order: slot -> {innings, runs, balls}. */
  public static LinkedHashMap<String, int[]> orderSlots(SortedMap<Integer, int[]> positions) {
    int[][] sums = new int[3][3];
    for (Map.Entry<Integer, int[]> e : positions.entrySet()) {
      int slot = e.getKey() <= 3 ? 0 : e.getKey() <= 7 ? 1 : 2;
      for (int i = 0; i < 3; i++) sums[slot][i] += e.getValue()[i];
    }
    LinkedHashMap<String, int[]> slots = new LinkedHashMap<>();
    for (int s = 0; s < 3; s++) slots.put(ORDER_SLOTS[s], sums[s]);
    return slots;
  }

  /** Deliveries grouped into five-over slots ("1-5", "6-10", ...): slot -> {innings bowled in it, runs, wickets, dots}. */
  public static LinkedHashMap<String, int[]> overSlots(List<Ball> bowled) {
    TreeMap<Integer, int[]> slots = new TreeMap<>();
    Map<Integer, Set<String>> innings = new HashMap<>();
    for (Ball b : bowled) {
      int slot = (b.over - 1) / 5;
      int[] s = slots.computeIfAbsent(slot, k -> new int[4]);
      if (innings.computeIfAbsent(slot, k -> new HashSet<>()).add(inningsKey(b.row, b.index))) s[0]++;
      s[1] += b.bowlerRuns;
      if (b.bowlerWicket()) s[2]++;
      if (b.legal && b.bowlerRuns == 0) s[3]++;
    }
    LinkedHashMap<String, int[]> labelled = new LinkedHashMap<>();
    for (Map.Entry<Integer, int[]> e : slots.entrySet())
      labelled.put((e.getKey() * 5 + 1) + "-" + (e.getKey() * 5 + 5), e.getValue());
    return labelled;
  }

  /** Each count as a percent of their total, in the same order. */
  public static LinkedHashMap<String, Double> shares(Map<String, Integer> counts) {
    int total = 0;
    for (int v : counts.values()) total += v;
    LinkedHashMap<String, Double> shares = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : counts.entrySet()) shares.put(e.getKey(), ratio(e.getValue() * 100.0, total));
    return shares;
  }

  /** Year with the highest value above zero, or -1. The earlier year wins a tie; NaN never wins. */
  public static int bestYear(SortedMap<Integer, Double> years) {
    int best = -1;
    double value = 0;
    for (Map.Entry<Integer, Double> e : years.entrySet())
      if (e.getValue() > value) { best = e.getKey(); value = e.getValue(); }
    return best;
  }

  // ---------------------------------------------------------------- results

  /** Percent of decided matches lost when the condition held on a batting innings; NaN when it never held. */
  public static double losePercent(Career c, Predicate<BatInnings> condition) {
    int held = 0, lost = 0;
    for (BatInnings b : c.batting) {
      if (!condition.test(b) || !b.row.won && !b.row.lost) continue;
      held++;
      if (b.row.lost) lost++;
    }
    return ratio(lost * 100.0, held);
  }

  // ---------------------------------------------------------------- bowling detail

  /** {wides, no-balls} among these deliveries. */
  public static int[] extras(List<Ball> balls) {
    int[] e = new int[2];
    for (Ball b : balls) { if (b.extra == Extra.WIDE) e[0]++; if (b.extra == Extra.NO_BALL) e[1]++; }
    return e;
  }

  /** True overs from the legal deliveries in the list, each counted at its own match's balls per over. */
  public static double overs(List<Ball> balls) {
    double overs = 0;
    for (Ball b : balls) if (b.legal) overs += 1.0 / b.row.match.rules.ballsPerOver;
    return overs;
  }

  /** Per recorded batting hand (RHB first): {share of wickets %, balls per wicket, runs per wicket}. */
  public static LinkedHashMap<String, double[]> handSplit(Map<String, int[]> byHand) {
    int total = 0;
    for (int[] h : byHand.values()) total += h[0];
    LinkedHashMap<String, double[]> split = new LinkedHashMap<>();
    for (String hand : PlayerProfile.BATTING_HANDS) {
      int[] h = byHand.get(hand);
      if (h == null) continue;
      split.put(hand, new double[] {ratio(h[0] * 100.0, total), ratio(h[1], h[0]), ratio(h[2], h[0])});
    }
    return split;
  }

  /** Percent of the bowler's wickets (with a known position) that were batters 1 to 6; NaN without any. */
  public static double topSixPercent(Career c) {
    int top = 0, total = 0;
    for (Map.Entry<Integer, Integer> e : c.wicketsByPosition().entrySet()) {
      total += e.getValue();
      if (e.getKey() <= 6) top += e.getValue();
    }
    return ratio(top * 100.0, total);
  }

  /** Dismissal type -> count, for dismissals while facing bowlers of one recorded bowling style. */
  public static Map<String, Integer> outTypesAgainst(Career c, Map<String, PlayerProfile> profiles, String style) {
    Map<String, Integer> types = new LinkedHashMap<>();
    for (Ball b : c.faced) {
      if (!b.batterOut) continue;
      PlayerProfile p = profiles.get(Career.key(b.row.opponent, b.bowler));
      if (p != null && style.equals(p.bowlingStyle)) types.merge(Career.dismissalLabel(b.dismissal.name()), 1, Integer::sum);
    }
    return types;
  }

  /** Batters bowled to, most deliveries first. */
  public static List<String> battersFaced(List<Ball> bowled) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Ball b : bowled) if (b.batter != null && !b.batter.isEmpty()) counts.merge(b.batter, 1, Integer::sum);
    List<String> names = new ArrayList<>(counts.keySet());
    names.sort((a, b) -> counts.get(b) - counts.get(a));
    return names;
  }

  // ---------------------------------------------------------------- year on year

  /** Year -> runs, innings, average or strike rate; undefined averages and strike rates stay NaN. */
  public static SortedMap<Integer, Double> battingByYear(Career c, String metric) {
    SortedMap<Integer, Double> years = new TreeMap<>();
    for (int year : c.battingByYear("runs").keySet()) {
      BatStats s = c.batStats(m -> Career.year(m.played()) == year);
      years.put(year, metric.equals("innings") ? s.innings : metric.equals("average") ? s.average()
          : metric.equals("strike rate") ? s.strikeRate() : (double) s.runs);
    }
    return years;
  }

  /** Year -> wickets, runs or economy; an undefined economy stays NaN. */
  public static SortedMap<Integer, Double> bowlingByYear(Career c, String metric) {
    SortedMap<Integer, Double> years = new TreeMap<>();
    for (int year : c.bowlingByYear("wickets").keySet()) {
      BowlStats s = c.bowlStats(m -> Career.year(m.played()) == year);
      years.put(year, metric.equals("runs") ? s.runs : metric.equals("economy") ? s.economy() : (double) s.wickets);
    }
    return years;
  }
}
