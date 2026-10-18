package com.example.madfinalproject;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket;
import com.example.madfinalproject.domain.MatchInsights;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local match analysis derived from the effective scoring journal. */
public final class MatchAnalysisActivity extends AppCompatActivity {
  public static final String EXTRA_MATCH_ID = "match";
  private Repository repository;
  private LinearLayout body;
  private String report;
  private ComparisonChartView.Mode chartMode = ComparisonChartView.Mode.MANHATTAN;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    repository = new Repository(this);
    ScrollView scroll = new ScrollView(this);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(22), dp(18), dp(22), dp(24));
    body.setBackgroundColor(0xfff3f6ef);
    scroll.addView(body);
    setContentView(scroll);
    UiInsets.apply(scroll);
    load();
  }

  private void load() {
    body.removeAllViews();
    report = null;
    action("Back to match", this::finish);
    label("Match analysis", 28, true);
    label("Runs per over and cumulative scoring from Boundary's effective delivery history. Orange markers show overs or deliveries with wickets.", 14, false);
    try {
      Cricket.Match match = null;
      String id = getIntent().getStringExtra(EXTRA_MATCH_ID);
      for (Cricket.Match item : repository.matches()) if (item.id.equals(id)) match = item;
      if (match == null) {
        label("Match could not be found. Return and select a saved match.", 16, false);
        return;
      }
      if (match.rules == null || match.rules.ballsPerOver <= 0)
        throw new IllegalArgumentException("This match has invalid balls-per-over rules.");
      Map<String, String> names = new HashMap<>();
      for (Cricket.Team team : repository.teams()) names.put(team.id, team.name);
      String title = match.name.isEmpty()
          ? names.getOrDefault(match.teamA, match.teamA) + " v " + names.getOrDefault(match.teamB, match.teamB)
          : match.name;
      label(title, 20, true);
      action("Refresh analysis", this::load);
      if (match.innings.isEmpty()) {
        label("No innings have started. Score a delivery to populate the charts.", 16, false);
        return;
      }
      StringBuilder text = new StringBuilder("Boundary match analysis\n").append(title);
      int active = match.activeIndex();
      label("Match progress", 21, true);
      LinearLayout modes = new LinearLayout(this);
      for (ComparisonChartView.Mode mode : ComparisonChartView.Mode.values()) {
        Button tab = new Button(this); tab.setText(mode.name().replace('_', ' ')); tab.setTextSize(10); tab.setAllCaps(false);
        tab.setTextColor(chartMode == mode ? 0xffffffff : 0xff147653);
        tab.setBackgroundTintList(ColorStateList.valueOf(chartMode == mode ? 0xff147653 : 0xffe1e9df));
        tab.setOnClickListener(v -> { chartMode = mode; load(); });
        modes.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
      }
      body.addView(modes);
      body.addView(new ComparisonChartView(this, match.progressAt(0), active >= 1 ? match.progressAt(1) : java.util.Collections.emptyList(),
          match.innings.get(0).battingTeam, active >= 1 ? match.innings.get(1).battingTeam : "Chase not started",
          match.rules.ballsPerOver, chartMode), new LinearLayout.LayoutParams(-1, dp(285)));

      for (int index = 0; index <= active && index < match.innings.size(); index++) {
        Cricket.Innings innings = match.innings.get(index);
        Cricket.State state = match.stateAt(index);
        List<Cricket.Progress> points = match.progressAt(index);
        String heading = "Innings " + (index + 1) + " · " + names.getOrDefault(innings.battingTeam, innings.battingTeam);
        label(heading, 21, true);
        String rate = state.legal == 0 ? "unavailable (no legal balls yet)"
            : String.format(Locale.US, "%.2f", state.runs * (double) match.rules.ballsPerOver / state.legal);
        String summary = state.runs + "/" + state.wickets + " in " + state.overs(match.rules)
            + " overs · Run rate " + rate;
        label(summary, 15, false);
        text.append("\n\n").append(heading).append("\n").append(summary);
        if (points.isEmpty()) {
          label("No effective deliveries in this innings yet.", 15, false);
          text.append("\nNo effective deliveries yet.");
          continue;
        }
        label("Phases of play", 19, true);
        for (MatchInsights.Phase phase : MatchInsights.phases(points, match.rules.ballsPerOver,
            match.rules.overs, match.rules.powerplay)) {
          label("Overs " + phase.firstOver + "?" + phase.lastOver + "    " + phase.runs + "/" + phase.wickets
              + "    RR " + decimal(phase.rate(match.rules.ballsPerOver)) + "\n" + phase.dots
              + " team dot balls ? " + phase.boundaryRuns + " boundary runs", 14, false);
        }
        label("Types of runs ? balls faced", 19, true);
        int[] distribution = MatchInsights.distribution(points);
        double[] bars = new double[distribution.length]; for (int i = 0; i < bars.length; i++) bars[i] = distribution[i];
        body.addView(new StatsVisualView(this, new String[]{"Dots", "1s", "2s", "3s", "4s", "6s", "Other"}, bars, false),
            new LinearLayout.LayoutParams(-1, dp(210)));
        label("Face off ? batter vs bowler", 19, true);
        for (MatchInsights.FaceOff row : MatchInsights.faceOffs(points))
          label(row.batter + " vs " + row.bowler + "\n" + row.runs + " runs ? " + row.balls + " faced ? "
              + row.dots + " dots ? " + row.fours + "/" + row.sixes + " boundaries ? SR " + decimal(row.strikeRate())
              + "\nBowler: " + row.conceded + " conceded ? " + row.wickets + " wickets ? Econ "
              + decimal(row.economy(match.rules.ballsPerOver)), 14, false);
        long[] zones = new long[8]; long tracked = 0;
        for (Cricket.Progress p : points) if (p.zone >= 0 && p.zone < 8 && p.batRuns > 0) { zones[p.zone] += p.batRuns; tracked += p.batRuns; }
        label("Wagon wheel ? recorded sectors", 19, true);
        if (tracked == 0) label("No scoring zones recorded. Hold a scoring button to record a shot sector.", 14, false);
        else { double[] z = new double[8]; for (int i = 0; i < 8; i++) z[i] = zones[i];
          body.addView(new StatsVisualView(this, new String[]{"0", "1", "2", "3", "4", "5", "6", "7"}, z, true),
              new LinearLayout.LayoutParams(-1, dp(280)));
          label(tracked + " batter runs have recorded sectors. Untracked runs are excluded; sectors show totals, not exact shot paths.", 12, false);
        }
        label("Manhattan · runs per over", 17, true);
        body.addView(new MatchChartView(this, points, match.rules.ballsPerOver, MatchChartView.Mode.MANHATTAN),
            new LinearLayout.LayoutParams(-1, dp(245)));
        label("Worm · cumulative score", 17, true);
        body.addView(new MatchChartView(this, points, match.rules.ballsPerOver, MatchChartView.Mode.WORM),
            new LinearLayout.LayoutParams(-1, dp(245)));
        label("Over summary", 17, true);
        String table = overTable(points, match.rules.ballsPerOver);
        TextView rows = label(table, 14, false);
        rows.setTextIsSelectable(true);
        text.append("\n").append(table);
      }
      String note = "Only effective deliveries in active innings are included. Reversed decisions and retained deliveries after innings completion are excluded. Extras can add runs without advancing the worm's legal-ball position. A partial over may contain fewer legal balls.";
      label(note, 13, false);
      text.append("\n\n").append(note);
      report = text.toString();
      action("Share analysis", () -> {
        if (report != null) startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share match analysis"));
      });
    } catch (RuntimeException error) {
      label("Could not load match analysis: " + error.getMessage(), 16, false);
    }
  }

  private String overTable(List<Cricket.Progress> points, int ballsPerOver) {
    int count = 0;
    for (Cricket.Progress point : points) count = Math.max(count, point.legalBefore / ballsPerOver + 1);
    int[] runs = new int[count], wickets = new int[count], balls = new int[count], totals = new int[count], fallen = new int[count];
    for (Cricket.Progress point : points) {
      int over = point.legalBefore / ballsPerOver;
      runs[over] += point.runs - point.runsBefore;
      wickets[over] += point.wickets - point.wicketsBefore;
      balls[over] += point.legal - point.legalBefore;
      totals[over] = point.runs;
      fallen[over] = point.wickets;
    }
    StringBuilder table = new StringBuilder();
    for (int over = 0; over < count; over++) {
      if (over > 0) table.append("\n\n");
      table.append("Over ").append(over + 1).append(": ").append(runs[over]).append(" runs, ")
          .append(wickets[over]).append(" wickets, ").append(balls[over]).append(" legal balls")
          .append("\nCumulative: ").append(totals[over]).append("/").append(fallen[over]);
    }
    return table.toString();
  }

  private String decimal(double value) { return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "?"; }

  private TextView label(String text, int size, boolean bold) {
    TextView view = new TextView(this);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(0xff142a24);
    view.setPadding(0, dp(10), 0, dp(6));
    if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    body.addView(view);
    return view;
  }

  private void action(String text, Runnable callback) {
    Button button = new Button(this);
    button.setText(text);
    button.setAllCaps(false);
    button.setTextColor(0xffffffff);
    button.setBackgroundTintList(ColorStateList.valueOf(0xff147653));
    button.setOnClickListener(v -> callback.run());
    body.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }

  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

  @Override
  protected void onDestroy() {
    if (repository != null) repository.close();
    super.onDestroy();
  }
}
