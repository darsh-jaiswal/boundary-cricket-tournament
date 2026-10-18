package com.example.madfinalproject.domain;

import com.example.madfinalproject.domain.Cricket.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/** Scorecard, over-by-over, squad and match-note derivations for the match detail screen. */
public final class MatchReport {
  private MatchReport() {}

  public static final class BatterLine {
    public final String name, team, dismissal;
    public final int runs, balls, fours, sixes;
    public final boolean out, retired;

    BatterLine(String name, String team, Batting b, String dismissal) {
      this.name = name;
      this.team = team;
      this.dismissal = dismissal;
      runs = b.runs;
      balls = b.balls;
      fours = b.fours;
      sixes = b.sixes;
      out = b.out;
      retired = b.retired;
    }

    public boolean notOut() { return !out && !retired; }
    public double strikeRate() { return balls == 0 ? Double.NaN : runs * 100.0 / balls; }
    public String figures() { return battingFigures(runs, balls, fours, sixes); }
  }

  public static final class BowlerLine {
    public final String name, team;
    public final int balls, ballsPerOver, maidens, runs, wickets;

    BowlerLine(String name, String team, Bowling b, int ballsPerOver, int maidens) {
      this.name = name;
      this.team = team;
      this.ballsPerOver = ballsPerOver;
      this.maidens = maidens;
      balls = b.balls;
      runs = b.runs;
      wickets = b.wickets;
    }

    public String overs() { return shortOvers(balls, ballsPerOver); }
    public double economy() { return balls == 0 ? Double.NaN : runs * (double) ballsPerOver / balls; }
    public String figures() { return bowlingFigures(wickets, runs, balls, ballsPerOver); }
  }

  public static final class Fall {
    public final int wicket, runs;
    public final String batter, over;

    Fall(int wicket, int runs, String batter, String over) {
      this.wicket = wicket;
      this.runs = runs;
      this.batter = batter;
      this.over = over;
    }
  }

  public static final class Scorecard {
    public final String battingTeam, bowlingTeam;
    public final boolean superOver;
    public final int runs, wickets, extras;
    public final String overs, extrasDetail;
    /** Runs per over so far, NaN before the first legal ball. */
    public final double runRate;
    public final List<BatterLine> batters = new ArrayList<>();
    public final List<String> yetToBat = new ArrayList<>();
    public final List<BowlerLine> bowlers = new ArrayList<>();
    public final List<Fall> falls = new ArrayList<>();

    Scorecard(Innings in, State s, Rules r, boolean superOver) {
      battingTeam = in.battingTeam;
      bowlingTeam = in.bowlingTeam;
      this.superOver = superOver;
      runs = s.runs;
      wickets = s.wickets;
      overs = s.overs(r);
      runRate = s.legal == 0 ? Double.NaN : s.rate(r);
      int total = 0;
      for (int v : s.extras.values()) total += v;
      extras = total;
      extrasDetail = extrasDetail(s.extras);
    }
  }

  public enum BallKind { DOT, RUNS, BOUNDARY, EXTRA, WICKET }

  public static final class Ball {
    public final String label;
    public final BallKind kind;

    Ball(String label, BallKind kind) {
      this.label = label;
      this.kind = kind;
    }
  }

  public static final class Over {
    /** 1-based over number. */
    public final int number;
    public int runs, scoreRuns, scoreWickets;
    public final List<Ball> balls = new ArrayList<>();
    public final List<String> bowlers = new ArrayList<>(), batters = new ArrayList<>();

    Over(int number) { this.number = number; }
  }

  // ---------------------------------------------------------------- scorecard

  public static boolean superOver(Match m, int index) { return index >= 2; }

