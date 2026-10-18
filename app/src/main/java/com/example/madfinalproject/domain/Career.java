package com.example.madfinalproject.domain;

import com.example.madfinalproject.domain.Cricket.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * One person's career across every team identity they hold, rebuilt from corrected journals.
 * Only the two main innings count (Super Overs are excluded), matching PlayerStats.
 */
public final class Career {
  public static final String[] ZONES = {
    "Third man", "Point", "Cover", "Long off", "Long on", "Midwicket", "Square leg", "Fine leg"
  };

  public static String key(String team, String name) { return team + "\u0000" + name; }

  /** A person: every (team, name) pair they appear under. */
  public static final class Who {
    public final String display;
    public final Set<String> keys = new LinkedHashSet<>();
    public Who(String display) { this.display = display; }
    public Who add(String team, String name) { keys.add(key(team, name)); return this; }
    String nameIn(String team) {
      for (String k : keys) {
        int split = k.indexOf('\u0000');
        if (k.substring(0, split).equals(team)) return k.substring(split + 1);
      }
      return null;
    }
  }

  public static final class Filter {
    public String ballType = "", matchType = "", pitchType = "";
    public int overs;
    public boolean accepts(Match m) {
      return (ballType.isEmpty() || ballType.equals(m.ballType()))
          && (matchType.isEmpty() || matchType.equals(m.matchType()))
          && (pitchType.isEmpty() || pitchType.equals(m.pitchType()))
          && (overs == 0 || overs == m.rules.overs);
    }
  }

  public static final class MatchRow {
    public final Match match;
    public final String team, opponent, name;
    public final boolean won, lost;
    MatchRow(Match match, String team, String name) {
      this.match = match; this.team = team; this.name = name;
      opponent = team.equals(match.teamA) ? match.teamB : match.teamA;
      String winner = match.winner();
      won = team.equals(winner);
      lost = winner != null && !won;
    }
  }

  public static final class BatInnings {
    public MatchRow row;
    public int index, position, runs, balls, fours, sixes, dots;
    public boolean out;
    public String dismissal = "";
    public int[] timeline = new int[0];
  }

  public static final class BowlInnings {
    public MatchRow row;
    public int index, balls, ballsPerOver, runs, wickets, maidens, wides, noBalls, dots, fours, sixes;
    public String figures() { return overs(balls, ballsPerOver) + "-" + maidens + "-" + runs + "-" + wickets; }
  }

  /** A delivery the person faced or bowled. */
  public static final class Ball {
    public MatchRow row;
    public int index, over, batRuns, bowlerRuns, zone, batterPosition;
    public String batter, bowler;
    public Extra extra;
    public Dismissal dismissal = Dismissal.NONE;
    public boolean batterOut, boundary, legal;
    public boolean faced() { return extra != Extra.WIDE && extra != Extra.PENALTY; }
    public boolean bowlerWicket() {
      return dismissal != Dismissal.NONE && dismissal != Dismissal.RUN_OUT && dismissal != Dismissal.OBSTRUCTING;
    }
  }

  public static final class Partnership {
    public String partner;
    public int runs, balls, myRuns, myBalls, partnerRuns, partnerBalls;
  }

  public final Who who;
  public final List<MatchRow> matches = new ArrayList<>();
  public final List<BatInnings> batting = new ArrayList<>();
  public final List<BowlInnings> bowling = new ArrayList<>();
  public final List<Ball> faced = new ArrayList<>(), bowled = new ArrayList<>();
  public final List<Partnership> partnerships = new ArrayList<>();
  public int catches, runOuts, stumpings, caughtAndBowled;

  private Career(Who who) { this.who = who; }

  public static Career of(List<Match> all, Who who, Filter filter) {
    Career c = new Career(who);
    List<Match> sorted = new ArrayList<>(all);
    sorted.sort(Comparator.comparingLong(Match::played));
    for (Match m : sorted) if (filter == null || filter.accepts(m)) c.scan(m);
    return c;
  }

