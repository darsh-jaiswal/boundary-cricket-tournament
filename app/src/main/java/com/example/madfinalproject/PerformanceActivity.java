package com.example.madfinalproject;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Placeholder until the full screen lands in this change. */
public final class PerformanceActivity extends AppCompatActivity {
  public static final String EXTRA_TAB = "tab", EXTRA_SHEET = "sheet";
  @Override protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    android.widget.TextView t = Ui.text(this, "PerformanceActivity", 20, Ui.palette(this).ink, true);
    setContentView(t);
  }
}
