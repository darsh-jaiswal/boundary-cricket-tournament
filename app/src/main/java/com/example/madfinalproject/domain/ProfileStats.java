package com.example.madfinalproject.domain;

import com.example.madfinalproject.domain.Cricket.Match;
import com.example.madfinalproject.domain.Cricket.State;
import java.text.SimpleDateFormat;
import java.util.*;

/** What the owner's profile screen shows: stat tiles, Stats-tab sections, filter options and labels. */
public final class ProfileStats {
  private ProfileStats() {}

  public static final String BATTING = "Batting", BOWLING = "Bowling", FIELDING = "Fielding";
  public static final String[] DISCIPLINES = {BATTING, BOWLING, FIELDING};

  /** One tile or table row: a value over its label. */
  public static final class Stat {
    public final String value, label;
    Stat(String value, String label) { this.value = value; this.label = label; }
    @Override public String toString() { return label + "=" + value; }
  }

  public static List<Stat> stats(String discipline, Career career) {
    if (FIELDING.equals(discipline)) return fielding(career);
    if (BOWLING.equals(discipline)) return bowling(career.bowlStats());
    return batting(career.batStats());
  }

  public static List<Stat> batting(Career.BatStats s) {
    return Arrays.asList(
        stat(s.matches, "Mat"), stat(s.innings, "Inns"), stat(s.notOuts, "NO"),
        stat(s.runs, "Runs"), new Stat(s.highestText(), "HS"), new Stat(decimal(s.average()), "Avg"),
        new Stat(decimal(s.strikeRate()), "SR"), stat(s.thirties, "30s"), stat(s.fifties, "50s"),
        stat(s.hundreds, "100s"), stat(s.fours, "4s"), stat(s.sixes, "6s"),
        stat(s.ducks, "Ducks"), stat(s.won, "Won"), stat(s.lost, "Loss"));
  }

  public static List<Stat> bowling(Career.BowlStats s) {
    return Arrays.asList(
        stat(s.matches, "Mat"), stat(s.innings, "Inns"), new Stat(s.innings == 0 ? "-" : s.oversText(), "Overs"),
        stat(s.maidens, "Maidens"), stat(s.wickets, "Wkts"), stat(s.runs, "Runs"),
        new Stat(s.best(), "Best"), new Stat(decimal(s.economy()), "Eco"), new Stat(decimal(s.strikeRate()), "SR"),
        new Stat(decimal(s.average()), "Avg"), stat(s.wides, "Wides"), stat(s.noBalls, "NB"),
        stat(s.dots, "Dots"), stat(s.fours, "4s"), stat(s.sixes, "6s"));
  }

  public static List<Stat> fielding(Career c) {
    return Arrays.asList(stat(c.catches, "Catches"), stat(c.runOuts, "Run outs"),
        stat(c.stumpings, "Stumpings"), stat(c.caughtAndBowled, "Caught & bowled"));
  }

  private static Stat stat(int value, String label) { return new Stat(String.valueOf(value), label); }

  /** Two decimals, or "-" when there is nothing to divide by. */
  public static String decimal(double v) {
    return Double.isNaN(v) || Double.isInfinite(v) ? "-" : String.format(Locale.US, "%.2f", v);
  }

  // ---------------------------------------------------------------- sections and filters

  /** A Stats-tab block below Overall: one ball type, or one match type other than limited overs. */
  public static final class Section {
    public final String title, ballType, matchType;
    Section(String title, String ballType, String matchType) {
      this.title = title; this.ballType = ballType; this.matchType = matchType;
    }
    public boolean byBallType() { return !ballType.isEmpty(); }
    /** The base filter narrowed to this section. */
    public Career.Filter filter(Career.Filter base) {
      Career.Filter f = copy(base);
      if (byBallType()) f.ballType = ballType; else f.matchType = matchType;
      return f;
    }
    @Override public String toString() { return title; }
  }

  /** Leather then Tennis, then Box Cricket and The Hundred, each only when a listed match recorded it. */
  public static List<Section> sections(List<Career.MatchRow> rows) {
    Set<String> balls = new HashSet<>(), types = new HashSet<>();
    for (Career.MatchRow r : rows) { balls.add(r.match.ballType()); types.add(r.match.matchType()); }
    List<Section> sections = new ArrayList<>();
    for (String ball : new String[] {"LEATHER", "TENNIS"})
      if (balls.contains(ball)) sections.add(new Section(PlayerProfile.label(ball), ball, ""));
    for (String type : PlayerProfile.MATCH_TYPES)
      if (!type.equals("LIMITED_OVERS") && types.contains(type)) sections.add(new Section(PlayerProfile.label(type), "", type));
    return sections;
  }