  private void scan(Match m) {
    for (String team : new String[] {m.teamA, m.teamB}) {
      String name = who.nameIn(team);
      if (name == null) continue;
      MatchRow row = new MatchRow(m, team, name);
      boolean involved = m.roster(team).contains(name);
      int main = Math.min(2, m.innings.size());
      for (int i = 0; i < main; i++) {
        Innings in = m.innings.get(i);
        State state = m.stateAt(i);
        List<Progress> progress = m.progressAt(i);
        List<String> order = new ArrayList<>(state.batting.keySet());
        if (team.equals(in.battingTeam)) involved |= batInnings(row, i, state, progress, order);
        if (team.equals(in.bowlingTeam)) involved |= bowlInnings(row, i, state, progress, order);
      }
      if (involved) matches.add(row);
      return;
    }
  }

  private boolean batInnings(MatchRow row, int index, State state, List<Progress> progress, List<String> order) {
    String me = row.name;
    Batting b = state.batting.get(me);
    boolean atCrease = false;
    for (Progress p : progress) if (me.equals(p.strikerBefore) || me.equals(p.nonStrikerBefore)) atCrease = true;
    if (b == null || !atCrease && !b.out && b.balls == 0) return false;
    BatInnings bi = new BatInnings();
    bi.row = row; bi.index = index; bi.position = order.indexOf(me) + 1;
    bi.runs = b.runs; bi.balls = b.balls; bi.fours = b.fours; bi.sixes = b.sixes;
    bi.out = b.out; bi.dismissal = b.out ? b.dismissal : "";
    List<Integer> timeline = new ArrayList<>();
    int total = 0;
    Partnership open = null;
    String openPair = null;
    for (Progress p : progress) {
      String pair = pairKey(p.strikerBefore, p.nonStrikerBefore);
      boolean mine = me.equals(p.strikerBefore) || me.equals(p.nonStrikerBefore);
      if (!pair.equals(openPair)) {
        open = null;
        openPair = pair;
        if (mine) {
          open = new Partnership();
          open.partner = me.equals(p.strikerBefore) ? p.nonStrikerBefore : p.strikerBefore;
          partnerships.add(open);
        }
      }
      if (open != null) {
        open.runs += p.runs - p.runsBefore;
        boolean faced = p.extra != Extra.WIDE && p.extra != Extra.PENALTY;
        if (faced) open.balls++;
        if (me.equals(p.strikerBefore)) { open.myRuns += p.batRuns; if (faced) open.myBalls++; }
        else { open.partnerRuns += p.batRuns; if (faced) open.partnerBalls++; }
      }
      if (!me.equals(p.strikerBefore)) {
        if (me.equals(p.dismissed())) faced(row, index, p, me, true, order);
        continue;
      }
      Ball ball = faced(row, index, p, me, me.equals(p.dismissed()), order);
      if (ball.faced()) {
        total += p.batRuns;
        timeline.add(total);
        if (p.batRuns == 0) bi.dots++;
      }
    }
    bi.timeline = new int[timeline.size()];
    for (int i = 0; i < bi.timeline.length; i++) bi.timeline[i] = timeline.get(i);
    batting.add(bi);
    return true;
  }

  private Ball faced(MatchRow row, int index, Progress p, String me, boolean out, List<String> order) {
    Ball ball = ball(row, index, p, order);
    ball.batterOut = out;
    if (me.equals(p.strikerBefore)) faced.add(ball);
    else if (out) faced.add(ball); // non-striker run out: counts as a dismissal, not a ball faced
    return ball;
  }

  private Ball ball(MatchRow row, int index, Progress p, List<String> order) {
    Ball ball = new Ball();
    ball.row = row; ball.index = index;
    ball.over = p.legalBefore / row.match.rules.ballsPerOver + 1;
    ball.batter = p.strikerBefore; ball.bowler = p.bowler;
    ball.batRuns = p.batRuns; ball.bowlerRuns = p.bowlerRuns; ball.zone = p.zone; ball.extra = p.extra;
    ball.boundary = p.boundary; ball.legal = p.legal > p.legalBefore;
    if (p.wickets > p.wicketsBefore) {
      ball.dismissal = p.dismissal;
      ball.batterPosition = order.indexOf(p.dismissed()) + 1;
    }
    return ball;
  }

