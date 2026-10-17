package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import com.example.madfinalproject.domain.Cricket;
import java.util.ArrayList;
import java.util.List;

/** Read-only charts of effective deliveries after journal replay. */
public final class MatchChartView extends View {
  public enum Mode { MANHATTAN, WORM }

  private final List<Cricket.Progress> points;
  private final int ballsPerOver;
  private final Mode mode;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] overRuns, overWickets;
  private final int recordedLegal, maxLegal, maxRuns;

  public MatchChartView(Context context, List<Cricket.Progress> progress, int ballsPerOver, Mode mode) {
    super(context);
    if (ballsPerOver <= 0) throw new IllegalArgumentException("Balls per over must be positive.");
    this.points = new ArrayList<>(progress);
    this.ballsPerOver = ballsPerOver;
    this.mode = mode;
    int buckets = 0, legal = 0, score = 0;
    for (Cricket.Progress point : points) {
      buckets = Math.max(buckets, point.legalBefore / ballsPerOver + 1);
      legal = Math.max(legal, point.legal);
      score = Math.max(score, point.runs);
    }
    overRuns = new int[buckets];
    overWickets = new int[buckets];
    int highestOver = 0;
    for (Cricket.Progress point : points) {
      int bucket = point.legalBefore / ballsPerOver;
      overRuns[bucket] += point.runs - point.runsBefore;
      overWickets[bucket] += point.wickets - point.wicketsBefore;
      highestOver = Math.max(highestOver, overRuns[bucket]);
    }
    recordedLegal = legal;
    maxLegal = Math.max(1, legal);
    maxRuns = Math.max(1, mode == Mode.MANHATTAN ? highestOver : score);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    setContentDescription((mode == Mode.MANHATTAN ? "Runs per over" : "Cumulative score by legal ball")
        + " chart. " + points.size() + " recorded scoring deliveries. "
        + "Wickets are marked in orange. A full accessible over summary follows the charts.");
    setBackgroundColor(0xffffffff);
  }

  private float dp(float value) { return value * getResources().getDisplayMetrics().density; }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float left = dp(43), right = getWidth() - dp(20), top = dp(28), bottom = getHeight() - dp(48);
    if (right <= left || bottom <= top) return;
    paint.setStyle(Paint.Style.FILL);
    paint.setTextSize(dp(11));
    paint.setColor(0xff52675c);
    paint.setTextAlign(Paint.Align.LEFT);
    canvas.drawText("Runs", left, dp(15), paint);
    for (int tick = 0; tick <= 2; tick++) {
      int value = tick == 0 ? 0 : tick == 1 ? maxRuns / 2 : maxRuns;
      if (tick == 1 && value == 0) continue;
      float y = bottom - value / (float) maxRuns * (bottom - top);
      paint.setColor(0xffe2e9df);
      paint.setStrokeWidth(dp(1));
      canvas.drawLine(left, y, right, y, paint);
      paint.setColor(0xff52675c);
      paint.setTextAlign(Paint.Align.RIGHT);
      canvas.drawText(String.valueOf(value), left - dp(7), y + dp(4), paint);
    }
    paint.setColor(0xff52675c);
    canvas.drawLine(left, top, left, bottom, paint);
    if (points.isEmpty()) {
      paint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText("No deliveries recorded", (left + right) / 2, (top + bottom) / 2, paint);
      return;
    }
    if (mode == Mode.MANHATTAN) drawBars(canvas, left, right, top, bottom);
    else drawWorm(canvas, left, right, top, bottom);
    paint.setColor(0xff52675c);
    paint.setTextSize(dp(11));
    paint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(mode == Mode.MANHATTAN ? "Over number" : "Overs (legal balls)",
        (left + right) / 2, bottom + dp(38), paint);
  }

  private void drawBars(Canvas canvas, float left, float right, float top, float bottom) {
    float width = (right - left) / overRuns.length;
    for (int i = 0; i < overRuns.length; i++) {
      float x = left + i * width;
      float y = bottom - overRuns[i] / (float) maxRuns * (bottom - top);
      float gap = Math.min(dp(3), width * .15f);
      paint.setColor(0xff147653);
      canvas.drawRect(x + gap, y, x + width - gap, bottom, paint);
      if (overWickets[i] > 0) {
        paint.setColor(0xffb75120);
        canvas.drawCircle(x + width / 2, Math.max(top + dp(4), y - dp(5)), dp(3), paint);
      }
    }
    int step = Math.max(1, (int) Math.ceil(overRuns.length / 4.0));
    paint.setColor(0xff52675c);
    paint.setTextAlign(Paint.Align.CENTER);
    for (int i = 0; i < overRuns.length; i += step)
      canvas.drawText(String.valueOf(i + 1), left + (i + .5f) * width, bottom + dp(18), paint);
  }

  private void drawWorm(Canvas canvas, float left, float right, float top, float bottom) {
    Path path = new Path();
    path.moveTo(left, bottom);
    for (Cricket.Progress point : points) {
      float x = left + point.legal / (float) maxLegal * (right - left);
      float y = bottom - point.runs / (float) maxRuns * (bottom - top);
      path.lineTo(x, y);
    }
    paint.setColor(0xff147653);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(2.5f));
    canvas.drawPath(path, paint);
    paint.setStyle(Paint.Style.FILL);
    for (Cricket.Progress point : points) {
      if (point.wickets <= point.wicketsBefore) continue;
      paint.setColor(0xffb75120);
      canvas.drawCircle(left + point.legal / (float) maxLegal * (right - left),
          bottom - point.runs / (float) maxRuns * (bottom - top), dp(3.5f), paint);
    }
    paint.setColor(0xff52675c);
    paint.setTextAlign(Paint.Align.CENTER);
    int ticks = Math.min(4, recordedLegal);
    if (ticks == 0) {
      canvas.drawText("0.0", left, bottom + dp(18), paint);
      return;
    }
    for (int i = 0; i <= ticks; i++) {
      int ball = (int) Math.round(i * maxLegal / (double) ticks);
      String over = ball / ballsPerOver + "." + ball % ballsPerOver;
      canvas.drawText(over, left + ball / (float) maxLegal * (right - left), bottom + dp(18), paint);
    }
  }
}
