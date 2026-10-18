package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import com.example.madfinalproject.domain.Cricket.Progress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Two innings on the same axes, as match-progress comparisons. */
public final class ComparisonChartView extends View {
  public enum Mode { MANHATTAN, WORM, RUN_RATE }
  private final List<Progress> first, second;
  private final String nameA, nameB;
  private final Mode mode;
  private final int bpo;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] colors = {0xff147653, 0xffc06b29};
  public ComparisonChartView(Context context, List<Progress> first, List<Progress> second,
      String a, String b, int ballsPerOver, Mode mode) {
    super(context);
    this.first = new ArrayList<>(first); this.second = new ArrayList<>(second);
    nameA = a; nameB = b; bpo = ballsPerOver; this.mode = mode;
    setBackgroundColor(0xffffffff);
    setContentDescription(mode.name().replace('_', ' ') + ": " + a + " and " + b
        + ". Green is first innings; orange is second innings. Wickets are circles. Exact figures follow below.");
  }
  private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
  private double value(Progress p) { return mode == Mode.RUN_RATE
      ? (p.legal == 0 ? 0 : p.runs * (double) bpo / p.legal) : p.runs; }
  private int[] bars(List<Progress> points, int n) {
    int[] bins = new int[n]; for (Progress p : points) bins[Math.min(n - 1, p.legalBefore / bpo)] += p.runs - p.runsBefore;
    return bins;
  }
  @Override protected void onDraw(Canvas c) {
    super.onDraw(c);
    float left = dp(40), right = getWidth() - dp(18), top = dp(48), bottom = getHeight() - dp(38);
    if (right <= left || bottom <= top) return;
    int maxBall = 1, n = 1;
    for (List<Progress> points : java.util.Arrays.asList(first, second)) for (Progress p : points) {
      maxBall = Math.max(maxBall, p.legal); n = Math.max(n, p.legalBefore / bpo + 1);
    }
    int[][] bins = {bars(first, n), bars(second, n)};
    double max = 1;
    for (int series = 0; series < 2; series++) {
      if (mode == Mode.MANHATTAN) for (int v : bins[series]) max = Math.max(max, v);
      else for (Progress p : series == 0 ? first : second) max = Math.max(max, value(p));
    }
    paint.setTextSize(dp(11)); paint.setStyle(Paint.Style.FILL);
    for (int tick = 0; tick <= 4; tick++) {
      float y = bottom - (bottom - top) * tick / 4;
      paint.setColor(0xffe1e9df); c.drawLine(left, y, right, y, paint);
      paint.setColor(0xff52675c); paint.setTextAlign(Paint.Align.RIGHT);
      c.drawText(String.format(Locale.US, mode == Mode.RUN_RATE ? "%.1f" : "%.0f", max * tick / 4), left - dp(5), y + dp(4), paint);
    }
    for (int series = 0; series < 2; series++) {
      List<Progress> points = series == 0 ? first : second;
      paint.setColor(colors[series]); paint.setTextAlign(Paint.Align.LEFT);
      String label = series == 0 ? nameA : nameB;
      if (label.length() > 22) label = label.substring(0, 21) + "…";
      c.drawText(label, left, dp(series == 0 ? 16 : 33), paint);
      if (mode == Mode.MANHATTAN) {
        float step = (right - left) / n;
        for (int i = 0; i < n; i++) {
          float x = left + i * step + step * (.12f + series * .4f);
          c.drawRect(x, bottom - (float) (bins[series][i] / max) * (bottom - top), x + step * .34f, bottom, paint);
        }
      } else {
        Path path = new Path(); path.moveTo(left, bottom);
        for (Progress p : points) path.lineTo(left + p.legal / (float) maxBall * (right - left), bottom - (float) (value(p) / max) * (bottom - top));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); c.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
      }
      for (Progress p : points) if (p.wickets > p.wicketsBefore) {
        float x, y;
        if (mode == Mode.MANHATTAN) {
          int bucket = p.legalBefore / bpo; float step = (right - left) / n;
          x = left + bucket * step + step * (.29f + series * .4f);
          y = bottom - (float) (bins[series][bucket] / max) * (bottom - top);
        } else { x = left + p.legal / (float) maxBall * (right - left); y = bottom - (float) (value(p) / max) * (bottom - top); }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); c.drawCircle(x, y, dp(4), paint); paint.setStyle(Paint.Style.FILL);
      }
    }
    paint.setColor(0xff52675c); paint.setTextAlign(Paint.Align.CENTER);
    for (int tick = 0; tick <= 4; tick++) {
      double over = mode == Mode.MANHATTAN ? n * tick / 4.0 : maxBall * tick / (4.0 * bpo);
      c.drawText(String.format(Locale.US, "%.1f", over), left + (right - left) * tick / 4, bottom + dp(17), paint);
    }
    c.drawText("Overs · " + (mode == Mode.RUN_RATE ? "runs per over" : "runs"), (left + right) / 2, bottom + dp(33), paint);
    if (first.isEmpty() && second.isEmpty()) c.drawText("No recorded deliveries", (left + right) / 2, (top + bottom) / 2, paint);
  }
}