  private boolean bowlInnings(MatchRow row, int index, State state, List<Progress> progress, List<String> order) {
    String me = row.name;
    int bpo = row.match.rules.ballsPerOver;
    Map<Integer, int[]> overs = new LinkedHashMap<>(); // over -> {legal balls by me, runs}
    for (Progress p : progress) {
      if (p.dismissal == Dismissal.CAUGHT && p.wickets > p.wicketsBefore && me.equals(p.fielder)) {
        catches++;
        if (me.equals(p.bowler)) caughtAndBowled++;
      }
      if (p.wickets > p.wicketsBefore && me.equals(p.fielder)) {
        if (p.dismissal == Dismissal.RUN_OUT) runOuts++;
        if (p.dismissal == Dismissal.STUMPED) stumpings++;
      }
      if (!me.equals(p.bowler) || p.extra == Extra.PENALTY) continue;
      Ball ball = ball(row, index, p, order);
      ball.batterOut = ball.dismissal != Dismissal.NONE;
      bowled.add(ball);
      int[] o = overs.computeIfAbsent(p.legalBefore / bpo, k -> new int[2]);
      if (ball.legal) o[0]++;
      o[1] += p.bowlerRuns;
    }
    Bowling b = state.bowling.get(me);
    if (b == null || b.balls == 0 && b.runs == 0 && b.wickets == 0 && overs.isEmpty()) return false;
    BowlInnings bi = new BowlInnings();
    bi.row = row; bi.index = index; bi.ballsPerOver = bpo;
    bi.balls = b.balls; bi.runs = b.runs; bi.wickets = b.wickets; bi.wides = b.wides; bi.noBalls = b.noBalls;
    for (int[] o : overs.values()) if (o[0] == bpo && o[1] == 0) bi.maidens++;
    for (Ball ball : bowled) {
      if (ball.row != row || ball.index != index) continue;
      if (ball.legal && ball.bowlerRuns == 0) bi.dots++;
      if (ball.boundary && ball.batRuns == 4) bi.fours++;
      if (ball.boundary && ball.batRuns == 6) bi.sixes++;
    }
    bowling.add(bi);
    return true;
  }

  private static String pairKey(String a, String b) {
    a = a == null ? "" : a; b = b == null ? "" : b;
    return a.compareTo(b) < 0 ? a + "\u0000" + b : b + "\u0000" + a;
  }

  // ---------------------------------------------------------------- batting

  public static final class BatStats {
    public int matches, innings, notOuts, runs, balls, highest, thirties, fifties, hundreds, fours, sixes, dots, ducks, won, lost;
    public boolean highestNotOut;
    public double average() { int outs = innings - notOuts; return outs == 0 ? Double.NaN : runs / (double) outs; }
    public double strikeRate() { return balls == 0 ? Double.NaN : runs * 100.0 / balls; }
    public String highestText() { return innings == 0 ? "-" : highest + (highestNotOut ? "*" : ""); }
  }

  public BatStats batStats() { return batStats(m -> true); }

  public BatStats batStats(Predicate<Match> where) {
    BatStats s = new BatStats();
    for (MatchRow r : matches) if (where.test(r.match)) { s.matches++; if (r.won) s.won++; if (r.lost) s.lost++; }
    for (BatInnings b : batting) {
      if (!where.test(b.row.match)) continue;
      s.innings++;
      if (!b.out) s.notOuts++;
      s.runs += b.runs; s.balls += b.balls; s.fours += b.fours; s.sixes += b.sixes; s.dots += b.dots;
      if (b.out && b.runs == 0) s.ducks++;
      if (b.runs >= 100) s.hundreds++; else if (b.runs >= 50) s.fifties++; else if (b.runs >= 30) s.thirties++;
      if (b.runs > s.highest || b.runs == s.highest && !b.out) { s.highest = b.runs; s.highestNotOut = !b.out; }
    }
    return s;
  }

  /** Most recent first. */
  public List<BatInnings> battingForm(int count) {
    List<BatInnings> recent = new ArrayList<>(batting);
    Collections.reverse(recent);
    return recent.subList(0, Math.min(count, recent.size()));
  }

  /** Average runs after each ball faced, over the innings that lasted that long (index 0 = ball 1). */
  public double[] playingStyle(int maxBalls) {
    int longest = 0;
    for (BatInnings b : batting) longest = Math.max(longest, b.timeline.length);
    double[] curve = new double[Math.min(maxBalls, longest)];
    for (int i = 0; i < curve.length; i++) {
      int total = 0, count = 0;
      for (BatInnings b : batting) if (b.timeline.length > i) { total += b.timeline[i]; count++; }
      curve[i] = count == 0 ? 0 : total / (double) count;
    }
    return curve;
  }

