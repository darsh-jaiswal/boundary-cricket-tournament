package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Teamwear drawn in code on a 100-unit grid: a jersey, full-sleeve jersey or cap painted in a design colourway. */
public final class ProductArt extends View {
  private final String category;
  private final boolean dark;
  private Storefront.Design design;
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path shape = new Path(), extra = new Path(), stitch = new Path();
  private final RectF oval = new RectF();
  private final Shader light = new LinearGradient(0, 0, 100, 0, new int[] {0x22ffffff, 0x00ffffff, 0x2e000000},
      new float[] {0, 0.45f, 1}, Shader.TileMode.CLAMP);
  private final DashPathEffect dashes = new DashPathEffect(new float[] {1.6f, 1.4f}, 0);
  private float scale;

  public ProductArt(Context c, String category, Storefront.Design design, boolean dark) {
    super(c);
    this.category = category;
    this.design = design;
    this.dark = dark;
    line.setStyle(Paint.Style.STROKE);
    line.setStrokeCap(Paint.Cap.ROUND);
    line.setStrokeJoin(Paint.Join.ROUND);
    fill.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    fill.setTextAlign(Paint.Align.CENTER);
    setContentDescription(design.name + " design");
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  public void setDesign(Storefront.Design d) {
    design = d;
    setContentDescription(d.name + " design");
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    int w = getWidth(), h = getHeight();
    if (w == 0 || h == 0) return;
    backdrop(canvas, w, h);
    float size = Math.min(w * 0.9f, h * 0.78f);
    scale = size / 100f;
    canvas.save();
    canvas.translate((w - size) / 2f, (h - size) / 2f);
    canvas.scale(scale, scale);
    fill.setShader(null);
    fill.setColor(dark ? 0x66000000 : 0x24142a24);
    oval.set(22, 93, 78, 100);
    canvas.drawOval(oval, fill);
    if ("cap".equals(category)) cap(canvas);
    else shirt(canvas, "full-sleeve".equals(category));
    canvas.restore();
  }

  /** Soft studio backdrop; white kit gets a darker one, as in a product photo. */
  private void backdrop(Canvas canvas, int w, int h) {
    int center, edge;
    if (design.light()) { center = dark ? 0xff3d4b44 : 0xff66736c; edge = dark ? 0xff232d28 : 0xff434e48; }
    else { center = dark ? 0xff34433b : 0xfffafbf7; edge = dark ? 0xff1d2621 : 0xffdfe7da; }
    fill.setShader(new RadialGradient(w / 2f, h * 0.42f, Math.max(w, h) * 0.75f, center, edge, Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, w, h, fill);
    fill.setShader(null);
  }

  private void shirt(Canvas canvas, boolean longSleeves) {
    Path s = shape;
    s.reset();
    s.moveTo(37, 9);
    s.quadTo(50, 19, 63, 9);
    s.lineTo(78, 14);
    if (longSleeves) {
      s.quadTo(85, 17, 87, 26);
      s.lineTo(94, 79);
      s.lineTo(84, 82);
      s.lineTo(76, 40);
    } else {
      s.lineTo(93, 33);
      s.lineTo(82, 42);
      s.lineTo(75, 36);
    }
    s.lineTo(75, 93);
    s.quadTo(50, 96, 25, 93);
    if (longSleeves) {
      s.lineTo(24, 40);
      s.lineTo(16, 82);
      s.lineTo(6, 79);
      s.lineTo(13, 26);
      s.quadTo(15, 17, 22, 14);
    } else {
      s.lineTo(25, 36);
      s.lineTo(18, 42);
      s.lineTo(7, 33);
      s.lineTo(22, 14);
    }
    s.close();

    fill.setColor(design.body);
    canvas.drawPath(s, fill);
    canvas.save();
    canvas.clipPath(s);
    pattern(canvas, false);
    // Cuffs and hem in the trim colour.
    stroke(design.trim, 3.2f);
    if (longSleeves) { canvas.drawLine(94, 79, 84, 82, line); canvas.drawLine(6, 79, 16, 82, line); }
    else { canvas.drawLine(93, 33, 82, 42, line); canvas.drawLine(7, 33, 18, 42, line); }
    shade(canvas);
    canvas.restore();

    // Polo collar and placket.
    stroke(design.trim, 3.4f);
    extra.reset();
    extra.moveTo(37, 9);
    extra.quadTo(50, 19, 63, 9);
    canvas.drawPath(extra, line);
    stroke(design.trim, 1.6f);
    canvas.drawLine(50, 15.5f, 50, 28, line);
    fill.setColor(design.stripe);
    canvas.drawCircle(50, 21, 0.8f, fill);
    canvas.drawCircle(50, 25, 0.8f, fill);
    crest(canvas, 38, 29, 3.6f);
    label(canvas, "BOUNDARY", 62, 30.2f, 2.6f, design.light() ? design.stripe : 0xccffffff);
    outline(canvas, s);
  }

  private void cap(Canvas canvas) {
    Path crown = shape;
    crown.reset();
    crown.moveTo(17, 66);
    crown.cubicTo(16, 31, 36, 18, 50, 18);
    crown.cubicTo(64, 18, 84, 31, 83, 66);
    crown.quadTo(50, 72, 17, 66);
    crown.close();
    fill.setColor(design.body);
    canvas.drawPath(crown, fill);
    canvas.save();
    canvas.clipPath(crown);
    pattern(canvas, true);
    stroke(design.light() ? 0x33000000 : 0x40000000, 0.8f);
    extra.reset();
    extra.moveTo(50, 19);
    extra.quadTo(36, 30, 33, 68);
    extra.moveTo(50, 19);
    extra.quadTo(64, 30, 67, 68);
    canvas.drawPath(extra, line);
    shade(canvas);
    canvas.restore();
    fill.setColor(design.trim);
    canvas.drawCircle(50, 18.5f, 2.2f, fill);
    canvas.drawCircle(37, 35, 0.9f, fill);
    canvas.drawCircle(63, 35, 0.9f, fill);
    crest(canvas, 50, 45, 6);
    outline(canvas, crown);

    Path brim = extra;
    brim.reset();
    brim.moveTo(13, 66);
    brim.quadTo(50, 76, 87, 66);
    brim.quadTo(84, 86, 50, 87);
    brim.quadTo(16, 86, 13, 66);
    brim.close();
    fill.setColor(design.light() ? design.stripe : design.trim);
    canvas.drawPath(brim, fill);
    canvas.save();
    canvas.clipPath(brim);
    shade(canvas);
    canvas.restore();
    // Stitching along the bill.
    stroke(design.light() ? 0xccffffff : design.stripe, 0.6f);
    line.setPathEffect(dashes);
    stitch.reset();
    stitch.moveTo(17, 70);
    stitch.quadTo(50, 89, 83, 70);
    canvas.drawPath(stitch, line);
    line.setPathEffect(null);
    outline(canvas, brim);
  }

  /** The design's stripe pattern, drawn inside the current clip. */
  private void pattern(Canvas canvas, boolean cap) {
    switch (design.pattern) {
      case Storefront.Design.WAVES:
        float top = cap ? 50 : 58, gap = cap ? 4 : 5.5f;
        for (int i = 0; i < (cap ? 4 : 7); i++) {
          float y = top + i * gap;
          stroke(design.stripe, 0.8f + i * 0.35f);
          extra.reset();
          extra.moveTo(0, y);
          extra.cubicTo(30, y - 4, 70, y + 4, 100, y - 2);
          canvas.drawPath(extra, line);
        }
        if (!cap) {
          stroke(design.stripe, 2.2f);
          extra.reset();
          extra.moveTo(80, 18);
          extra.quadTo(62, 55, 74, 96);
          canvas.drawPath(extra, line);
        }
        break;
      case Storefront.Design.SASH:
        stroke(design.stripe, cap ? 7 : 9);
        line.setStrokeCap(Paint.Cap.BUTT);
        canvas.drawLine(cap ? 20 : 18, cap ? 22 : 12, cap ? 80 : 86, cap ? 70 : 96, line);
        stroke(design.trim, 1.2f);
        line.setStrokeCap(Paint.Cap.BUTT);
        float o = cap ? 5 : 6.5f;
        canvas.drawLine((cap ? 20 : 18) + o, (cap ? 22 : 12) - o, (cap ? 80 : 86) + o, (cap ? 70 : 96) - o, line);
        line.setStrokeCap(Paint.Cap.ROUND);
        break;
      case Storefront.Design.BAND:
        fill.setColor(design.stripe);
        if (cap) canvas.drawRect(0, 56, 100, 62, fill);
        else {
          canvas.drawRect(0, 33, 100, 41, fill);
          fill.setColor(design.trim);
          canvas.drawRect(0, 41, 100, 42.2f, fill);
          stroke(design.stripe, 1.1f);
          canvas.drawLine(0, 46, 100, 46, line);
        }
        break;
      default:
        // Piping: fine accent lines on the seams.
        stroke(design.stripe, 1.3f);
        extra.reset();
        if (cap) {
          extra.moveTo(17, 64);
          extra.quadTo(50, 70, 83, 64);
        } else {
          extra.moveTo(28, 38);
          extra.quadTo(31, 66, 28, 94);
          extra.moveTo(72, 38);
          extra.quadTo(69, 66, 72, 94);
          extra.moveTo(37, 9.5f);
          extra.lineTo(22, 14.5f);
          extra.moveTo(63, 9.5f);
          extra.lineTo(78, 14.5f);
        }
        canvas.drawPath(extra, line);
    }
  }

  /** Light from the left, so the flat colour reads as fabric. */
  private void shade(Canvas canvas) {
    fill.setShader(light);
    canvas.drawRect(0, 0, 100, 100, fill);
    fill.setShader(null);
  }

  private void crest(Canvas canvas, float x, float y, float r) {
    fill.setColor(design.stripe);
    canvas.drawCircle(x, y, r, fill);
    label(canvas, "B", x, y + r * 0.42f, r * 1.25f, design.light() ? 0xffffffff : design.body);
  }

  /** Text is drawn at device scale so it stays sharp. */
  private void label(Canvas canvas, String text, float x, float y, float size, int color) {
    canvas.save();
    canvas.scale(1 / scale, 1 / scale);
    fill.setColor(color);
    fill.setTextSize(size * scale);
    fill.setLetterSpacing(text.length() > 1 ? 0.12f : 0);
    canvas.drawText(text, x * scale, y * scale, fill);
    canvas.restore();
  }

  private void outline(Canvas canvas, Path p) {
    stroke(dark ? 0x30ffffff : 0x26000000, 0.5f);
    canvas.drawPath(p, line);
  }

  private void stroke(int color, float width) {
    line.setColor(color);
    line.setStrokeWidth(width);
  }
}
