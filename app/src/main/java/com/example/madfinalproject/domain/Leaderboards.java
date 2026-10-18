package com.example.madfinalproject.domain;

import com.example.madfinalproject.domain.Cricket.*;
import java.util.*;

/** Tournament figures derived from corrected match journals; no duplicate stored totals. */
public final class Leaderboards {
  private Leaderboards() {}

  public static final class Player {
    public final String name, team;
    public int runs, balls, fours, sixes, wickets, conceded, deliveries, innings;
    public double oversBowled;

    Player(String name, String team) {
      this.name = name;
      this.team = team;
    }

    public double strikeRate() { return balls == 0 ? 0 : runs * 100.0 / balls; }
    public double economy() { return oversBowled == 0 ? 0 : conceded / oversBowled; }
  }

  public static List<Player> players(String tournamentId, List<Match> matches) {
    Map<String, Player> totals = new LinkedHashMap<>();
    for (Match match : matches) {
      if (!tournamentId.equals(match.tournament)) continue;
      for (int index = 0; index < match.innings.size(); index++) {
        if (index > Math.min(1, match.activeIndex())) break;
        Innings innings = match.innings.get(index);
        State state = match.stateAt(index);
        for (Map.Entry<String, Batting> entry : state.batting.entrySet()) {
          if (entry.getKey().isEmpty()) continue;
          Batting b = entry.getValue();
          Player p = totals.computeIfAbsent(innings.battingTeam + "\u0000" + entry.getKey(),
              key -> new Player(entry.getKey(), innings.battingTeam));
          p.runs += b.runs;
          p.balls += b.balls;
          p.fours += b.fours;
          p.sixes += b.sixes;
          if (b.balls > 0 || b.runs > 0 || b.out) p.innings++;
        }
        for (Map.Entry<String, Bowling> entry : state.bowling.entrySet()) {
          if (entry.getKey().isEmpty()) continue;
          Bowling b = entry.getValue();
          Player p = totals.computeIfAbsent(innings.bowlingTeam + "\u0000" + entry.getKey(),
              key -> new Player(entry.getKey(), innings.bowlingTeam));
          p.wickets += b.wickets;
          p.conceded += b.runs;
          p.deliveries += b.balls;
          p.oversBowled += b.balls / (double) match.rules.ballsPerOver;
        }
      }
    }
    return new ArrayList<>(totals.values());
  }

  public static List<Player> batting(String tournamentId, List<Match> matches) {
    List<Player> rows = players(tournamentId, matches);
    rows.removeIf(p -> p.innings == 0);
    rows.sort(Comparator.comparingInt((Player p) -> p.runs).reversed()
        .thenComparing(Comparator.comparingDouble(Player::strikeRate).reversed())
        .thenComparing(p -> p.name).thenComparing(p -> p.team));
    return rows;
  }

  public static List<Player> bowling(String tournamentId, List<Match> matches) {
    List<Player> rows = players(tournamentId, matches);
    rows.removeIf(p -> p.deliveries == 0 && p.conceded == 0 && p.wickets == 0);
    rows.sort(Comparator.comparingInt((Player p) -> p.wickets).reversed()
        .thenComparingDouble(Player::economy).thenComparing(p -> p.name)
        .thenComparing(p -> p.team));
    return rows;
  }
}
