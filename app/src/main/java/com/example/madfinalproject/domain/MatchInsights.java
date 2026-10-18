package com.example.madfinalproject.domain;

import com.example.madfinalproject.domain.Cricket.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All analytics consume effective journal replay; retained/reversed balls never enter here. */
public final class MatchInsights {
  private MatchInsights() {}

  public static final class Phase {
    public final int firstOver, lastOver;
    public int runs, wickets, legal, dots, boundaryRuns;
    Phase(int first, int last) { firstOver = first; lastOver = last; }
    public double rate(int ballsPerOver) { return legal == 0 ? Double.NaN : runs * (double) ballsPerOver / legal; }
  }

  public static final class FaceOff {
    public final String batter, bowler;
    public int runs, balls, dots, fours, sixes, wickets, conceded, legal;
    FaceOff(String batter, String bowler) { this.batter = batter; this.bowler = bowler; }
    public double strikeRate() { return balls == 0 ? Double.NaN : runs * 100.0 / balls; }
    public double economy(int ballsPerOver) { return legal == 0 ? Double.NaN : conceded * (double) ballsPerOver / legal; }
  }

  public static List<Phase> phases(List<Progress> points, int ballsPerOver, int overs, int powerplay) {
    if (ballsPerOver <= 0 || overs <= 0) throw new IllegalArgumentException("Invalid innings rules");
    List<Phase> rows = new ArrayList<>();
    int opening = Math.min(overs, Math.max(0, powerplay));
    int deathStart = Math.max(opening, overs - Math.max(1, overs / 5));
    if (opening > 0) rows.add(new Phase(1, opening));
    if (deathStart > opening) rows.add(new Phase(opening + 1, deathStart));
    if (overs > deathStart) rows.add(new Phase(deathStart + 1, overs));
    for (Progress p : points) {
      int over = p.legalBefore / ballsPerOver + 1;
      for (Phase row : rows) if (over >= row.firstOver && over <= row.lastOver) {
        row.runs += p.runs - p.runsBefore;
        row.wickets += p.wickets - p.wicketsBefore;
        row.legal += p.legal - p.legalBefore;
        if (p.legal > p.legalBefore && p.runs == p.runsBefore) row.dots++;
        if (p.boundary) row.boundaryRuns += p.batRuns;
      }
    }
    return rows;
  }

  public static List<FaceOff> faceOffs(List<Progress> points) {
    Map<String, FaceOff> rows = new LinkedHashMap<>();
    for (Progress p : points) {
      if (p.extra == Extra.PENALTY || p.bowler == null || p.bowler.isEmpty()) continue;
      FaceOff row = rows.computeIfAbsent(p.strikerBefore + "\u0000" + p.bowler,
          ignored -> new FaceOff(p.strikerBefore, p.bowler));
      row.runs += p.batRuns;
      row.conceded += p.bowlerRuns;
      row.legal += p.legal - p.legalBefore;
      if (p.extra != Extra.WIDE) { row.balls++; if (p.batRuns == 0) row.dots++; }
      if (p.boundary && p.batRuns == 4) row.fours++;
      if (p.boundary && p.batRuns == 6) row.sixes++;
      if (p.wickets > p.wicketsBefore && p.dismissal != Dismissal.RUN_OUT
          && p.dismissal != Dismissal.OBSTRUCTING && p.dismissal != Dismissal.NONE) row.wickets++;
    }
    return new ArrayList<>(rows.values());
  }

  /** Batter-run distribution: dots, 1s, 2s, 3s, fours, sixes, other. Extras excluded. */
  public static int[] distribution(List<Progress> points) {
    int[] bins = new int[7];
    for (Progress p : points) {
      if (p.extra == Extra.WIDE || p.extra == Extra.PENALTY) continue;
      int bin = p.batRuns >= 0 && p.batRuns <= 3 ? p.batRuns
          : p.boundary && p.batRuns == 4 ? 4 : p.boundary && p.batRuns == 6 ? 5 : 6;
      bins[bin]++;
    }
    return bins;
  }
}
