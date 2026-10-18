package com.example.madfinalproject;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Scan a Boundary profile QR code to follow that player. Placeholder until the scanner lands. */
public final class QrScanActivity extends AppCompatActivity {
  @Override protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    setContentView(Ui.text(this, "Scan a profile QR code", 20, Ui.palette(this).ink, true));
  }
}
