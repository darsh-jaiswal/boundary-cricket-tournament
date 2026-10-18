package com.example.madfinalproject;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import java.io.File;
import java.util.Locale;

/** Shared green-and-white palette (with its dark twin) and the small view kit the new screens use. */
public final class Ui {
  private Ui() {}

  public static final String PREFS = "boundary-ui", APPEARANCE = "appearance";

  /** Resolved colours for the current light/dark mode. */
  public static final class Palette {
    public final boolean dark;
    public final int bg, surface, surface2, ink, muted, accent, accentSoft, onAccent, line, danger, gold;
    Palette(boolean dark) {
      this.dark = dark;
      bg = dark ? 0xff0e1411 : 0xfff3f6ef;
      surface = dark ? 0xff18211c : 0xffffffff;
      surface2 = dark ? 0xff222d27 : 0xffe9efe6;
      ink = dark ? 0xffe7efe9 : 0xff17251d;
      muted = dark ? 0xff98a89f : 0xff66756b;
      accent = dark ? 0xff35b07f : 0xff147653;
      accentSoft = dark ? 0xff173d2d : 0xffdcefe3;
      onAccent = dark ? 0xff03140b : 0xffffffff;
      line = dark ? 0xff2b3731 : 0xffdde5d9;
      danger = dark ? 0xffff7a6e : 0xffc0392b;
      gold = 0xffe2a92b;
    }
  }

  /** "light", "dark" or "system" (default). */
  public static String appearance(Context c) {
    return c.getSharedPreferences(PREFS, 0).getString(APPEARANCE, "system");
  }

