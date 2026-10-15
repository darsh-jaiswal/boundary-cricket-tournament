package com.example.madfinalproject;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

/** Nine draggable fielders with normalized coordinates retained across redraws. */
public final class FieldView extends View {
  private final Paint paint = new Paint(3);
  private final float[][] points = {
    {.2f, .25f},
    {.35f, .15f},
    {.65f, .15f},
    {.8f, .3f},
    {.8f, .6f},
    {.65f, .85f},
    {.3f, .8f},
    {.15f, .6f},
    {.55f, .32f}
  };
  private int selected = -1;

  public FieldView(Context c) {
    super(c);
    setContentDescription(
        "Cricket field radar. Drag any of the nine numbered fielders to reposition them.");
  }

  public void recommend(int[] zones, boolean powerplay) {
    int strongest = 0;
    for (int i = 1; i < 8; i++) if (zones[i] > zones[strongest]) strongest = i;
    for (int i = 0; i < 9; i++) {
      double angle = (i * Math.PI * 2 / 9) - Math.PI / 2;
      float radius = powerplay && i > 1 ? .24f : .4f;
      points[i][0] = .5f + (float) Math.sin(angle) * radius;
      points[i][1] = .5f - (float) Math.cos(angle) * radius;
    }
    if (java.util.Arrays.stream(zones).sum() > 0) {
      double angle = strongest * Math.PI / 4 - Math.PI / 4;
      points[0][0] = .5f + (float) Math.sin(angle) * .42f;
      points[0][1] = .5f - (float) Math.cos(angle) * .42f;
    }
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float w = getWidth(), h = getHeight();
    paint.setColor(Color.rgb(224, 238, 215));
    c.drawOval(w * .03f, h * .03f, w * .97f, h * .97f, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(3);
    paint.setColor(Color.rgb(110, 153, 99));
    c.drawOval(w * .08f, h * .08f, w * .92f, h * .92f, paint);
    c.drawOval(w * .25f, h * .25f, w * .75f, h * .75f, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.rgb(211, 189, 135));
    c.drawRect(w * .46f, h * .4f, w * .54f, h * .62f, paint);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(24);
    for (int i = 0; i < 9; i++) {
      paint.setColor(Color.rgb(24, 111, 75));
      c.drawCircle(points[i][0] * w, points[i][1] * h, 22, paint);
      paint.setColor(Color.WHITE);
      c.drawText(String.valueOf(i + 1), points[i][0] * w, points[i][1] * h + 8, paint);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    float x = e.getX() / getWidth(), y = e.getY() / getHeight();
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      float best = .012f;
      for (int i = 0; i < 9; i++) {
        float dist =
            (points[i][0] - x) * (points[i][0] - x) + (points[i][1] - y) * (points[i][1] - y);
        if (dist < best) {
          best = dist;
          selected = i;
        }
      }
      getParent().requestDisallowInterceptTouchEvent(selected >= 0);
    }
    if (selected >= 0) {
      points[selected][0] = Math.max(.08f, Math.min(.92f, x));
      points[selected][1] = Math.max(.08f, Math.min(.92f, y));
      invalidate();
    }
    if (e.getAction() == MotionEvent.ACTION_UP) {
      selected = -1;
      performClick();
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