  public static Scorecard scorecard(Match m, int index) {
    Innings in = m.innings.get(index);
    State s = m.stateAt(index);
    Rules r = m.rules;
    int bpo = r.ballsPerOver;
    Scorecard card = new Scorecard(in, s, r, superOver(m, index));
    Map<String, String> howOut = new HashMap<>();
    Set<String> atCrease = new HashSet<>(), bowled = new HashSet<>();
    Map<String, Map<Integer, int[]>> spells = new HashMap<>(); // bowler -> over -> {legal, runs}
    for (Progress p : m.progressAt(index)) {
      atCrease.add(p.strikerBefore);
      atCrease.add(p.nonStrikerBefore);
      if (p.extra != Extra.PENALTY && p.bowler != null && !p.bowler.isEmpty()) {
        bowled.add(p.bowler);
        int[] over = spells.computeIfAbsent(p.bowler, k -> new HashMap<>())
            .computeIfAbsent(p.legalBefore / bpo, k -> new int[2]);
        if (p.legal > p.legalBefore) over[0]++;
        over[1] += p.bowlerRuns;
      }
      String out = p.dismissed();
      if (!out.isEmpty()) {
        howOut.put(out, dismissal(p));
        card.falls.add(new Fall(p.wickets, p.runs, out, ballNumber(p.legalBefore, bpo)));
      }
    }
    Set<String> shown = new HashSet<>();
    for (Map.Entry<String, Batting> e : s.batting.entrySet()) {
      String name = e.getKey();
      Batting b = e.getValue();
      if (name == null || name.isEmpty()) continue;
      boolean live = !s.closed && (name.equals(s.striker) || name.equals(s.nonStriker));
      if (!atCrease.contains(name) && !b.out && !b.retired && b.balls == 0 && b.runs == 0 && !live)
        continue;
      String how = b.out ? howOut.getOrDefault(name, fallbackDismissal(b.dismissal))
          : b.retired ? "retired" : "not out";
      card.batters.add(new BatterLine(name, in.battingTeam, b, how));
      shown.add(name);
    }
    for (String player : m.roster(in.battingTeam)) if (!shown.contains(player)) card.yetToBat.add(player);
    for (Map.Entry<String, Bowling> e : s.bowling.entrySet()) {
      String name = e.getKey();
      Bowling b = e.getValue();
      if (name == null || name.isEmpty()) continue;
      if (!bowled.contains(name) && b.balls == 0 && b.runs == 0 && b.wickets == 0) continue;
      int maidens = 0;
      Map<Integer, int[]> spell = spells.get(name);
      if (spell != null) for (int[] o : spell.values()) if (o[0] == bpo && o[1] == 0) maidens++;
      card.bowlers.add(new BowlerLine(name, in.bowlingTeam, b, bpo, maidens));
    }
    return card;
  }

  /** Scorecard wording for the wicket that fell on this delivery. */
  public static String dismissal(Progress p) {
    String bowler = p.bowler == null ? "" : p.bowler;
    String fielder = p.fielder == null ? "" : p.fielder;
    switch (p.dismissal) {
      case BOWLED: return "b " + bowler;
      case LBW: return "lbw b " + bowler;
      case CAUGHT:
        if (fielder.isEmpty()) return "caught b " + bowler;
        return fielder.equals(bowler) ? "c & b " + bowler : "c " + fielder + " b " + bowler;
      case STUMPED: return (fielder.isEmpty() ? "stumped" : "st " + fielder) + " b " + bowler;
      case HIT_WICKET: return "hit wicket b " + bowler;
      case RUN_OUT: return fielder.isEmpty() ? "run out" : "run out (" + fielder + ")";
      case OBSTRUCTING: return "obstructing the field";
      default: return "out";
    }
  }

  private static String fallbackDismissal(String code) {
    return code == null || code.isEmpty() ? "out" : code.replace('_', ' ').toLowerCase(Locale.ROOT);
  }

