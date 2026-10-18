package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.Arrays;

/** Labelled bars and an eight-sector wagon wheel from recorded scoring zones. */
public final class StatsVisualView extends View {
  private final String[] labels;
  private final double[] values;
  private final boolean wheel;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public StatsVisualView(Context context, String[] labels, double[] values, boolean wheel) {
    super(context); this.labels = labels.clone(); this.values = values.clone(); this.wheel = wheel;
    setBackgroundColor(0xffffffff);
    StringBuilder summary = new StringBuilder(wheel ? "Recorded wagon wheel. " : "Chart. ");
    for (int i = 0; i < Math.min(labels.length, values.length); i++) summary.append(labels[i]).append(": ").append(values[i]).append(". ");
    setContentDescription(summary.toString());
  }
  private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
  @Override protected void onDraw(Canvas c) {
    super.onDraw(c); paint.setTextSize(dp(11)); paint.setStyle(Paint.Style.FILL);
    if (values.length == 0) return;
    double max = Math.max(1, Arrays.stream(values).max().orElse(1));
    if (wheel) {
      float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - dp(35);
      paint.setColor(0xfff0f5ed); c.drawCircle(cx, cy, r, paint);
      for (int i = 0; i < 8 && i < values.length; i++) {
        float length = (float) Math.sqrt(values[i] / max) * r;
        paint.setColor(0xff147653); c.drawArc(new RectF(cx - length, cy - length, cx + length, cy + length), i * 45 - 90, 44, true, paint);
        double angle = Math.toRadians(i * 45 - 67.5);
        paint.setColor(0xff52675c); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.valueOf((long) values[i]), cx + (float) Math.cos(angle) * (r + dp(16)), cy + (float) Math.sin(angle) * (r + dp(16)) + dp(4), paint);
      }
      paint.setColor(0xffa8c1ad); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); c.drawCircle(cx, cy, r, paint);
      paint.setStyle(Paint.Style.FILL); paint.setColor(0xff142a24); c.drawCircle(cx, cy, dp(3), paint);
      return;
    }
    float left = dp(35), right = getWidth() - dp(20), top = dp(28), bottom = getHeight() - dp(35);
    float step = (right - left) / values.length;
    for (int i = 0; i < values.length; i++) {
      float x = left + step * i, y = bottom - (float) (values[i] / max) * (bottom - top);
      paint.setColor(0xff147653); c.drawRoundRect(x + dp(3), y, x + step - dp(3), bottom, dp(3), dp(3), paint);
      paint.setColor(0xff52675c); paint.setTextAlign(Paint.Align.CENTER);
      c.drawText(String.valueOf((long) values[i]), x + step / 2, y - dp(6), paint);
      if (i < labels.length) c.drawText(labels[i], x + step / 2, bottom + dp(19), paint);
    }
  }
}
