package com.example.madfinalproject.domain;

import com.example.madfinalproject.domain.Cricket.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derived career and match figures. Identity is the saved team name plus player name. */
public final class PlayerStats {
  private PlayerStats() {}

  public static final class BattingEntry {
    public final String matchId, matchName;
    public final int innings, runs, balls;
    public final boolean out, retired;

    private BattingEntry(Match match, int index, Batting batting) {
      matchId = match.id;
      matchName = match.name;
      innings = index + 1;
      runs = batting.runs;
      balls = batting.balls;
      out = batting.out;
      retired = batting.retired;
    }
  }

  public static final class Player {
    public final String name, team;
    public long runs, balls, fours, sixes, wickets, conceded, ballsBowled, wides, noBalls;
    public int innings, dismissed, notOuts, retiredInnings, highest, fifties, hundreds;
    public int bowlingInnings, bestWickets, bestConceded, fiveFors, catches, runOuts, stumpings;
    public boolean highestNotOut;
    public double oversBowled;
    public final List<BattingEntry> battingHistory = new ArrayList<>();
    public final Map<String, Integer> dismissals = new LinkedHashMap<>();
    private final Set<String> matchIds = new HashSet<>();

    private Player(String name, String team) { this.name = name; this.team = team; }
    public int matches() { return matchIds.size(); }
    public double average() { return dismissed == 0 ? Double.NaN : runs / (double) dismissed; }
    public double strikeRate() { return balls == 0 ? Double.NaN : runs * 100.0 / balls; }
    public double economy() { return oversBowled == 0 ? Double.NaN : conceded / oversBowled; }
    public int fieldingInvolvements() { return catches + runOuts + stumpings; }
    public long mvpPoints() { return runs + 20L * wickets + 8L * fieldingInvolvements(); }

    public List<String> badges() {
      List<String> badges = new ArrayList<>();
      if (runs > 0) badges.add("First recorded run");
      if (fifties + hundreds > 0) badges.add("Half-century scorer");
      if (hundreds > 0) badges.add("Century scorer");
      if (runs >= 500) badges.add("500 recorded runs");
      if (wickets > 0) badges.add("First bowling wicket");
      if (fiveFors > 0) badges.add("Five-wicket innings");
      if (catches >= 5) badges.add("Five recorded catches");
      if (runOuts > 0) badges.add("Run-out contributor");
      if (stumpings > 0) badges.add("Stumping contributor");
      return badges;
    }
  }

  private static Player player(Map<String, Player> players, String name, String team) {
    if (name == null || name.isEmpty()) return null;
    return players.computeIfAbsent(team + "\u0000" + name, ignored -> new Player(name, team));
  }

  public static Player find(String name, String team, List<Match> matches) {
    for (Player player : players(matches))
      if (player.name.equals(name) && player.team.equals(team)) return player;
    return new Player(name, team);
  }

  public static List<Player> players(List<Match> matches) {
    Map<String, Player> players = new LinkedHashMap<>();
    for (Match match : matches) {
      if (match.innings.isEmpty()) continue;
      int active = Math.min(1, match.activeIndex());
      for (int index = 0; index <= active && index < match.innings.size(); index++) {
        Innings innings = match.innings.get(index);
        State state = match.stateAt(index);
        List<Progress> progress = match.progressAt(index);
        Set<String> batters = new HashSet<>(), bowlers = new HashSet<>();
        for (Progress point : progress) {
          batters.add(point.strikerBefore);
          batters.add(point.nonStrikerBefore);
          if (point.extra != Extra.PENALTY) bowlers.add(point.bowler);
          if (point.wickets <= point.wicketsBefore) continue;
          if (point.dismissal != Dismissal.CAUGHT && point.dismissal != Dismissal.RUN_OUT
              && point.dismissal != Dismissal.STUMPED) continue;
          Player fielder = player(players, point.fielder, innings.bowlingTeam);
          if (fielder == null) continue;
          if (point.dismissal == Dismissal.CAUGHT) fielder.catches++;
          else if (point.dismissal == Dismissal.RUN_OUT) fielder.runOuts++;
          else fielder.stumpings++;
          fielder.matchIds.add(match.id);
        }
        for (Map.Entry<String, Batting> entry : state.batting.entrySet()) {
          Batting batting = entry.getValue();
          if (!batters.contains(entry.getKey()) && !batting.out && !batting.retired
              && batting.balls == 0 && batting.runs == 0) continue;
          Player player = player(players, entry.getKey(), innings.battingTeam);
          if (player == null) continue;
          player.matchIds.add(match.id);
          player.innings++;
          player.runs += batting.runs;
          player.balls += batting.balls;
          player.fours += batting.fours;
          player.sixes += batting.sixes;
          if (batting.out) {
            player.dismissed++;
            player.dismissals.put(batting.dismissal, player.dismissals.getOrDefault(batting.dismissal, 0) + 1);
          } else player.notOuts++;
          if (batting.retired) player.retiredInnings++;
          if (batting.runs > player.highest || (batting.runs == player.highest && !batting.out)) {
            player.highest = batting.runs;
            player.highestNotOut = !batting.out;
          }
          if (batting.runs >= 100) player.hundreds++;
          else if (batting.runs >= 50) player.fifties++;
          player.battingHistory.add(new BattingEntry(match, index, batting));
        }
        for (Map.Entry<String, Bowling> entry : state.bowling.entrySet()) {
          Bowling bowling = entry.getValue();
          if (!bowlers.contains(entry.getKey()) && bowling.balls == 0 && bowling.runs == 0 && bowling.wickets == 0) continue;
          Player player = player(players, entry.getKey(), innings.bowlingTeam);
          if (player == null) continue;
          player.matchIds.add(match.id);
          boolean first = player.bowlingInnings == 0;
          player.bowlingInnings++;
          player.wickets += bowling.wickets;
          player.conceded += bowling.runs;
          player.ballsBowled += bowling.balls;
          player.oversBowled += bowling.balls / (double) match.rules.ballsPerOver;
          player.wides += bowling.wides;
          player.noBalls += bowling.noBalls;
          if (first || bowling.wickets > player.bestWickets
              || (bowling.wickets == player.bestWickets && bowling.runs < player.bestConceded)) {
            player.bestWickets = bowling.wickets;
            player.bestConceded = bowling.runs;
          }
          if (bowling.wickets >= 5) player.fiveFors++;
        }
      }
    }
    List<Player> result = new ArrayList<>(players.values());
    result.sort(Comparator.comparing((Player p) -> p.team).thenComparing(p -> p.name));
    return result;
  }

  public static List<Player> mvp(Match match) {
    List<Match> matches = new ArrayList<>();
    matches.add(match);
    List<Player> players = players(matches);
    players.sort(Comparator.comparingLong(Player::mvpPoints).reversed()
        .thenComparing(p -> p.name).thenComparing(p -> p.team));
    return players;
  }
}
