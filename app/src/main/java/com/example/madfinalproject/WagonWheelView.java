package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wagon wheel: field with eight scoring sectors, one line per scoring shot coloured by runs, and the
 * share of runs per sector. Bowler's end is at the top; the off side is on the left for a right-hander.
 * Zones follow Career.ZONES: third man, point, cover, long off, long on, midwicket, square leg, fine leg.
 */
public final class WagonWheelView extends View {
  public static final String OUT = "Out", DOTS = "Dots", ONES = "1's", TWOS = "2's", FOURS = "4's", SIXES = "6's";
  public static final String[] CATEGORIES = {OUT, DOTS, ONES, TWOS, FOURS, SIXES};
  public static final int[] CATEGORY_COLORS = {0xfff25c19, 0xffffffff, 0xff0d74a6, 0xfff0a73a, 0xff9b59b6, 0xffe5202e};

  /** One delivery with a recorded zone. */
  public static final class Shot {
    public final int zone, runs;
    public final boolean out;
    public Shot(int zone, int runs, boolean out) { this.zone = zone; this.runs = runs; this.out = out; }
    public String category() {
      if (out) return OUT;
      if (runs == 0) return DOTS;
      if (runs >= 6) return SIXES;
      if (runs >= 4) return FOURS;
      return runs == 1 ? ONES : TWOS; // 3s draw with 2s, as in the reference legend
    }
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF oval = new RectF();
  private List<Shot> shots = new ArrayList<>();
  private final Set<String> visible = new HashSet<>();
  private boolean leftHanded;

  public WagonWheelView(Context c) {
    super(c);
    for (String category : CATEGORIES) visible.add(category);
    text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    text.setTextAlign(Paint.Align.CENTER);
  }

  public void setShots(List<Shot> shots, boolean leftHanded) {
    this.shots = shots == null ? new ArrayList<>() : new ArrayList<>(shots);
    this.leftHanded = leftHanded;
    invalidate();
  }

  /** Legend toggles: only shots in these categories are drawn and counted. */
  public void setVisible(Set<String> categories) {
    visible.clear();
    visible.addAll(categories);
    invalidate();
  }

  public Set<String> visible() { return new HashSet<>(visible); }

  @Override protected void onMeasure(int w, int h) {
    int width = MeasureSpec.getSize(w);
    setMeasuredDimension(width, width);
  }

  @Override protected void onDraw(Canvas canvas) {
    float size = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f;
    float outer = size / 2f - 2, rope = outer * 0.88f, inner = outer * 0.48f;
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xff1f6b46);
    canvas.drawCircle(cx, cy, outer, paint);
    paint.setColor(0xff4f9a3a);
    canvas.drawCircle(cx, cy, outer - size * 0.012f, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(size * 0.007f);
    paint.setColor(0xffffffff);
    canvas.drawCircle(cx, cy, rope, paint);
    paint.setStrokeWidth(size * 0.0025f);
    paint.setColor(0x99ffffff);
    canvas.drawCircle(cx, cy, inner, paint);
    for (int i = 0; i < 8; i++) {
      double a = Math.toRadians(i * 45);
      canvas.drawLine(cx, cy, cx + (float) Math.sin(a) * rope, cy - (float) Math.cos(a) * rope, paint);
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xffdcc596);
    float pw = size * 0.045f, ph = size * 0.16f;
    canvas.drawRect(cx - pw / 2, cy - ph * 0.2f, cx + pw / 2, cy + ph * 0.8f, paint);
    paint.setColor(0xffe2c044);
    canvas.drawCircle(cx, cy - ph * 0.42f, size * 0.009f, paint);

    int[] runs = new int[8];
    int total = 0, index = 0;
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeWidth(size * 0.0045f);
    for (Shot s : shots) {
      index++;
      if (s.zone < 0 || s.zone > 7 || !visible.contains(s.category())) continue;
      runs[s.zone] += s.runs;
      total += s.runs;
      if (s.runs == 0 && !s.out) continue; // dots have no direction worth drawing
      double sector = sectorStart(s.zone) + 8 + (index * 37 % 29);
      double a = Math.toRadians(sector);
      float length = s.runs >= 6 ? outer * 0.97f : s.runs == 4 ? rope * 0.99f : rope * (0.38f + (index * 13 % 40) / 100f);
      float dx = (float) Math.sin(a) * length * (leftHanded ? -1 : 1), dy = -(float) Math.cos(a) * length;
      paint.setColor(CATEGORY_COLORS[indexOf(s.category())]);
      canvas.drawLine(cx, cy, cx + dx, cy + dy, paint);
    }

    text.setTextSize(size * 0.045f);
    for (int z = 0; z < 8; z++) {
      if (total == 0 || runs[z] == 0) continue;
      double a = Math.toRadians(sectorStart(z) + 22.5);
      float r = rope * 0.72f;
      float x = cx + (float) Math.sin(a) * r * (leftHanded ? -1 : 1), y = cy - (float) Math.cos(a) * r + text.getTextSize() / 3;
      boolean best = runs[z] == max(runs);
      text.setColor(best ? 0xffffd36b : 0xddf4f7ee);
      canvas.drawText(Math.round(runs[z] * 100f / total) + "%", x, y, text);
    }
    text.setColor(0x99ffffff);
    text.setTextSize(size * 0.05f);
    canvas.drawText(leftHanded ? "Leg" : "Off", cx - inner * 0.62f, cy + text.getTextSize() / 3, text);
    canvas.drawText(leftHanded ? "Off" : "Leg", cx + inner * 0.62f, cy + text.getTextSize() / 3, text);
  }

  /** Clockwise from straight up, for a right-hander (off side on the left). */
  private static double sectorStart(int zone) {
    switch (zone) {
      case 4: return 0;     // long on
      case 5: return 45;    // midwicket
      case 6: return 90;    // square leg
      case 7: return 135;   // fine leg
      case 0: return 180;   // third man
      case 1: return 225;   // point
      case 2: return 270;   // cover
      default: return 315;  // long off
    }
  }

  private static int indexOf(String category) {
    for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(category)) return i;
    return 0;
  }

  private static int max(int[] values) {
    int m = 0;
    for (int v : values) m = Math.max(m, v);
    return m;
  }

  /** "57% runs scored in Long on from total 7", or "" when no zone was recorded. */
  public static String summary(List<Shot> shots, String[] zoneNames) {
    int[] runs = new int[8];
    int total = 0;
    for (Shot s : shots) if (s.zone >= 0 && s.zone < 8) { runs[s.zone] += s.runs; total += s.runs; }
    int best = -1;
    for (int z = 0; z < 8; z++) if (runs[z] > 0 && (best < 0 || runs[z] > runs[best])) best = z;
    if (best < 0) return "";
    return String.format(Locale.US, "%d%% runs scored in %s from total %d", Math.round(runs[best] * 100f / total), zoneNames[best], total);
  }
}