  /** Distinct overs limits of the listed matches, smallest first. */
  public static List<Integer> oversPlayed(List<Career.MatchRow> rows) {
    SortedSet<Integer> overs = new TreeSet<>();
    for (Career.MatchRow r : rows) overs.add(r.match.rules.overs);
    return new ArrayList<>(overs);
  }

  public static Career.Filter copy(Career.Filter f) {
    Career.Filter c = new Career.Filter();
    if (f == null) return c;
    c.ballType = f.ballType; c.matchType = f.matchType; c.pitchType = f.pitchType; c.overs = f.overs;
    return c;
  }

  /** Deliveries whose direction was recorded, for the wagon wheel. */
  public static List<Career.Ball> withZone(List<Career.Ball> balls) {
    List<Career.Ball> zoned = new ArrayList<>();
    for (Career.Ball b : balls) if (b.zone >= 0 && b.zone < Career.ZONES.length) zoned.add(b);
    return zoned;
  }

  /** Matches newest first. */
  public static List<Career.MatchRow> newestFirst(List<Career.MatchRow> rows) {
    List<Career.MatchRow> sorted = new ArrayList<>(rows);
    sorted.sort((a, b) -> Long.compare(b.match.played(), a.match.played()));
    return sorted;
  }

  // ---------------------------------------------------------------- labels

  /** {"139/8", "20.0 Ov"} for the team's main innings, or null when it has not batted. */
  public static String[] score(Match m, String team) {
    for (int i = 0; i < Math.min(2, m.innings.size()); i++) {
      if (!m.innings.get(i).battingTeam.equals(team)) continue;
      State s = m.stateAt(i);
      return new String[] {s.runs + "/" + s.wickets, s.overs(m.rules) + " Ov"};
    }
    return null;
  }

  /** "Today", "Yesterday" or "12-Oct-26". */
  public static String matchDate(long played, long now, TimeZone zone) {
    Calendar day = Calendar.getInstance(zone, Locale.US), today = Calendar.getInstance(zone, Locale.US);
    day.setTimeInMillis(played);
    today.setTimeInMillis(now);
    if (sameDay(day, today)) return "Today";
    today.add(Calendar.DAY_OF_YEAR, -1);
    if (sameDay(day, today)) return "Yesterday";
    return format("dd-MMM-yy", played, zone);
  }

  /** "Since 11 Sep, 2026". */
  public static String since(long millis, TimeZone zone) { return "Since " + format("dd MMM, yyyy", millis, zone); }

  public static String matchesLabel(int n) { return n + (n == 1 ? " match" : " matches"); }

  /** First role line: batting order and hand, whichever are recorded. */
  public static List<String> battingRole(PlayerProfile p) {
    List<String> parts = new ArrayList<>();
    if (p != null && !p.battingOrder.isEmpty()) parts.add(p.battingOrder);
    if (p != null && !p.battingHand.isEmpty()) parts.add(p.battingHand);
    return parts;
  }

  /** "Middle-order batter, RHB, Right-arm leg break", or "" when nothing is recorded. */
  public static String roleSentence(PlayerProfile p) {
    List<String> parts = battingRole(p);
    if (p != null && !p.bowlingStyle.isEmpty()) parts.add(p.bowlingStyle);
    StringBuilder b = new StringBuilder();
    for (String part : parts) b.append(b.length() > 0 ? ", " : "").append(part);
    return b.toString();
  }

  /** Account ids look like "user_abc123..."; people read the first eight characters after the prefix. */
  public static String shortId(String accountId) {
    if (accountId == null) return "";
    String id = accountId.startsWith("user_") ? accountId.substring(5) : accountId;
    return id.length() <= 8 ? id : id.substring(0, 8);
  }

  private static boolean sameDay(Calendar a, Calendar b) {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
  }

  private static String format(String pattern, long millis, TimeZone zone) {
    SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
    f.setTimeZone(zone);
    return f.format(new Date(millis));
  }
}
