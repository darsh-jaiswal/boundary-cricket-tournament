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
import com.example.madfinalproject.domain.PlayerStats;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only career profile derived from local corrected journals. */
public final class PlayerProfileActivity extends AppCompatActivity {
  public static final String EXTRA_PLAYER = "player", EXTRA_TEAM = "team";
  private Repository repository;
  private LinearLayout body;
  private String report;

  @Override public void onCreate(Bundle saved) {
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
    action("Back", this::finish);
    label("Player profile", 28, true);
    try {
      String name = getIntent().getStringExtra(EXTRA_PLAYER), team = getIntent().getStringExtra(EXTRA_TEAM);
      if (name == null || name.isEmpty() || team == null || team.isEmpty()) {
        label("Select a player and team to view a career profile.", 16, false);
        return;
      }
      for (Cricket.Team saved : repository.teams()) if (saved.id.equals(team)) { team = saved.name; break; }
      PlayerStats.Player player = PlayerStats.find(name, team, repository.matches());
      label(name, 25, true);
      label(team, 17, false);
      LinearLayout metrics = new LinearLayout(this);
      for (String metric : new String[]{player.runs + "\nRUNS", player.wickets + "\nWICKETS", decimal(player.average()) + "\nAVERAGE", decimal(player.strikeRate()) + "\nSTRIKE RATE"}) {
        TextView tile = new TextView(this); tile.setText(metric); tile.setTextSize(15); tile.setTextColor(0xff147653);
        tile.setGravity(android.view.Gravity.CENTER); tile.setPadding(dp(4), dp(15), dp(4), dp(15));
        metrics.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
      }
      body.addView(metrics);
      if (!player.battingHistory.isEmpty()) {
        label("Innings form ? runs", 19, true);
        int count = Math.min(10, player.battingHistory.size()); double[] form = new double[count]; String[] names = new String[count];
        for (int i = 0; i < count; i++) { form[i] = player.battingHistory.get(player.battingHistory.size() - count + i).runs; names[i] = String.valueOf(i + 1); }
        body.addView(new StatsVisualView(this, names, form, false), new LinearLayout.LayoutParams(-1, dp(220)));
        label("Last " + count + " innings in saved match order. A zero is a recorded innings, not missing data.", 12, false);
      }
      action("Refresh profile", this::load);
      StringBuilder text = new StringBuilder("Boundary player profile\n").append(name).append(" · ").append(team);
      section(text, "Recorded participation", player.matches() + " matches with recorded batting, bowling or credited fielding.\n"
          + "Players are identified by saved team name and player name; this profile covers this team only.");
      String high = player.innings == 0 ? "—" : player.highest + (player.highestNotOut ? "*" : "");
      section(text, "Batting", player.innings + " innings · " + player.dismissed + " dismissed · " + player.notOuts + " not out\n"
          + player.runs + " runs from " + player.balls + " balls\nHighest " + high + " · Average " + decimal(player.average())
          + " · Strike rate " + decimal(player.strikeRate()) + "\n" + player.fours + " fours · " + player.sixes + " sixes\n"
          + player.fifties + " fifties (50–99) · " + player.hundreds + " centuries (100+)\n"
          + player.retiredInnings + " innings currently marked retired; retired batters remain not out unless a dismissal is recorded.");
      section(text, "Bowling", player.bowlingInnings + " innings · " + player.wickets + " wickets\n"
          + player.conceded + " conceded · " + player.ballsBowled + " legal balls\nEconomy " + decimal(player.economy())
          + " · Best " + (player.bowlingInnings == 0 ? "—" : player.bestWickets + "/" + player.bestConceded)
          + "\n" + player.fiveFors + " five-wicket innings\n" + player.wides + " wide runs · " + player.noBalls + " no-ball penalty runs\n"
          + "Economy uses each match's own balls per over, including mixed formats. Bowler wickets exclude run-outs and obstruction.");
      section(text, "Scoring & pressure", "Boundary runs " + decimal(player.boundaryPercent()) + "% of batter runs\n"
          + "Batting dots " + player.battingDots + " (" + decimal(player.battingDotPercent()) + "%)\n"
          + "Bowling dots " + player.bowlingDots + " (" + decimal(player.bowlingDotPercent()) + "% of legal balls; no bowler runs conceded)");
      section(text, "Fielding", player.catches + " catches · " + player.runOuts + " run-out involvements · "
          + player.stumpings + " stumpings\nOnly named fielders on effective dismissals receive credit; missing attribution remains uncredited.");
      List<String> badges = player.badges();
      section(text, "Milestones", badges.isEmpty() ? "Score a recorded performance to earn a milestone." : android.text.TextUtils.join("\n", badges));
      if (!player.dismissals.isEmpty()) {
        StringBuilder dismissals = new StringBuilder();
        for (Map.Entry<String, Integer> entry : player.dismissals.entrySet()) {
          if (dismissals.length() > 0) dismissals.append("\n");
          dismissals.append(entry.getKey().replace('_', ' ')).append(": ").append(entry.getValue());
        }
        section(text, "Dismissals", dismissals.toString());
      }
      if (!player.battingHistory.isEmpty()) {
        StringBuilder history = new StringBuilder();
        for (PlayerStats.BattingEntry entry : player.battingHistory) {
          if (history.length() > 0) history.append("\n\n");
          history.append(entry.matchName).append(" · innings ").append(entry.innings).append("\n")
              .append(entry.runs).append(entry.out ? "" : "*").append(" (").append(entry.balls).append(" balls)")
              .append(entry.retired ? " · retired" : "");
        }
        section(text, "Batting scorecards", history.toString());
      }
      long importedRuns = 0, importedBalls = 0, importedWickets = 0, importedConceded = 0, importedBowled = 0;
      int imports = 0;
      for (Cricket.Scorebook sheet : repository.list("scorebook", Cricket.Scorebook.class))
        if (team.equals(sheet.team)) for (Cricket.ImportedPlayer item : sheet.players) if (name.equals(item.name)) {
          imports++;
          importedRuns += item.runs; importedBalls += item.balls; importedWickets += item.wickets;
          importedConceded += item.conceded; importedBowled += item.bowled;
        }
      if (imports > 0) section(text, "Imported summaries", imports + " imported player rows\n" + importedRuns + " runs · "
          + importedBalls + " balls faced · " + importedWickets + " wickets\n" + importedConceded + " conceded · "
          + importedBowled + " balls bowled\nShown separately: imports do not record innings, dismissals or balls per over, and may overlap scored matches. They do not affect career averages or milestones above.");
      section(text, "How these figures work", "Figures reflect active innings after corrections, including ongoing matches. Reversed or retained deliveries after innings completion are excluded. An average is unavailable until a dismissal; rates require balls. Batters recorded at the crease can have a zero-ball innings. Unrecorded fielding appearances and unused squad membership do not count as participation.");
      report = text.toString();
      action("Share player profile", () -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
          .setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share player profile")));
    } catch (RuntimeException error) { label("Could not load profile: " + error.getMessage(), 16, false); }
  }

  private String decimal(double value) { return Double.isNaN(value) ? "—" : String.format(Locale.US, "%.2f", value); }
  private void section(StringBuilder report, String title, String text) {
    label(title, 19, true);
    TextView view = label(text, 14, false);
    view.setTextIsSelectable(true);
    report.append("\n\n").append(title).append("\n").append(text);
  }
  private TextView label(String text, int size, boolean bold) {
    TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(0xff142a24);
    view.setPadding(0, dp(10), 0, dp(6)); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    body.addView(view); return view;
  }
  private void action(String title, Runnable callback) {
    Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setTextColor(0xffffffff);
    button.setBackgroundTintList(ColorStateList.valueOf(0xff147653)); button.setOnClickListener(v -> callback.run());
    body.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  @Override protected void onDestroy() { if (repository != null) repository.close(); super.onDestroy(); }
}