  public double strikeRateFirst(int balls) {
    int runs = 0, faced = 0;
    for (BatInnings b : batting) {
      int n = Math.min(balls, b.timeline.length);
      if (n == 0) continue;
      runs += b.timeline[n - 1]; faced += n;
    }
    return faced == 0 ? Double.NaN : runs * 100.0 / faced;
  }

  /** Runs per zone from the balls in the list; zone -1 (unrecorded) is skipped. */
  public static int[] zoneRuns(List<Ball> balls) {
    int[] zones = new int[8];
    for (Ball b : balls) if (b.zone >= 0 && b.zone < 8) zones[b.zone] += b.batRuns;
    return zones;
  }

  public static String bestZone(int[] zones) {
    int best = -1;
    for (int i = 0; i < zones.length; i++) if (zones[i] > 0 && (best < 0 || zones[i] > zones[best])) best = i;
    return best < 0 ? "" : ZONES[best];
  }

  /** Percent of deliveries by bat runs: dots, 1s, 2s, 3s, 4s, 6s. Wides and penalties excluded. */
  public static double[] runTypes(List<Ball> balls) {
    int[] counts = new int[6];
    int total = 0;
    for (Ball b : balls) {
      if (!b.faced() || b.batterOut && b.batRuns == 0 && b.dismissal == Dismissal.RUN_OUT && !b.legal) continue;
      int bucket = b.batRuns >= 6 ? 5 : b.batRuns == 4 || b.batRuns == 5 ? 4 : b.batRuns;
      counts[bucket]++;
      total++;
    }
    double[] pct = new double[6];
    for (int i = 0; i < 6; i++) pct[i] = total == 0 ? 0 : counts[i] * 100.0 / total;
    return pct;
  }

  /** Position -> {innings, runs, balls}. */
  public SortedMap<Integer, int[]> battingPositions() {
    SortedMap<Integer, int[]> positions = new TreeMap<>();
    for (BatInnings b : batting) {
      int[] p = positions.computeIfAbsent(b.position, k -> new int[3]);
      p[0]++; p[1] += b.runs; p[2] += b.balls;
    }
    return positions;
  }

  /** Positions ordered by innings played there, then runs. */
  public List<Integer> preferredPositions() {
    SortedMap<Integer, int[]> positions = battingPositions();
    List<Integer> order = new ArrayList<>(positions.keySet());
    order.sort((a, b) -> {
      int[] x = positions.get(a), y = positions.get(b);
      return x[0] != y[0] ? y[0] - x[0] : y[1] - x[1];
    });
    return order;
  }

  /** Match innings 1 (setting) or 2 (chasing). */
  public BatStats battingInMatchInnings(int inningsNumber) {
    BatStats s = new BatStats();
    for (BatInnings b : batting) {
      if (b.index != inningsNumber - 1) continue;
      s.innings++;
      if (!b.out) s.notOuts++;
      s.runs += b.runs; s.balls += b.balls;
    }
    return s;
  }

  /** Percent of decided matches won when the condition held, NaN when it never held. */
  public double winPercent(Predicate<BatInnings> condition) {
    int held = 0, won = 0;
    for (BatInnings b : batting) {
      if (!condition.test(b) || !b.row.won && !b.row.lost) continue;
      held++;
      if (b.row.won) won++;
    }
    return held == 0 ? Double.NaN : won * 100.0 / held;
  }

  public Map<String, Integer> outTypes() {
    Map<String, Integer> types = new LinkedHashMap<>();
    for (BatInnings b : batting) if (b.out) types.merge(dismissalLabel(b.dismissal), 1, Integer::sum);
    return types;
  }

  public List<Partnership> topPartnerships(int count) {
    List<Partnership> top = new ArrayList<>(partnerships);
    top.sort((a, b) -> b.runs != a.runs ? b.runs - a.runs : a.balls - b.balls);
    return top.subList(0, Math.min(count, top.size()));
  }

