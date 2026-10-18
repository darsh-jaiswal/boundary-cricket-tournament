package com.example.madfinalproject;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Match detail: Info, Summary, Scorecard, Overs, Squads. Placeholder until the screen lands. */
public final class MatchDetailActivity extends AppCompatActivity {
  public static final String EXTRA_MATCH_ID = "match", EXTRA_TAB = "tab";
  @Override protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    setContentView(Ui.text(this, "Match detail", 20, Ui.palette(this).ink, true));
  }
}
