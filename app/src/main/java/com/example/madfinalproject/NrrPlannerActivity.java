package com.example.madfinalproject;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket;
import com.example.madfinalproject.domain.NrrScenario;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compares an unsaved NRR scenario with the tournament's current standings. */
public final class NrrPlannerActivity extends AppCompatActivity {
  public static final String EXTRA_TOURNAMENT_ID = "tournament";
  private final List<Cricket.Row> teams = new ArrayList<>(), rivals = new ArrayList<>();
  private final Map<String, String> names = new HashMap<>();
  private final int[] ballOptions = {4, 5, 6, 8};
  private Repository repository;
  private LinearLayout body;
  private Spinner team, rival, ballsPerOver;
  private EditText runsFor, ballsFaced, runsAgainst, ballsBowled;
  private TextView result;
  private Button share;
  private String report;

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
    action("Back to tournament", this::finish);
    label("NRR scenario planner", 28, true);
    label("Explore one hypothetical match using this tournament's recorded totals.", 14, false);
    try {
      String tournamentId = getIntent().getStringExtra(EXTRA_TOURNAMENT_ID);
      Cricket.Tournament tournament = null;
      for (Cricket.Tournament item : repository.tournaments())
        if (item.id.equals(tournamentId)) tournament = item;
      if (tournament == null) {
        label("Tournament could not be found. Return and select a saved tournament.", 16, false);
        return;
      }
      for (Cricket.Team item : repository.teams()) names.put(item.id, item.name);
      teams.addAll(Cricket.standings(tournament, repository.matches()));
      label(tournament.name, 20, true);
      if (teams.isEmpty()) {
        label("Register teams in this tournament to use the planner.", 16, false);
        return;
      }
      team = picker("Your team", teamNames(teams));
      rival = picker("Compare with rival's current NRR", new ArrayList<>());
      ballsPerOver = picker("Balls per over in the hypothetical match", java.util.Arrays.asList("4", "5", "6", "8"));
      ballsPerOver.setSelection(2);
      label("Enter runs from your team's perspective. Enter legal balls as whole numbers, for example 43 balls for 7 overs and 1 ball in a six-ball over.", 14, false);
      label("If either innings is all out, enter that innings' full allocated ball limit for NRR, including any reduced allocation. Otherwise enter actual legal balls. Wides and no-balls do not count as legal balls.", 14, false);
      runsFor = field("Your team's runs scored");
      ballsFaced = field("NRR balls faced (full allocation if all out)");
      runsAgainst = field("Runs conceded");
      ballsBowled = field("NRR balls bowled (full allocation if opponent all out)");
      action("Calculate scenario", this::calculate);
      result = label("Enter the scenario and tap Calculate.", 16, false);
      result.setTextIsSelectable(true);
      share = action("Share scenario", () -> {
        if (report == null) return;
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share NRR scenario"));
      });
      share.setEnabled(false);
      label("This is an estimate for one team's NRR, not a qualification or rank guarantee. Points, other results, group rules, penalties and special rain/DLS adjustments are not simulated. The rival comparison uses its current recorded NRR, even if it is the opponent in this scenario. No scores or standings are changed.", 13, false);
      if (saved != null) {
        selectTeam(saved.getString("team"));
        ballsPerOver.setSelection(saved.getInt("bpo", 2));
        runsFor.setText(saved.getString("runsFor", ""));
        ballsFaced.setText(saved.getString("ballsFaced", ""));
        runsAgainst.setText(saved.getString("runsAgainst", ""));
        ballsBowled.setText(saved.getString("ballsBowled", ""));
      }
      updateRivals(saved == null ? null : saved.getString("rival"));
      team.setOnItemSelectedListener(listener(() -> updateRivals(null)));
      rival.setOnItemSelectedListener(listener(this::invalidateResult));
      ballsPerOver.setOnItemSelectedListener(listener(this::invalidateResult));
    } catch (RuntimeException error) {
      label("Could not load NRR planner: " + error.getMessage(), 16, false);
    }
  }

  private void calculate() {
    report = null;
    share.setEnabled(false);
    try {
      Cricket.Row selected = teams.get(team.getSelectedItemPosition());
      int scored = number(runsFor), faced = number(ballsFaced), conceded = number(runsAgainst), bowled = number(ballsBowled);
      int bpo = ballOptions[ballsPerOver.getSelectedItemPosition()];
      NrrScenario.Result scenario = NrrScenario.calculate(selected, scored, faced, conceded, bowled, bpo);
      StringBuilder text = new StringBuilder("Boundary NRR scenario\n")
          .append(names.getOrDefault(selected.team, selected.team))
          .append("\nCurrent NRR: ").append(format(scenario.current))
          .append("\nProjected NRR: ").append(format(scenario.projected))
          .append("\nChange: ").append(format(scenario.delta))
          .append("\n\nScored ").append(scored).append(" in ").append(faced)
          .append(" NRR balls; conceded ").append(conceded).append(" in ").append(bowled)
          .append(" NRR balls. ").append(bpo).append(" balls per over.");
      if (!rivals.isEmpty()) {
        Cricket.Row comparison = rivals.get(rival.getSelectedItemPosition());
        double rivalNrr = comparison.nrr(bpo);
        text.append("\n\n").append(names.getOrDefault(comparison.team, comparison.team))
            .append(" current NRR: ").append(format(rivalNrr))
            .append("\nProjected gap versus rival's current NRR: ")
            .append(format(scenario.projected - rivalNrr));
      }
      text.append("\n\nHypothetical only. The rival's future NRR and qualification are not calculated. Use full allocated balls for all-out innings. Rain/DLS adjustments require tournament-specific calculation.");
      report = text.toString();
      result.setText(report);
      share.setEnabled(true);
    } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
      result.setText(error.getMessage());
    }
  }

  private int number(EditText field) {
    try {
      return Integer.parseInt(field.getText().toString().trim());
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("Enter a whole number from 0 to 2147483647 for " + field.getHint() + ".");
    }
  }

  private void updateRivals(String selectedId) {
    if (team.getSelectedItemPosition() < 0) return;
    String teamId = teams.get(team.getSelectedItemPosition()).team;
    if (selectedId == null && rival.getSelectedItemPosition() >= 0
        && rival.getSelectedItemPosition() < rivals.size())
      selectedId = rivals.get(rival.getSelectedItemPosition()).team;
    rivals.clear();
    for (Cricket.Row row : teams) if (!row.team.equals(teamId)) rivals.add(row);
    rival.setAdapter(adapter(teamNames(rivals)));
    rival.setEnabled(!rivals.isEmpty());
    for (int i = 0; i < rivals.size(); i++)
      if (rivals.get(i).team.equals(selectedId)) rival.setSelection(i);
    invalidateResult();
  }

  private List<String> teamNames(List<Cricket.Row> rows) {
    List<String> labels = new ArrayList<>();
    for (Cricket.Row row : rows) labels.add(names.getOrDefault(row.team, row.team));
    return labels;
  }

  private void selectTeam(String id) {
    for (int i = 0; i < teams.size(); i++) if (teams.get(i).team.equals(id)) team.setSelection(i);
  }

  private void invalidateResult() {
    report = null;
    if (result != null) result.setText("Tap Calculate to update the scenario.");
    if (share != null) share.setEnabled(false);
  }

  private AdapterView.OnItemSelectedListener listener(Runnable action) {
    return new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { action.run(); }
      public void onNothingSelected(AdapterView<?> parent) { invalidateResult(); }
    };
  }

  private ArrayAdapter<String> adapter(List<String> values) {
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    return adapter;
  }

  private Spinner picker(String title, List<String> values) {
    TextView caption = label(title, 14, true);
    Spinner spinner = new Spinner(this);
    spinner.setId(View.generateViewId());
    caption.setLabelFor(spinner.getId());
    spinner.setAdapter(adapter(values));
    body.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
    return spinner;
  }

  private EditText field(String title) {
    TextView caption = label(title, 14, true);
    EditText field = new EditText(this);
    field.setId(View.generateViewId());
    caption.setLabelFor(field.getId());
    field.setHint(title);
    field.setInputType(InputType.TYPE_CLASS_NUMBER);
    field.setSingleLine(true);
    body.addView(field, new LinearLayout.LayoutParams(-1, dp(54)));
    field.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) { invalidateResult(); }
      public void afterTextChanged(Editable value) {}
    });
    return field;
  }

  private TextView label(String value, int size, boolean bold) {
    TextView text = new TextView(this);
    text.setText(value);
    text.setTextSize(size);
    text.setTextColor(0xff142a24);
    text.setPadding(0, dp(10), 0, dp(6));
    if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    body.addView(text);
    return text;
  }

  private Button action(String title, Runnable action) {
    Button button = new Button(this);
    button.setText(title);
    button.setAllCaps(false);
    button.setTextColor(0xffffffff);
    button.setBackgroundTintList(ColorStateList.valueOf(0xff147653));
    button.setOnClickListener(v -> action.run());
    body.addView(button, new LinearLayout.LayoutParams(-1, -2));
    return button;
  }

  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private String format(double value) { return String.format(Locale.US, "%+.3f", value); }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    if (runsFor != null) {
      state.putString("team", teams.get(team.getSelectedItemPosition()).team);
      if (!rivals.isEmpty()) state.putString("rival", rivals.get(rival.getSelectedItemPosition()).team);
      state.putInt("bpo", ballsPerOver.getSelectedItemPosition());
      state.putString("runsFor", runsFor.getText().toString());
      state.putString("ballsFaced", ballsFaced.getText().toString());
      state.putString("runsAgainst", runsAgainst.getText().toString());
      state.putString("ballsBowled", ballsBowled.getText().toString());
    }
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy() {
    if (repository != null) repository.close();
    super.onDestroy();
  }
}
