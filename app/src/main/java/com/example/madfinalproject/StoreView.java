package com.example.madfinalproject;

import android.app.Activity;
import android.widget.LinearLayout;

/** The Store tab's content, drawn inside MainActivity under its top bar and above the bottom bar. */
public final class StoreView extends LinearLayout {
  public StoreView(Activity activity, Ui.Palette palette) {
    super(activity);
    setOrientation(VERTICAL);
    addView(Ui.text(activity, "Store", 20, palette.ink, true));
  }
}
