package com.example.madfinalproject;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Attendance;
import com.example.madfinalproject.domain.Cricket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/** Local match-squad availability notes protected by the scoring device's official PIN. */
public final class AttendanceActivity extends AppCompatActivity {
  public static final String EXTRA_MATCH_ID = "match", EXTRA_ROLE = "role";
  private static final String[] LABELS = {"Not confirmed", "Available", "Unavailable"};
  private Repository repository;
  private LinearLayout body;
  private Cricket.Match match;
  private Attendance attendance;
  private TextView summary;
  private String role;
  private List<String> playersA, playersB;

  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    repository = new Repository(this);
    role = getIntent().getStringExtra(EXTRA_ROLE);
    if (role == null) role = getSharedPreferences("MainActivity", MODE_PRIVATE).getString("role", "Organizer");
    ScrollView scroll = new ScrollView(this);
    body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(22), dp(18), dp(22), dp(24)); body.setBackgroundColor(0xfff3f6ef); scroll.addView(body);
    setContentView(scroll); UiInsets.apply(scroll);
    action("Back to match", this::finish); label("Squad availability", 28, true);
    label("Organizer notes saved on this device. These statuses are not player RSVPs, do not send notifications and do not change the playing squad or scorecard.", 14, false);
    try {
      String matchId = getIntent().getStringExtra(EXTRA_MATCH_ID);
      for (Cricket.Match item : repository.matches()) if (item.id.equals(matchId)) match = item;
      if (match == null) { label("Match could not be found. Return and select a saved match.", 16, false); return; }
      attendance = new Attendance(match.id);
      for (Attendance item : repository.list("attendance", Attendance.class))
        if (item.matchId.equals(match.id)) { attendance = item; break; }
      label(match.name, 20, true);
      boolean editable = editableRole();
      label(editable ? "Choose a status to save immediately. The official PIN is required; after unlocking, repeat the change."
          : "Read-only workspace. An organizer, manager, captain, scorer or umpire with the device PIN can change these notes.", 14, false);
      if (editable) action("Unlock official controls", () -> OfficialAccess.require(this));
      playersA = squad(match.rosterA); playersB = squad(match.rosterB);
      summary = label("", 14, false);
      renderTeam(match.teamA, playersA, editable);
      renderTeam(match.teamB, playersB, editable);
      updateSummary();
      action("Share availability summary", () -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
          .setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText()), "Share availability notes")));
    } catch (RuntimeException error) { label("Could not load availability: " + error.getMessage(), 16, false); }
  }

  private List<String> squad(List<String> roster) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (String player : roster) if (player != null && !player.isEmpty()) names.add(player);
    return new ArrayList<>(names);
  }

  private boolean editableRole() {
    return Arrays.asList("Organizer", "Manager", "Captain", "Scorer", "Umpire").contains(role);
  }

  private void renderTeam(String team, List<String> players, boolean editable) {
    label(team, 21, true);
    if (players.isEmpty()) label("No players saved in this match's squad.", 15, false);
    for (String name : players) {
      TextView caption = label(name, 15, true);
      Spinner picker = new Spinner(this); picker.setId(View.generateViewId()); caption.setLabelFor(picker.getId());
      ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, LABELS);
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); picker.setAdapter(adapter);
      picker.setSelection(attendance.status(team, name).ordinal()); picker.setEnabled(editable);
      body.addView(picker, new LinearLayout.LayoutParams(-1, dp(52)));
      picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          Attendance.Status before = attendance.status(team, name), after = Attendance.Status.values()[position];
          if (before == after) return;
          if (!editableRole() || !OfficialAccess.require(AttendanceActivity.this)) {
            picker.setSelection(before.ordinal()); return;
          }
          long previousUpdated = attendance.updated;
          try {
            attendance.set(team, name, after); attendance.updated = System.currentTimeMillis();
            repository.save(attendance.storageId(), "attendance", attendance); updateSummary();
          } catch (RuntimeException error) {
            attendance.set(team, name, before); attendance.updated = previousUpdated; picker.setSelection(before.ordinal());
            Toast.makeText(AttendanceActivity.this, "Could not save availability: " + error.getMessage(), Toast.LENGTH_LONG).show();
          }
        }
      });
    }
  }

  private String counts(String team, List<String> players) {
    int available = 0, unavailable = 0;
    for (String name : players) {
      if (attendance.status(team, name) == Attendance.Status.AVAILABLE) available++;
      else if (attendance.status(team, name) == Attendance.Status.UNAVAILABLE) unavailable++;
    }
    return team + ": " + available + " available, " + unavailable + " unavailable, "
        + (players.size() - available - unavailable) + " not confirmed";
  }

  private void updateSummary() {
    summary.setText(counts(match.teamA, playersA) + "\n" + counts(match.teamB, playersB) + "\n"
        + (attendance.updated == 0 ? "No availability notes saved yet."
            : "Last saved on this device: " + DateFormat.getDateTimeInstance().format(new Date(attendance.updated))));
  }

  private String shareText() {
    StringBuilder text = new StringBuilder("Boundary local availability notes\n").append(match.name).append("\n\n");
    appendTeam(text, match.teamA, playersA); text.append("\n\n"); appendTeam(text, match.teamB, playersB);
    text.append("\n\nOrganizer-entered notes from this device, not confirmed player RSVPs. Playing squads and scorecards are unchanged.");
    if (attendance.updated > 0) text.append("\nLast saved: ").append(DateFormat.getDateTimeInstance().format(new Date(attendance.updated)));
    return text.toString();
  }

  private void appendTeam(StringBuilder text, String team, List<String> players) {
    text.append(counts(team, players));
    for (String player : players) text.append("\n").append(player).append(": ").append(LABELS[attendance.status(team, player).ordinal()]);
  }

  private TextView label(String text, int size, boolean bold) {
    TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(0xff142a24);
    view.setPadding(0, dp(10), 0, dp(6)); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); body.addView(view); return view;
  }
  private void action(String text, Runnable callback) {
    Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextColor(0xffffffff);
    button.setBackgroundTintList(ColorStateList.valueOf(0xff147653)); button.setOnClickListener(v -> callback.run());
    body.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  @Override protected void onDestroy() { if (repository != null) repository.close(); super.onDestroy(); }
}