  /** "wd 15, nb 2, b 7, lb 1, pen 5", leaving out kinds that did not occur. */
  public static String extrasDetail(Map<Extra, Integer> extras) {
    Extra[] order = {Extra.WIDE, Extra.NO_BALL, Extra.BYE, Extra.LEG_BYE, Extra.PENALTY};
    String[] names = {"wd", "nb", "b", "lb", "pen"};
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < order.length; i++) {
      Integer v = extras.get(order[i]);
      if (v == null || v == 0) continue;
      if (b.length() > 0) b.append(", ");
      b.append(names[i]).append(' ').append(v);
    }
    return b.toString();
  }

  /** Delivery notation: the sixth ball of the first over is "0.6", not "1.0". */
  public static String ballNumber(int legalBefore, int ballsPerOver) {
    return legalBefore / ballsPerOver + "." + (legalBefore % ballsPerOver + 1);
  }

  /** "4" for whole overs, "3.2" otherwise. */
  public static String shortOvers(int balls, int ballsPerOver) {
    return balls % ballsPerOver == 0 ? String.valueOf(balls / ballsPerOver)
        : balls / ballsPerOver + "." + balls % ballsPerOver;
  }

  public static String battingFigures(int runs, int balls, int fours, int sixes) {
    return runs + "(" + balls + ")  " + fours + " 4s  " + sixes + " 6s";
  }

  public static String bowlingFigures(int wickets, int runs, int balls, int ballsPerOver) {
    return wickets + "-" + runs + " (" + shortOvers(balls, ballsPerOver) + " Ov)";
  }

  // ---------------------------------------------------------------- overs

  /** Overs of one innings in the order they were bowled. */
  public static List<Over> overs(Match m, int index) {
    int bpo = m.rules.ballsPerOver;
    List<Over> overs = new ArrayList<>();
    Over current = null;
    for (Progress p : m.progressAt(index)) {
      int number = p.legalBefore / bpo + 1;
      if (current == null || current.number != number) {
        current = new Over(number);
        overs.add(current);
      }
      current.balls.add(ball(p, m.rules));
      current.runs += p.runs - p.runsBefore;
      current.scoreRuns = p.runs;
      current.scoreWickets = p.wickets;
      if (p.extra != Extra.PENALTY && p.bowler != null && !p.bowler.isEmpty()
          && !current.bowlers.contains(p.bowler)) current.bowlers.add(p.bowler);
      if (p.strikerBefore != null && !p.strikerBefore.isEmpty()
          && !current.batters.contains(p.strikerBefore)) current.batters.add(p.strikerBefore);
    }
    return overs;
  }

  /** Ball chip: "•", "4", "W", "1W", "WD", "2WD", "1NB", "1B", "1LB", "5P", "WD+W". */
  public static Ball ball(Progress p, Rules r) {
    int total = p.runs - p.runsBefore;
    String label;
    BallKind kind = BallKind.EXTRA;
    switch (p.extra) {
      case WIDE: label = counted(total - r.wide, "WD"); break;
      case NO_BALL: label = counted(total - r.noBall, "NB"); break;
      case BYE: label = counted(total, "B"); break;
      case LEG_BYE: label = counted(total, "LB"); break;
      case PENALTY: label = counted(total, "P"); break;
      default:
        label = total == 0 ? "•" : String.valueOf(total);
        kind = total == 0 ? BallKind.DOT : p.boundary ? BallKind.BOUNDARY : BallKind.RUNS;
    }
    if (p.wickets > p.wicketsBefore) {
      label = p.extra != Extra.NONE ? label + "+W" : total == 0 ? "W" : total + "W";
      kind = BallKind.WICKET;
    }
    return new Ball(label, kind);
  }

  private static String counted(int runs, String suffix) { return runs > 0 ? runs + suffix : suffix; }

  // ---------------------------------------------------------------- squads

  /** The playing squad, or, when none was saved, everyone the innings record for that team. */
  public static List<String> squad(Match m, String team) {
    List<String> roster = m.roster(team);
    if (!roster.isEmpty()) return new ArrayList<>(roster);
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; i < m.innings.size(); i++) {
      Innings in = m.innings.get(i);
      if (!team.equals(in.battingTeam) && !team.equals(in.bowlingTeam)) continue;
      Scorecard card = scorecard(m, i);
      if (team.equals(in.battingTeam)) for (BatterLine b : card.batters) seen.add(b.name);
      if (team.equals(in.bowlingTeam)) {
        for (BowlerLine b : card.bowlers) seen.add(b.name);
        for (Progress p : m.progressAt(i))
          if (p.wickets > p.wicketsBefore && p.fielder != null && !p.fielder.isEmpty()) seen.add(p.fielder);
      }
    }
    return new ArrayList<>(seen);
  }

  // ---------------------------------------------------------------- summary

  /** Most runs in the two main innings, ties going to fewer balls; null before anyone has batted. */
  public static BatterLine bestBatter(Match m) {
    BatterLine best = null;
    for (int i = 0; i < Math.min(2, m.innings.size()); i++)
      for (BatterLine b : scorecard(m, i).batters) {
        if (b.balls == 0 && b.runs == 0) continue;
        if (best == null || b.runs > best.runs || b.runs == best.runs && b.balls < best.balls) best = b;
      }
    return best;
  }

  /** Most wickets in the two main innings, ties going to fewer runs; null before a ball is bowled. */
  public static BowlerLine bestBowler(Match m) {
    BowlerLine best = null;
    for (int i = 0; i < Math.min(2, m.innings.size()); i++)
      for (BowlerLine b : scorecard(m, i).bowlers) {
        if (b.balls == 0) continue;
        if (best == null || b.wickets > best.wickets || b.wickets == best.wickets && b.runs < best.runs) best = b;
      }
    return best;
  }

  // ---------------------------------------------------------------- notes

  /** Timeline notes for one innings; {@code time} formats an event timestamp. */
  public static List<String> notes(Match m, int index, LongFunction<String> time) {
    Innings in = m.innings.get(index);
    int bpo = m.rules.ballsPerOver;
    List<String> notes = new ArrayList<>();
    Event first = null, last = null;
    for (Event e : in.events) if (!e.reversed) { if (first == null) first = e; last = e; }
    if (first == null) return notes;
    notes.add((index == 0 ? "Match started at " : "Innings started at ") + time.apply(first.created));
    List<Progress> progress = m.progressAt(index);
    int next = 0, runs = 0, wickets = 0, legal = 0, extras = 0;
    for (Event e : in.events) {
      if (e.reversed) continue;
      String at = runs + "/" + wickets + " (" + shortOvers(legal, bpo) + " Ov)";
      switch (e.kind) {
        case BALL:
          if (next >= progress.size()) break; // retained after the innings closed
          Progress p = progress.get(next++);
          if (p.extra != Extra.NONE) extras += p.runs - p.runsBefore - p.batRuns;
          runs = p.runs;
          wickets = p.wickets;
          legal = p.legal;
          if (p.runs / 50 > p.runsBefore / 50)
            notes.add(in.battingTeam + ": " + p.runs + " runs in " + overWords(p.legal, bpo) + ", Extras " + extras);
          break;
        case NOTE:
          if (e.note != null && !e.note.trim().isEmpty()) notes.add(e.note.trim() + " at " + at);
          break;
        case INTERRUPTION:
          notes.add("Interruption at " + at + ": innings reduced to " + overWords(e.revisedBalls, bpo)
              + (e.revisedTarget > 0 ? ", revised target " + e.revisedTarget : ""));
          break;
        case END_INNINGS:
          notes.add("Innings closed early at " + at);
          break;
        default:
          break;
      }
    }
    State s = m.stateAt(index);
    if (!s.closed) return notes;
    StringBuilder crease = new StringBuilder();
    for (Map.Entry<String, Batting> e : s.batting.entrySet()) {
      String name = e.getKey();
      if (name == null || name.isEmpty() || e.getValue().out || e.getValue().retired) continue;
      if (!name.equals(s.striker) && !name.equals(s.nonStriker)) continue;
      crease.append(crease.length() == 0 ? " (" : ", ").append(name).append(' ').append(e.getValue().runs);
    }
    if (crease.length() > 0) crease.append(')');
    notes.add((index == 0 ? "Innings Break: " : "End of innings: ") + in.battingTeam + " - " + s.runs + "/"
        + s.wickets + " in " + overWords(s.legal, bpo) + crease);
    String result = m.result();
    if (!"Live".equals(result) && index == m.innings.size() - 1)
      notes.add("Match ended at " + time.apply(last.created) + ". " + result);
    else notes.add("Innings ended at " + time.apply(last.created));
    return notes;
  }

  private static String overWords(int balls, int bpo) {
    String overs = shortOvers(balls, bpo);
    return overs + (overs.equals("1") ? " over" : " overs");
  }

  // ---------------------------------------------------------------- sharing

  public static String shareText(Match m, String title) {
    StringBuilder b = new StringBuilder(m.teamA + " v " + m.teamB);
    if (title != null && !title.isEmpty()) b.append(" · ").append(title);
    for (int i = 0; i < m.innings.size(); i++) {
      State s = m.stateAt(i);
      b.append('\n').append(m.innings.get(i).battingTeam).append(superOver(m, i) ? " (Super Over)" : "")
          .append(' ').append(s.runs).append('/').append(s.wickets)
          .append(" (").append(s.overs(m.rules)).append(" Ov)");
    }
    String result = m.result();
    b.append('\n').append("Live".equals(result) ? "Match in progress" : result);
    return b.toString();
  }
}
