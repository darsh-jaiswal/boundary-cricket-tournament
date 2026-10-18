package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line icons drawn on a 24-unit grid, so they recolour with the palette and need no assets. */
public final class IconView extends View {
  private final String name;
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final RectF r = new RectF();

  public IconView(Context c, String name, int color) {
    super(c);
    this.name = name;
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeCap(Paint.Cap.ROUND);
    stroke.setStrokeJoin(Paint.Join.ROUND);
    stroke.setColor(color);
    fill.setColor(color);
    setContentDescription(name);
  }

  public void setColor(int color) { stroke.setColor(color); fill.setColor(color); invalidate(); }

  @Override protected void onDraw(Canvas canvas) {
    float s = Math.min(getWidth(), getHeight()) / 24f;
    canvas.save();
    canvas.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
    canvas.scale(s, s);
    stroke.setStrokeWidth(1.8f);
    path.reset();
    switch (name) {
      case "back": line(canvas, 20, 12, 4, 12); poly(canvas, 10, 6, 4, 12, 10, 18); break;
      case "close": line(canvas, 6, 6, 18, 18); line(canvas, 18, 6, 6, 18); break;
      case "plus": line(canvas, 12, 5, 12, 19); line(canvas, 5, 12, 19, 12); break;
      case "chevron-right": poly(canvas, 9, 6, 15, 12, 9, 18); break;
      case "chevron-down": poly(canvas, 6, 9, 12, 15, 18, 9); break;
      case "chevron-up": poly(canvas, 6, 15, 12, 9, 18, 15); break;
      case "search": canvas.drawCircle(10.5f, 10.5f, 6, stroke); line(canvas, 15, 15, 20, 20); break;
      case "share": poly(canvas, 14, 5, 20, 10.5f, 14, 16);
        path.moveTo(20, 10.5f); path.cubicTo(11, 10.5f, 6, 12, 4, 19); canvas.drawPath(path, stroke); break;
      case "camera": rect(canvas, 3, 7, 21, 19, 2.5f); poly(canvas, 8, 7, 9.5f, 4.5f, 14.5f, 4.5f, 16, 7);
        canvas.drawCircle(12, 13, 3.5f, stroke); break;
      case "qr": rect(canvas, 3.5f, 3.5f, 10, 10, 1.2f); rect(canvas, 14, 3.5f, 20.5f, 10, 1.2f);
        rect(canvas, 3.5f, 14, 10, 20.5f, 1.2f); dot(canvas, 6.75f, 6.75f, 1.3f); dot(canvas, 17.25f, 6.75f, 1.3f);
        dot(canvas, 6.75f, 17.25f, 1.3f); dot(canvas, 14.5f, 14.5f, 1); dot(canvas, 19.5f, 14.5f, 1);
        dot(canvas, 17, 17, 1); dot(canvas, 14.5f, 19.5f, 1); dot(canvas, 19.5f, 19.5f, 1); break;
      case "bag": rect(canvas, 4, 8, 20, 20.5f, 2.5f); path.moveTo(8.5f, 10); path.lineTo(8.5f, 7);
        path.cubicTo(8.5f, 2.5f, 15.5f, 2.5f, 15.5f, 7); path.lineTo(15.5f, 10); canvas.drawPath(path, stroke); break;
      case "edit": poly(canvas, 5, 19, 5.5f, 15, 15.5f, 5, 19, 8.5f, 9, 18.5f, 5, 19); line(canvas, 13.5f, 7, 17, 10.5f); break;
      case "user": canvas.drawCircle(12, 8.5f, 4, stroke); path.moveTo(4.5f, 20.5f);
        path.cubicTo(5.5f, 15, 18.5f, 15, 19.5f, 20.5f); canvas.drawPath(path, stroke); break;
      case "bat": poly(canvas, 15, 4, 20, 9, 10, 19, 7.5f, 18.5f, 5.5f, 16.5f, 5, 14, 15, 4); line(canvas, 5.5f, 18.5f, 3.5f, 20.5f);
        canvas.drawCircle(18.5f, 18.5f, 2, stroke); break;
      case "performance": line(canvas, 4, 20, 20, 20); rect(canvas, 5.5f, 12, 8.5f, 20, 0.8f); rect(canvas, 10.5f, 6, 13.5f, 20, 0.8f);
        rect(canvas, 15.5f, 9.5f, 18.5f, 20, 0.8f); break;
      case "store": poly(canvas, 3.5f, 9, 5, 4, 19, 4, 20.5f, 9, 3.5f, 9); rect(canvas, 5, 9, 19, 20, 1);
        rect(canvas, 9.5f, 13.5f, 14.5f, 20, 0.5f); break;
      case "appearance": canvas.drawCircle(12, 12, 8.5f, stroke); path.moveTo(12, 3.5f); path.arcTo(new RectF(3.5f, 3.5f, 20.5f, 20.5f), -90, 180);
        path.close(); canvas.drawPath(path, fill); break;
      case "support": path.moveTo(4.5f, 14); path.lineTo(4.5f, 12); path.cubicTo(4.5f, 2, 19.5f, 2, 19.5f, 12); path.lineTo(19.5f, 14);
        canvas.drawPath(path, stroke); rect(canvas, 3.5f, 12.5f, 7, 18, 1.2f); rect(canvas, 17, 12.5f, 20.5f, 18, 1.2f);
        path.reset(); path.moveTo(19, 18); path.cubicTo(19, 20.5f, 16, 21, 13, 21); canvas.drawPath(path, stroke); break;
      case "others": canvas.drawCircle(12, 12, 9, stroke); dot(canvas, 8, 12, 1.2f); dot(canvas, 12, 12, 1.2f); dot(canvas, 16, 12, 1.2f); break;
      case "settings": canvas.drawCircle(12, 12, 3, stroke);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          line(canvas, 12 + (float) Math.cos(a) * 6.5f, 12 + (float) Math.sin(a) * 6.5f, 12 + (float) Math.cos(a) * 9, 12 + (float) Math.sin(a) * 9);
        }
        canvas.drawCircle(12, 12, 6.5f, stroke); break;
      case "bell": path.moveTo(6, 16.5f); path.lineTo(6, 11); path.cubicTo(6, 3.5f, 18, 3.5f, 18, 11); path.lineTo(18, 16.5f);
        path.lineTo(20, 18); path.lineTo(4, 18); path.close(); canvas.drawPath(path, stroke); line(canvas, 10, 21, 14, 21); break;
      case "language": canvas.drawCircle(12, 12, 9, stroke); line(canvas, 3, 12, 21, 12);
        canvas.drawOval(new RectF(8, 3, 16, 21), stroke); break;
      case "receipt": poly(canvas, 5, 3, 19, 3, 19, 21, 16.5f, 19.5f, 14, 21, 12, 19.5f, 10, 21, 7.5f, 19.5f, 5, 21, 5, 3);
        line(canvas, 8.5f, 8, 15.5f, 8); line(canvas, 8.5f, 12, 15.5f, 12); break;
      case "trash": line(canvas, 4, 6.5f, 20, 6.5f); poly(canvas, 9, 6.5f, 9.5f, 3.5f, 14.5f, 3.5f, 15, 6.5f);
        poly(canvas, 6, 6.5f, 7, 20.5f, 17, 20.5f, 18, 6.5f); line(canvas, 10, 10.5f, 10, 16.5f); line(canvas, 14, 10.5f, 14, 16.5f); break;
      case "clear": line(canvas, 4, 7, 20, 7); line(canvas, 6, 12, 18, 12); line(canvas, 8, 17, 16, 17); break;
      case "logout": poly(canvas, 13, 4, 5, 4, 5, 20, 13, 20); line(canvas, 10, 12, 20, 12); poly(canvas, 16.5f, 8, 20.5f, 12, 16.5f, 16); break;
      case "star": star(canvas); break;
      case "info": canvas.drawCircle(12, 12, 9, stroke); line(canvas, 12, 11, 12, 16.5f); dot(canvas, 12, 7.8f, 1.2f); break;
      case "doc": rect(canvas, 5, 3, 19, 21, 2); line(canvas, 8.5f, 8, 15.5f, 8); line(canvas, 8.5f, 12, 15.5f, 12); line(canvas, 8.5f, 16, 13, 16); break;
      case "shield": poly(canvas, 12, 3, 19.5f, 6, 19, 12.5f, 12, 21, 5, 12.5f, 4.5f, 6, 12, 3); poly(canvas, 9, 12, 11.2f, 14.2f, 15.5f, 9.5f); break;
      case "return": poly(canvas, 8, 4, 4, 8, 8, 12); path.moveTo(4, 8); path.lineTo(15, 8); path.cubicTo(22, 8, 22, 19, 15, 19);
        path.lineTo(9, 19); canvas.drawPath(path, stroke); break;
      case "live": canvas.drawCircle(12, 12, 2.5f, fill); arc(canvas, 12, 12, 6, -45, 90); arc(canvas, 12, 12, 6, 135, 90);
        arc(canvas, 12, 12, 9.5f, -45, 90); arc(canvas, 12, 12, 9.5f, 135, 90); break;
      case "trophy": path.moveTo(7, 4); path.lineTo(17, 4); path.lineTo(17, 9); path.cubicTo(17, 15, 7, 15, 7, 9); path.close();
        canvas.drawPath(path, stroke); path.reset(); path.moveTo(7, 6); path.cubicTo(2.5f, 6, 3.5f, 11.5f, 7.5f, 11);
        path.moveTo(17, 6); path.cubicTo(21.5f, 6, 20.5f, 11.5f, 16.5f, 11); canvas.drawPath(path, stroke);
        line(canvas, 12, 14, 12, 18); line(canvas, 8, 20, 16, 20); break;
      case "team": canvas.drawCircle(9, 8.5f, 3.2f, stroke); path.moveTo(3, 19.5f); path.cubicTo(3.5f, 13.5f, 14.5f, 13.5f, 15, 19.5f);
        canvas.drawPath(path, stroke); canvas.drawCircle(16.5f, 8, 2.6f, stroke); path.reset(); path.moveTo(16, 13.2f);
        path.cubicTo(19.5f, 13, 21, 15.5f, 21, 18.5f); canvas.drawPath(path, stroke); break;
      case "binoculars": canvas.drawCircle(7, 16, 3.8f, stroke); canvas.drawCircle(17, 16, 3.8f, stroke); line(canvas, 10.8f, 15.5f, 13.2f, 15.5f);
        poly(canvas, 3.5f, 15, 6, 5, 9.5f, 5, 10.5f, 12.5f); poly(canvas, 20.5f, 15, 18, 5, 14.5f, 5, 13.5f, 12.5f); break;
      case "help": canvas.drawCircle(12, 12, 9, stroke); path.moveTo(9.3f, 9.5f); path.cubicTo(9.3f, 5.8f, 15, 5.8f, 15, 9.3f);
        path.cubicTo(15, 11.8f, 12, 11.8f, 12, 14.3f); canvas.drawPath(path, stroke); dot(canvas, 12, 17.2f, 1.2f); break;
      case "home": poly(canvas, 3.5f, 11, 12, 4, 20.5f, 11); poly(canvas, 6, 9.5f, 6, 20, 18, 20, 18, 9.5f); rect(canvas, 10, 14, 14, 20, 0.5f); break;
      case "download": line(canvas, 12, 4, 12, 15); poly(canvas, 7.5f, 10.5f, 12, 15, 16.5f, 10.5f); poly(canvas, 4, 16, 4, 20, 20, 20, 20, 16); break;
      case "link": rect(canvas, 3, 9, 13, 15, 3); rect(canvas, 11, 9, 21, 15, 3); break;
      case "filter": poly(canvas, 4, 5, 20, 5, 14, 12.5f, 14, 19, 10, 21, 10, 12.5f, 4, 5); break;
      case "scan": poly(canvas, 3.5f, 8, 3.5f, 3.5f, 8, 3.5f); poly(canvas, 16, 3.5f, 20.5f, 3.5f, 20.5f, 8);
        poly(canvas, 20.5f, 16, 20.5f, 20.5f, 16, 20.5f); poly(canvas, 8, 20.5f, 3.5f, 20.5f, 3.5f, 16); line(canvas, 6, 12, 18, 12); break;
      case "image": rect(canvas, 3.5f, 4.5f, 20.5f, 19.5f, 2); canvas.drawCircle(9, 9.5f, 1.8f, stroke);
        poly(canvas, 4, 18, 10, 12.5f, 14, 16, 16.5f, 13.5f, 20.5f, 17.5f); break;
      default: canvas.drawCircle(12, 12, 8, stroke);
    }
    canvas.restore();
  }

  private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, stroke); }

  private void poly(Canvas c, float... xy) {
    Path p = new Path();
    p.moveTo(xy[0], xy[1]);
    for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
    c.drawPath(p, stroke);
  }

  private void rect(Canvas c, float l, float t, float rr, float b, float radius) {
    r.set(l, t, rr, b);
    c.drawRoundRect(r, radius, radius, stroke);
  }

  private void dot(Canvas c, float x, float y, float radius) { c.drawCircle(x, y, radius, fill); }

  private void arc(Canvas c, float cx, float cy, float radius, float start, float sweep) {
    c.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius), start, sweep, false, stroke);
  }

  private void star(Canvas c) {
    Path p = new Path();
    for (int i = 0; i < 10; i++) {
      double a = -Math.PI / 2 + i * Math.PI / 5;
      float radius = i % 2 == 0 ? 9 : 4;
      float x = 12 + (float) Math.cos(a) * radius, y = 12.5f + (float) Math.sin(a) * radius;
      if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
    }
    p.close();
    c.drawPath(p, stroke);
  }
}