  public static void applyAppearance(Context c) {
    String mode = appearance(c);
    AppCompatDelegate.setDefaultNightMode(mode.equals("dark") ? AppCompatDelegate.MODE_NIGHT_YES
        : mode.equals("light") ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
  }

  public static Palette palette(Context c) {
    int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return new Palette(night == Configuration.UI_MODE_NIGHT_YES);
  }

  public static int dp(Context c, float v) {
    return Math.round(v * c.getResources().getDisplayMetrics().density);
  }

  public static TextView text(Context c, String s, float size, int color, boolean bold) {
    TextView v = new TextView(c);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setIncludeFontPadding(false);
    v.setLineSpacing(dp(c, 2), 1);
    if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return v;
  }

  public static GradientDrawable round(int color, float radiusPx) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radiusPx);
    return d;
  }

  public static GradientDrawable outline(int fill, int stroke, int strokePx, float radiusPx) {
    GradientDrawable d = round(fill, radiusPx);
    d.setStroke(strokePx, stroke);
    return d;
  }

  public static GradientDrawable circle(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setShape(GradientDrawable.OVAL);
    d.setColor(color);
    return d;
  }

  public static LinearLayout row(Context c) {
    LinearLayout r = new LinearLayout(c);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    return r;
  }

  public static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

  public static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }

  public static View space(Context c, int w, int h) {
    View v = new View(c);
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, w), dp(c, h)));
    return v;
  }

  /** Rounded pill button: filled accent, or tonal on surface2. */
  public static TextView pill(Context c, Palette p, String label, boolean filled, Runnable action) {
    TextView b = text(c, label, 14, filled ? p.onAccent : p.ink, true);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
    b.setBackground(round(filled ? p.accent : p.surface2, dp(c, 24)));
    b.setOnClickListener(v -> action.run());
    b.setClickable(true);
    return b;
  }

  /** Small rounded chip, e.g. "Insights", "Squads", filter selectors. */
  public static TextView chip(Context c, Palette p, String label, boolean selected, Runnable action) {
    TextView b = text(c, label, 13, selected ? p.onAccent : p.ink, true);
    b.setGravity(Gravity.CENTER);
    b.setSingleLine(true);
    b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
    b.setBackground(selected ? round(p.accent, dp(c, 18)) : outline(p.surface, p.line, dp(c, 1), dp(c, 18)));
    if (action != null) b.setOnClickListener(v -> action.run());
    return b;
  }

  /** Circular icon button. */
  public static FrameLayout iconButton(Context c, Palette p, String icon, int size, int bg, int fg, Runnable action) {
    FrameLayout f = new FrameLayout(c);
    f.setBackground(circle(bg));
    IconView i = new IconView(c, icon, fg);
    int inner = dp(c, size * 0.5f);
    f.addView(i, new FrameLayout.LayoutParams(inner, inner, Gravity.CENTER));
    f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, size), dp(c, size)));
    if (action != null) f.setOnClickListener(v -> action.run());
    f.setContentDescription(icon);
    return f;
  }

  /** White (or dark surface) rounded card. */
  public static LinearLayout card(Context c, Palette p) {
    LinearLayout card = column(c);
    card.setBackground(round(p.surface, dp(c, 18)));
    card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
    card.setElevation(p.dark ? 0 : dp(c, 1));
    return card;
  }

  /** "Title  >" section heading. */
  public static LinearLayout sectionTitle(Context c, Palette p, String title, Runnable more) {
    LinearLayout r = row(c);
    r.setPadding(0, dp(c, 18), 0, dp(c, 10));
    r.addView(text(c, title, 21, p.ink, true));
    if (more != null) {
      r.addView(new IconView(c, "chevron-right", p.ink), new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
      r.setOnClickListener(v -> more.run());
    }
    return r;
  }

  /** Big number over a small label, used for the stat grids. */
  public static LinearLayout tile(Context c, Palette p, String value, String label) {
    LinearLayout t = column(c);
    t.setGravity(Gravity.CENTER);
    t.setBackground(round(p.surface2, dp(c, 14)));
    t.setPadding(dp(c, 6), dp(c, 16), dp(c, 6), dp(c, 16));
    TextView v = text(c, value, 24, p.ink, true);
    v.setGravity(Gravity.CENTER);
    t.addView(v);
    TextView l = text(c, label, 12, p.muted, false);
    l.setGravity(Gravity.CENTER);
    l.setPadding(0, dp(c, 6), 0, 0);
    t.addView(l);
    return t;
  }

  /** Lays tiles out in rows of {@code columns}. */
  public static LinearLayout grid(Context c, View[] items, int columns) {
    LinearLayout g = column(c);
    for (int i = 0; i < items.length; i += columns) {
      LinearLayout r = row(c);
      for (int j = 0; j < columns; j++) {
        LinearLayout.LayoutParams lp = weight(1);
        lp.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        if (i + j < items.length) r.addView(items[i + j], lp);
        else r.addView(new View(c), lp);
      }
      g.addView(r);
    }
    return g;
  }

  /** "14 | Total runs in last 5 innings" insight row. */
  public static LinearLayout insight(Context c, Palette p, String value, String label) {
    LinearLayout r = row(c);
    r.setBackground(round(p.surface2, dp(c, 12)));
    r.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
    TextView v = text(c, value, 24, p.ink, true);
    v.setMinWidth(dp(c, 56));
    r.addView(v);
    View bar = new View(c);
    bar.setBackgroundColor(p.line);
    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(c, 1), dp(c, 28));
    bl.setMargins(dp(c, 10), 0, dp(c, 14), 0);
    r.addView(bar, bl);
    r.addView(text(c, label, 14, p.ink, false), weight(1));
    return r;
  }

  /** Sentence with one highlighted phrase: "Most productive region: Mid On". */
  public static TextView highlight(Context c, Palette p, String before, String strong, String after) {
    TextView t = text(c, "", 15, p.ink, false);
    android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder(before);
    int start = s.length();
    s.append(strong);
    s.setSpan(new android.text.style.ForegroundColorSpan(p.accent), start, s.length(), 0);
    s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, s.length(), 0);
    s.append(after);
    t.setText(s);
    t.setBackground(round(p.surface2, dp(c, 12)));
    t.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
    return t;
  }

  public static View divider(Context c, Palette p) {
    View v = new View(c);
    v.setBackgroundColor(p.line);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 0.7f))));
    return v;
  }

  /** Circular profile photo, falling back to initials on the accent colour. */
  public static View avatar(Context c, Palette p, File photo, String name, int sizeDp) {
    int size = dp(c, sizeDp);
    Bitmap bitmap = photo != null && photo.exists() ? decode(photo, size) : null;
    if (bitmap != null) {
      ImageView v = new ImageView(c);
      v.setImageBitmap(bitmap);
      v.setScaleType(ImageView.ScaleType.CENTER_CROP);
      v.setClipToOutline(true);
      v.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override public void getOutline(View view, android.graphics.Outline outline) {
          outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
      });
      v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
      return v;
    }
    TextView t = text(c, initials(name), sizeDp * 0.36f, p.onAccent, true);
    t.setGravity(Gravity.CENTER);
    t.setBackground(circle(p.accent));
    t.setLayoutParams(new LinearLayout.LayoutParams(size, size));
    return t;
  }

  public static Bitmap decode(File file, int targetPx) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    int sample = 1;
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
  }

  public static String initials(String name) {
    if (name == null || name.trim().isEmpty()) return "B";
    String[] parts = name.trim().split("\\s+");
    String s = parts[0].substring(0, 1) + (parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "");
    return s.toUpperCase(Locale.ROOT);
  }

  public static String decimal(double v) {
    return Double.isNaN(v) || Double.isInfinite(v) ? "-" : String.format(Locale.US, "%.2f", v);
  }

  public static String percent(double v) {
    return Double.isNaN(v) ? "-" : Math.round(v) + "%";
  }

  public static void tint(View v, int color) { v.setBackgroundTintList(ColorStateList.valueOf(color)); }

  public static void match(View v) {
    v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }
}