  /** Bowler -> {runs, balls, dismissals, dots, boundaries} for deliveries faced. */
  public Map<String, int[]> againstBowlers() {
    Map<String, int[]> against = new LinkedHashMap<>();
    for (Ball b : faced) {
      int[] a = against.computeIfAbsent(b.bowler, k -> new int[5]);
      a[0] += b.batRuns;
      if (b.faced()) { a[1]++; if (b.batRuns == 0) a[3]++; }
      if (b.batterOut && b.bowlerWicket()) a[2]++;
      if (b.boundary) a[4]++;
    }
    return against;
  }

  /** Groups faced deliveries by the bowler's style: style -> {runs, balls, dismissals, fours, sixes}. */
  public Map<String, int[]> againstStyles(Map<String, PlayerProfile> profiles, boolean paceSpin) {
    Map<String, int[]> styles = new LinkedHashMap<>();
    for (Ball b : faced) {
      PlayerProfile p = profiles.get(key(b.row.opponent, b.bowler));
      String style = p == null ? "" : paceSpin ? PlayerProfile.paceOrSpin(p.bowlingStyle) : p.bowlingStyle;
      if (style.isEmpty()) continue;
      int[] a = styles.computeIfAbsent(style, k -> new int[5]);
      a[0] += b.batRuns;
      if (b.faced()) a[1]++;
      if (b.batterOut) a[2]++;
      if (b.boundary && b.batRuns == 4) a[3]++;
      if (b.boundary && b.batRuns == 6) a[4]++;
    }
    return styles;
  }

  /** Pitch type -> stats, for matches that recorded one. */
  public Map<String, BatStats> battingByPitch() {
    Map<String, BatStats> by = new LinkedHashMap<>();
    for (String pitch : PlayerProfile.PITCH_TYPES) {
      BatStats s = batStats(m -> pitch.equals(m.pitchType()));
      if (s.innings > 0) by.put(pitch, s);
    }
    return by;
  }

  /** Year -> value, oldest first. Metric: runs, innings, average, strike rate. */
  public SortedMap<Integer, Double> battingByYear(String metric) {
    SortedMap<Integer, Double> years = new TreeMap<>();
    for (int year : years(batting.isEmpty() ? null : batting)) {
      BatStats s = batStats(m -> year(m.played()) == year);
      double v = metric.equals("innings") ? s.innings : metric.equals("average") ? s.average()
          : metric.equals("strike rate") ? s.strikeRate() : s.runs;
      years.put(year, Double.isNaN(v) ? 0 : v);
    }
    return years;
  }

  // ---------------------------------------------------------------- bowling

  public static final class BowlStats {
    public int matches, innings, balls, maidens, wickets, runs, bestWickets, bestRuns, wides, noBalls, dots, fours, sixes;
    public double overs; // true overs, each innings at its own balls per over
    public boolean uniform = true;
    public int ballsPerOver = 6;
    public double economy() { return overs == 0 ? Double.NaN : runs / overs; }
    public double strikeRate() { return wickets == 0 ? Double.NaN : balls / (double) wickets; }
    public double average() { return wickets == 0 ? Double.NaN : runs / (double) wickets; }
    public String best() { return innings == 0 ? "-" : bestWickets + "/" + bestRuns; }
    public String oversText() {
      if (uniform) return Career.overs(balls, ballsPerOver);
      return String.format(Locale.US, "%.1f", overs);
    }
  }

  public BowlStats bowlStats() { return bowlStats(m -> true); }

  public BowlStats bowlStats(Predicate<Match> where) {
    BowlStats s = new BowlStats();
    for (MatchRow r : matches) if (where.test(r.match)) s.matches++;
    boolean first = true;
    for (BowlInnings b : bowling) {
      if (!where.test(b.row.match)) continue;
      if (s.innings > 0 && b.ballsPerOver != s.ballsPerOver) s.uniform = false;
      s.ballsPerOver = b.ballsPerOver;
      s.innings++;
      s.balls += b.balls; s.overs += b.balls / (double) b.ballsPerOver;
      s.maidens += b.maidens; s.wickets += b.wickets; s.runs += b.runs;
      s.wides += b.wides; s.noBalls += b.noBalls; s.dots += b.dots; s.fours += b.fours; s.sixes += b.sixes;
      if (first || b.wickets > s.bestWickets || b.wickets == s.bestWickets && b.runs < s.bestRuns) {
        s.bestWickets = b.wickets; s.bestRuns = b.runs; first = false;
      }
    }
    return s;
  }

