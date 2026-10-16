package com.example.madfinalproject;

import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class UiInsets {
  private UiInsets() {}

  static void apply(View root) {
    int left = root.getPaddingLeft(),
        top = root.getPaddingTop(),
        right = root.getPaddingRight(),
        bottom = root.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
          return insets;
        });
    ViewCompat.requestApplyInsets(root);
  }
}
