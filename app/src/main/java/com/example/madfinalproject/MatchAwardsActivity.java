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

/** Transparent local awards from corrected match performance. */
public final class MatchAwardsActivity extends AppCompatActivity {
  public static final String EXTRA_MATCH_ID = "match";
  private Repository repository;
  private LinearLayout body;
  private String report;

  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    repository = new Repository(this);
    ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(22), dp(18), dp(22), dp(24)); body.setBackgroundColor(0xfff3f6ef); scroll.addView(body);
    setContentView(scroll); UiInsets.apply(scroll); load();
  }

  private void load() {
    body.removeAllViews(); report = null; action("Back to match", this::finish); label("Match awards", 28, true);
    try {
      Cricket.Match match = null; String id = getIntent().getStringExtra(EXTRA_MATCH_ID);
      for (Cricket.Match item : repository.matches()) if (item.id.equals(id)) match = item;
      if (match == null) { label("Match could not be found. Return and select a saved match.", 16, false); return; }
      label(match.name, 21, true); action("Refresh awards", this::load);
      List<PlayerStats.Player> players = PlayerStats.mvp(match);
      boolean completed = match.mainCompleted() && !"Live".equals(match.result());
      String status = completed ? "Completed match · suggested awards" : "Live match · provisional suggestions";
      label(status, 16, true);
      String rules = "Boundary MVP points = batting runs + 20 per bowling wicket + 8 per credited catch, run-out involvement or stumping. Run-outs are not bowler wickets. Tied point totals share the position; alphabetical order only arranges tied players. This original formula is a suggestion, not an official tournament award.";
      label(rules, 14, false);
      if (players.isEmpty() || players.get(0).mvpPoints() == 0) {
        label("No positive award points yet. Record runs, bowling wickets or attributed fielding dismissals.", 16, false); return;
      }
      StringBuilder text = new StringBuilder("Boundary match awards\n").append(match.name).append("\n").append(status).append("\n\n").append(rules);
      long highest = players.get(0).mvpPoints();
      int rank = 0; long previous = Long.MIN_VALUE;
      for (int i = 0; i < players.size(); i++) {
        PlayerStats.Player player = players.get(i);
        if (player.mvpPoints() != previous) rank = i + 1;
        previous = player.mvpPoints();
        String title = "#" + rank + " · " + player.name + " · " + player.team;
        if (player.mvpPoints() == highest) title = "Suggested MVP · " + title;
        String detail = player.mvpPoints() + " points = " + player.runs + " batting + " + (20L * player.wickets)
            + " bowling + " + (8L * player.fieldingInvolvements()) + " fielding\n" + player.wickets + " bowling wickets · "
            + player.catches + " catches · " + player.runOuts + " run-out involvements · " + player.stumpings + " stumpings";
        label(title, 18, true); label(detail, 14, false); text.append("\n\n").append(title).append("\n").append(detail);
        final String playerName = player.name, teamName = player.team;
        action("View " + player.name + " profile", () -> startActivity(new Intent(this, PlayerProfileActivity.class)
            .putExtra(PlayerProfileActivity.EXTRA_PLAYER, playerName).putExtra(PlayerProfileActivity.EXTRA_TEAM, teamName)));
      }
      long bestBatting = 0, bestBowling = 0; int bestFielding = 0;
      for (PlayerStats.Player player : players) {
        bestBatting = Math.max(bestBatting, player.runs); bestBowling = Math.max(bestBowling, player.wickets);
        bestFielding = Math.max(bestFielding, player.fieldingInvolvements());
      }
      category(text, "Most runs", players, bestBatting, 0);
      category(text, "Most bowling wickets", players, bestBowling, 1);
      category(text, "Most credited fielding involvements", players, bestFielding, 2);
      String note = "Suggestions use effective active innings after corrections and can change while scoring continues. Fielding with no named attribution receives no points. Unscored matches and imported scorebook summaries are excluded.";
      label(note, 13, false); text.append("\n\n").append(note); report = text.toString();
      action("Share award suggestions", () -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
          .setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share match awards")));
    } catch (RuntimeException error) { label("Could not load awards: " + error.getMessage(), 16, false); }
  }

  private void category(StringBuilder text, String title, List<PlayerStats.Player> players, long highest, int kind) {
    if (highest <= 0) return;
    StringBuilder winners = new StringBuilder();
    for (PlayerStats.Player player : players) {
      long value = kind == 0 ? player.runs : kind == 1 ? player.wickets : player.fieldingInvolvements();
      if (value != highest) continue;
      if (winners.length() > 0) winners.append("\n");
      winners.append(player.name).append(" · ").append(player.team).append(": ").append(value);
    }
    label(title, 19, true); label(winners.toString(), 14, false); text.append("\n\n").append(title).append("\n").append(winners);
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