  public List<BowlInnings> bowlingForm(int count) {
    List<BowlInnings> recent = new ArrayList<>(bowling);
    Collections.reverse(recent);
    return recent.subList(0, Math.min(count, recent.size()));
  }

  /** Over number -> {innings that bowled it, runs, wickets, dots}. */
  public SortedMap<Integer, int[]> bowlingByOver() {
    SortedMap<Integer, int[]> overs = new TreeMap<>();
    Set<String> seen = new HashSet<>();
    for (Ball b : bowled) {
      int[] o = overs.computeIfAbsent(b.over, k -> new int[4]);
      if (seen.add(b.row.match.id + ":" + b.index + ":" + b.over)) o[0]++;
      o[1] += b.bowlerRuns;
      if (b.bowlerWicket()) o[2]++;
      if (b.legal && b.bowlerRuns == 0) o[3]++;
    }
    return overs;
  }

  /** Five-over slot label ("1-5", "6-10", ...) with the highest value in column (1 runs, 2 wickets, 3 dots). */
  public String bestSlot(int column) {
    Map<Integer, Integer> slots = new TreeMap<>();
    for (Map.Entry<Integer, int[]> e : bowlingByOver().entrySet())
      slots.merge((e.getKey() - 1) / 5, e.getValue()[column], Integer::sum);
    int best = -1, value = 0;
    for (Map.Entry<Integer, Integer> e : slots.entrySet()) if (e.getValue() > value) { best = e.getKey(); value = e.getValue(); }
    return best < 0 ? "" : (best * 5 + 1) + "-" + (best * 5 + 5);
  }

  public Map<String, Integer> wicketTypes(String batter) {
    Map<String, Integer> types = new LinkedHashMap<>();
    for (Ball b : bowled)
      if (b.bowlerWicket() && (batter == null || batter.equals(b.batter)))
        types.merge(dismissalLabel(b.dismissal.name()), 1, Integer::sum);
    return types;
  }

  public BowlStats bowlingInMatchInnings(int inningsNumber) {
    return bowlStatsOf(b -> b.index == inningsNumber - 1);
  }

  private BowlStats bowlStatsOf(Predicate<BowlInnings> which) {
    BowlStats s = new BowlStats();
    for (BowlInnings b : bowling) {
      if (!which.test(b)) continue;
      if (s.innings > 0 && b.ballsPerOver != s.ballsPerOver) s.uniform = false;
      s.ballsPerOver = b.ballsPerOver;
      s.innings++; s.balls += b.balls; s.overs += b.balls / (double) b.ballsPerOver;
      s.maidens += b.maidens; s.wickets += b.wickets; s.runs += b.runs;
    }
    return s;
  }

  /** Percent of the bowler's runs conceded in fours and sixes. */
  public double boundaryRunPercent() {
    int boundary = 0, runs = 0;
    for (Ball b : bowled) { runs += b.bowlerRuns; if (b.boundary) boundary += b.batRuns; }
    return runs == 0 ? Double.NaN : boundary * 100.0 / runs;
  }

  /** {wide deliveries, no-ball deliveries}. */
  public int[] extrasBowled() {
    int[] e = new int[2];
    for (Ball b : bowled) { if (b.extra == Extra.WIDE) e[0]++; if (b.extra == Extra.NO_BALL) e[1]++; }
    return e;
  }

  /** Split by the batter's hand: hand -> {wickets, balls, runs}. */
  public Map<String, int[]> byBatterHand(Map<String, PlayerProfile> profiles) {
    Map<String, int[]> hands = new LinkedHashMap<>();
    for (Ball b : bowled) {
      PlayerProfile p = profiles.get(key(b.row.opponent, b.batter));
      if (p == null || p.battingHand.isEmpty()) continue;
      int[] h = hands.computeIfAbsent(p.battingHand, k -> new int[3]);
      if (b.bowlerWicket()) h[0]++;
      if (b.legal) h[1]++;
      h[2] += b.bowlerRuns;
    }
    return hands;
  }

  /** {top 1-3, middle 4-7, lower 8+} wicket counts by the dismissed batter's position. */
  public int[] wicketsByOrder() {
    int[] order = new int[3];
    for (Ball b : bowled) {
      if (!b.bowlerWicket() || b.batterPosition <= 0) continue;
      order[b.batterPosition <= 3 ? 0 : b.batterPosition <= 7 ? 1 : 2]++;
    }
    return order;
  }

  public SortedMap<Integer, Integer> wicketsByPosition() {
    SortedMap<Integer, Integer> positions = new TreeMap<>();
    for (Ball b : bowled) if (b.bowlerWicket() && b.batterPosition > 0) positions.merge(b.batterPosition, 1, Integer::sum);
    return positions;
  }

  /** Wickets in an innings -> number of innings. */
  public SortedMap<Integer, Integer> wicketsPerInnings() {
    SortedMap<Integer, Integer> counts = new TreeMap<>();
    for (BowlInnings b : bowling) counts.merge(b.wickets, 1, Integer::sum);
    return counts;
  }

  /** Percent of decided matches lost when the person bowled and took no wicket. */
  public double losePercentWhenWicketless() {
    int held = 0, lost = 0;
    Set<String> counted = new HashSet<>();
    Map<String, Integer> wicketsByMatch = new HashMap<>();
    for (BowlInnings b : bowling) wicketsByMatch.merge(b.row.match.id, b.wickets, Integer::sum);
    for (BowlInnings b : bowling) {
      if (!counted.add(b.row.match.id) || wicketsByMatch.get(b.row.match.id) > 0) continue;
      if (!b.row.won && !b.row.lost) continue;
      held++;
      if (b.row.lost) lost++;
    }
    return held == 0 ? Double.NaN : lost * 100.0 / held;
  }

  public Map<String, BowlStats> bowlingByPitch() {
    Map<String, BowlStats> by = new LinkedHashMap<>();
    for (String pitch : PlayerProfile.PITCH_TYPES) {
      BowlStats s = bowlStats(m -> pitch.equals(m.pitchType()));
      if (s.innings > 0) by.put(pitch, s);
    }
    return by;
  }

  /** Year -> value. Metric: wickets, runs, economy. */
  public SortedMap<Integer, Double> bowlingByYear(String metric) {
    SortedMap<Integer, Double> years = new TreeMap<>();
    for (int year : years(bowling.isEmpty() ? null : bowling)) {
      BowlStats s = bowlStats(m -> year(m.played()) == year);
      double v = metric.equals("runs") ? s.runs : metric.equals("economy") ? s.economy() : s.wickets;
      years.put(year, Double.isNaN(v) ? 0 : v);
    }
    return years;
  }

  // ---------------------------------------------------------------- teams

  public static final class TeamSummary {
    public String team;
    public long since;
    public int played, won, lost;
  }

  public List<TeamSummary> teams() {
    Map<String, TeamSummary> teams = new LinkedHashMap<>();
    for (MatchRow r : matches) {
      TeamSummary t = teams.get(r.team);
      if (t == null) { t = new TeamSummary(); t.team = r.team; t.since = r.match.played(); teams.put(r.team, t); }
      t.since = Math.min(t.since, r.match.played());
      t.played++;
      if (r.won) t.won++;
      if (r.lost) t.lost++;
    }
    List<TeamSummary> list = new ArrayList<>(teams.values());
    list.sort((a, b) -> Long.compare(b.since, a.since));
    return list;
  }

  // ---------------------------------------------------------------- helpers

  public static String overs(int balls, int ballsPerOver) {
    return balls / ballsPerOver + "." + balls % ballsPerOver;
  }

  public static String dismissalLabel(String code) {
    if (code == null || code.isEmpty()) return "Not out";
    switch (code) {
      case "BOWLED": return "Bowled";
      case "CAUGHT": return "Caught out";
      case "LBW": return "LBW";
      case "RUN_OUT": return "Run out";
      case "STUMPED": return "Stumped";
      case "HIT_WICKET": return "Hit wicket";
      case "OBSTRUCTING": return "Obstructing the field";
      default: return code;
    }
  }

  public static int year(long millis) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(millis);
    return c.get(Calendar.YEAR);
  }

  private static SortedSet<Integer> years(List<?> innings) {
    SortedSet<Integer> years = new TreeSet<>();
    if (innings == null) return years;
    for (Object o : innings) {
      MatchRow r = o instanceof BatInnings ? ((BatInnings) o).row : ((BowlInnings) o).row;
      years.add(year(r.match.played()));
    }
    return years;
  }
}
