package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/** Canvas charts for My Performance: bars, a line with tap tooltips, a pie with legend, and a one-bar meter. */
public final class Charts {
  private Charts() {}

  public static final int BLUE = 0xff3d8fd1, AMBER = 0xffe8a93a, CORAL = 0xffe07856, PURPLE = 0xff9b6bd3,
      LIME = 0xffaac83a, CYAN = 0xff3fb8c0, PINK = 0xffd16ba5;

  /** Categorical colours, accent first, that read on both the light and the dark surface. */
  public static int[] series(Ui.Palette p) {
    return new int[] {p.accent, BLUE, AMBER, CORAL, PURPLE, LIME, CYAN, PINK};
  }

  /** Text for a tapped bar or point. */
  public interface Tooltip { String text(int series, int index); }

  /** Near-black or white, whichever reads better on the colour. */
  public static int onColor(int color) {
    double light = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
    return light > 0.62 ? 0xff17251d : 0xffffffff;
  }

  /** Step of about {@code ticks} gridlines up to max: 1, 2, 2.5 or 5 times a power of ten. */
  static double niceStep(double max, int ticks, boolean integers) {
    double raw = Math.max(max, 1e-9) / ticks;
    double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
    double n = raw / magnitude;
    double step = (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * magnitude;
    return integers ? Math.max(1, Math.ceil(step)) : step;
  }

  /** Plot frame shared by the bar and line charts: gridlines, tick labels, axis titles and a tooltip bubble. */
  abstract static class AxisChart extends View {
    final Ui.Palette p;
    final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG), bubbleText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final RectF box = new RectF();
    String yTitle = "", xTitle = "";
    Tooltip tooltip;
    boolean integers;
    int heightDp = 200;
    float left, top, right, bottom;
    double axisMax = 1, step = 1;

    AxisChart(Context c, Ui.Palette p) {
      super(c);
      this.p = p;
      grid.setColor(p.line);
      grid.setStrokeWidth(Math.max(1, dp(0.8f)));
      text.setColor(p.muted);
      text.setTextSize(sp(11));
      bubbleText.setColor(p.surface);
      bubbleText.setTextSize(sp(12.5f));
      bubbleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

    @Override protected void onMeasure(int w, int h) {
      setMeasuredDimension(MeasureSpec.getSize(w), Math.round(dp(heightDp)));
    }

    float y(double v) { return (float) (bottom - v / axisMax * (bottom - top)); }

    String tick(double v) {
      return Math.abs(step - Math.rint(step)) < 1e-9 ? String.valueOf(Math.round(v)) : String.format(Locale.US, "%.1f", v);
    }

    /** Lays out the plot area for values up to {@code max} and draws gridlines, ticks and titles. */
    void frame(Canvas c, double max) {
      if (!(max > 0)) max = 1;
      step = niceStep(max, 4, integers);
      axisMax = Math.max(step, Math.ceil(max / step - 1e-9) * step);
      float labelWidth = 0;
      for (double v = 0; v <= axisMax + step / 2; v += step) labelWidth = Math.max(labelWidth, text.measureText(tick(v)));
      float titleWidth = yTitle.isEmpty() ? 0 : text.getTextSize() + dp(8);
      left = getPaddingLeft() + titleWidth + labelWidth + dp(8);
      right = getWidth() - getPaddingRight() - dp(6);
      top = dp(16);
      bottom = getHeight() - dp(24) - (xTitle.isEmpty() ? 0 : dp(22));
      text.setTextAlign(Paint.Align.RIGHT);
      for (double v = 0; v <= axisMax + step / 2; v += step) {
        float y = y(v);
        c.drawLine(left, y, right, y, grid);
        c.drawText(tick(v), left - dp(6), y + text.getTextSize() / 3, text);
      }
      c.drawLine(left, top, left, bottom, grid);
      text.setTextAlign(Paint.Align.CENTER);
      if (!yTitle.isEmpty()) {
        float x = getPaddingLeft() + text.getTextSize(), mid = (top + bottom) / 2;
        c.save();
        c.rotate(-90, x, mid);
        c.drawText(TextUtils.ellipsize(yTitle, text, bottom - top, TextUtils.TruncateAt.END).toString(), x, mid, text);
        c.restore();
      }
      if (!xTitle.isEmpty()) c.drawText(xTitle, (left + right) / 2, getHeight() - dp(6), text);
    }

    /** Dark bubble with the text centred above (x, y), kept inside the view. */
    void bubble(Canvas c, String s, float x, float y) {
      float w = bubbleText.measureText(s) + dp(18), h = dp(28);
      float l = Math.max(dp(2), Math.min(x - w / 2, getWidth() - w - dp(2)));
      float t = y - h - dp(8);
      if (t < dp(2)) t = y + dp(8);
      box.set(l, t, l + w, t + h);
      fill.setAlpha(255);
      fill.setColor(p.ink);
      c.drawRoundRect(box, dp(7), dp(7), fill);
      bubbleText.setTextAlign(Paint.Align.CENTER);
      c.drawText(s, box.centerX(), box.centerY() + bubbleText.getTextSize() / 3, bubbleText);
    }

    @Override public boolean performClick() { return super.performClick(); }
  }

  /** Vertical bars with gridlines, axis titles, a colour per bar or per series, and a tap tooltip. */
  public static final class BarChartView extends AxisChart {
    private String[] labels = new String[0];
    private double[][] values = new double[0][];
    private int[][] colors = new int[0][];
    private int selectedSeries = -1, selected = -1;
    private final Path bar = new Path();

    public BarChartView(Context c, Ui.Palette p) { super(c, p); }

    /** One series; colours repeat when fewer than the bars. NaN draws no bar. */
    public BarChartView bars(String[] labels, double[] values, int... barColors) {
      int[] cs = new int[labels.length];
      for (int i = 0; i < cs.length; i++) cs[i] = barColors[i % barColors.length];
      return set(labels, new double[][] {values}, new int[][] {cs});
    }

    /** Series side by side in each category, one colour per series. */
    public BarChartView grouped(String[] labels, double[][] values, int[] seriesColors) {
      int[][] cs = new int[values.length][labels.length];
      for (int s = 0; s < values.length; s++) java.util.Arrays.fill(cs[s], seriesColors[s % seriesColors.length]);
      return set(labels, values, cs);
    }

    private BarChartView set(String[] labels, double[][] values, int[][] colors) {
      this.labels = labels;
      this.values = values;
      this.colors = colors;
      selected = -1;
      invalidate();
      return this;
    }

    public BarChartView axes(String yTitle, String xTitle) { this.yTitle = yTitle; this.xTitle = xTitle; invalidate(); return this; }

    public BarChartView tooltip(Tooltip t) { tooltip = t; return this; }

    public BarChartView integers() { integers = true; return this; }

    public BarChartView height(int dp) { heightDp = dp; requestLayout(); return this; }

    private float slot() { return (right - left) / Math.max(1, labels.length); }

    private float group() { return slot() * (values.length > 1 ? 0.8f : 0.62f); }

    @Override protected void onDraw(Canvas c) {
      double max = 0;
      for (double[] series : values) for (double v : series) if (!Double.isNaN(v)) max = Math.max(max, v);
      frame(c, max);
      int n = labels.length;
      if (n == 0) return;
      float slot = slot(), group = group(), width = group / values.length, radius = Math.min(dp(4), width / 3);
      for (int i = 0; i < n; i++)
        for (int s = 0; s < values.length; s++) {
          double v = values[s][i];
          if (Double.isNaN(v) || v <= 0) continue;
          float x = left + slot * i + (slot - group) / 2 + width * s, gap = values.length > 1 ? dp(1) : 0;
          fill.setColor(colors[s][i]);
          fill.setAlpha(selected >= 0 && (selected != i || selectedSeries != s) ? 120 : 255);
          bar.reset();
          bar.addRoundRect(new RectF(x + gap, y(v), x + width - gap, bottom),
              new float[] {radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
          c.drawPath(bar, fill);
        }
      fill.setAlpha(255);
      text.setTextAlign(Paint.Align.CENTER);
      text.setColor(p.muted);
      float widest = 0;
      boolean numeric = true;
      for (String l : labels) { widest = Math.max(widest, text.measureText(l)); numeric &= l.matches("\\d+"); }
      int every = numeric ? Math.max(1, (int) Math.ceil((widest + dp(8)) / slot)) : 1; // words are shortened instead
      for (int i = 0; i < n; i++) {
        if (every > 1 && Integer.parseInt(labels[i]) % every != 0) continue;
        CharSequence label = TextUtils.ellipsize(labels[i], text, slot * every - dp(4), TextUtils.TruncateAt.END);
        c.drawText(label.toString(), left + slot * i + slot / 2, bottom + dp(16), text);
      }
      if (selected >= 0 && tooltip != null) {
        double v = values[selectedSeries][selected];
        float x = left + slot * selected + (slot - group) / 2 + width * selectedSeries + width / 2;
        bubble(c, tooltip.text(selectedSeries, selected), x, y(Double.isNaN(v) ? 0 : Math.max(0, v)));
      }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
      if (tooltip == null || labels.length == 0) return super.onTouchEvent(e);
      if (e.getAction() == MotionEvent.ACTION_UP) {
        int i = (int) Math.floor((e.getX() - left) / slot());
        int s = 0;
        if (values.length > 1) {
          float x0 = left + slot() * i + (slot() - group()) / 2;
          s = Math.max(0, Math.min(values.length - 1, (int) Math.floor((e.getX() - x0) / (group() / values.length))));
        }
        if (i < 0 || i >= labels.length || Double.isNaN(values[s][i]) || i == selected && s == selectedSeries) selected = -1;
        else { selected = i; selectedSeries = s; }
        invalidate();
        performClick();
      }
      return true;
    }
  }

  /** Line through values at x = 1..n with marked points; tapping shows a crosshair and a tooltip. */
  public static final class LineChartView extends AxisChart {
    private double[] values = new double[0];
    private int selected = -1;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public LineChartView(Context c, Ui.Palette p) {
      super(c, p);
      line.setStyle(Paint.Style.STROKE);
      line.setStrokeJoin(Paint.Join.ROUND);
      line.setStrokeCap(Paint.Cap.ROUND);
      line.setColor(p.accent);
      line.setStrokeWidth(dp(2.2f));
      dot.setStrokeWidth(dp(1.6f));
    }

    public LineChartView values(double[] values) { this.values = values; selected = -1; invalidate(); return this; }

    public LineChartView axes(String yTitle, String xTitle) { this.yTitle = yTitle; this.xTitle = xTitle; invalidate(); return this; }

    public LineChartView tooltip(Tooltip t) { tooltip = t; return this; }

    public LineChartView height(int dp) { heightDp = dp; requestLayout(); return this; }

    private float pad() { return dp(10); }

    private float x(int i) {
      int n = values.length;
      return n <= 1 ? (left + right) / 2 : left + pad() + i * (right - left - 2 * pad()) / (n - 1);
    }

    @Override protected void onDraw(Canvas c) {
      double max = 0;
      for (double v : values) if (!Double.isNaN(v)) max = Math.max(max, v);
      frame(c, max);
      int n = values.length;
      if (n == 0) return;
      text.setTextAlign(Paint.Align.CENTER);
      int every = (int) niceStep(n, 6, true);
      for (int b = every; b <= n; b += every) c.drawText(String.valueOf(b), x(b - 1), bottom + dp(16), text);
      if (n < every) c.drawText("1", x(0), bottom + dp(16), text);
      if (selected >= 0) {
        line.setAlpha(110);
        line.setStrokeWidth(dp(1));
        c.drawLine(x(selected), top, x(selected), bottom, line);
        c.drawLine(left, y(values[selected]), right, y(values[selected]), line);
        line.setAlpha(255);
        line.setStrokeWidth(dp(2.2f));
      }
      path.reset();
      for (int i = 0; i < n; i++) {
        if (i == 0) path.moveTo(x(i), y(values[i]));
        else path.lineTo(x(i), y(values[i]));
      }
      c.drawPath(path, line);
      if (n <= 40)
        for (int i = 0; i < n; i++) {
          dot.setStyle(Paint.Style.FILL);
          dot.setColor(p.surface);
          c.drawCircle(x(i), y(values[i]), dp(3.6f), dot);
          dot.setStyle(Paint.Style.STROKE);
          dot.setColor(p.accent);
          c.drawCircle(x(i), y(values[i]), dp(3.6f), dot);
        }
      if (selected >= 0 && tooltip != null) bubble(c, tooltip.text(0, selected), x(selected), y(values[selected]));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
      if (tooltip == null || values.length == 0) return super.onTouchEvent(e);
      if (e.getAction() == MotionEvent.ACTION_UP) {
        int n = values.length;
        int i = n <= 1 ? 0 : Math.round((e.getX() - left - pad()) / ((right - left - 2 * pad()) / (n - 1)));
        i = Math.max(0, Math.min(n - 1, i));
        selected = i == selected ? -1 : i;
        invalidate();
        performClick();
      }
      return true;
    }
  }

  /** Pie with percentage labels inside the slices and a legend on the right. */
  public static final class PieChartView extends View {
    private final Ui.Palette p;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private String[] labels = new String[0];
    private double[] values = new double[0];
    private int[] colors = new int[0];

    public PieChartView(Context c, Ui.Palette p) {
      super(c);
      this.p = p;
      edge.setStyle(Paint.Style.STROKE);
      edge.setColor(p.surface);
      edge.setStrokeWidth(dp(1.5f));
      text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

    /** Slices in order; colours repeat when fewer than the slices. */
    public PieChartView slices(String[] labels, double[] values, int[] colors) {
      this.labels = labels;
      this.values = values;
      this.colors = colors;
      requestLayout();
      invalidate();
      return this;
    }

    @Override protected void onMeasure(int w, int h) {
      setMeasuredDimension(MeasureSpec.getSize(w), Math.round(Math.max(dp(190), labels.length * dp(24) + dp(24))));
    }

    @Override protected void onDraw(Canvas c) {
      double total = 0;
      for (double v : values) if (v > 0) total += v;
      if (total <= 0) return;
      float h = getHeight(), d = Math.min(h - dp(16), getWidth() * 0.5f), r = d / 2, cx = dp(8) + r, cy = h / 2;
      oval.set(cx - r, cy - r, cx + r, cy + r);
      float start = -90;
      text.setTextAlign(Paint.Align.CENTER);
      text.setTextSize(sp(12));
      for (int i = 0; i < values.length; i++) {
        if (!(values[i] > 0)) continue;
        float sweep = (float) (values[i] / total * 360);
        int color = colors[i % colors.length];
        fill.setColor(color);
        c.drawArc(oval, start, sweep, true, fill);
        if (sweep < 359.9f) c.drawArc(oval, start, sweep, true, edge);
        if (sweep >= 16) {
          double mid = Math.toRadians(start + sweep / 2);
          float tx = cx + (float) Math.cos(mid) * r * 0.62f, ty = cy + (float) Math.sin(mid) * r * 0.62f;
          text.setColor(onColor(color));
          c.drawText(Math.round(values[i] * 100 / total) + "%", tx, ty + text.getTextSize() / 3, text);
        }
        start += sweep;
      }
      float x = cx + r + dp(20), row = dp(24), y = cy - (labels.length * row) / 2 + row / 2;
      text.setTextAlign(Paint.Align.LEFT);
      text.setTextSize(sp(13));
      text.setColor(p.ink);
      for (int i = 0; i < labels.length; i++) {
        fill.setColor(colors[i % colors.length]);
        c.drawRoundRect(new RectF(x, y - dp(5), x + dp(11), y + dp(6)), dp(2), dp(2), fill);
        CharSequence label = TextUtils.ellipsize(labels[i], text, getWidth() - x - dp(22), TextUtils.TruncateAt.END);
        c.drawText(label.toString(), x + dp(18), y + text.getTextSize() / 3, text);
        y += row;
      }
    }
  }

  /** One horizontal bar filling a fraction of the width, from the start or from the end. */
  public static final class Meter extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float fraction;
    private final boolean fromEnd;

    public Meter(Context c, float fraction, int color, boolean fromEnd) {
      super(c);
      this.fraction = Math.max(0, Math.min(1, fraction));
      this.fromEnd = fromEnd;
      fill.setColor(color);
    }

    @Override protected void onMeasure(int w, int h) {
      setMeasuredDimension(MeasureSpec.getSize(w), Math.round(22 * getResources().getDisplayMetrics().density));
    }

    @Override protected void onDraw(Canvas c) {
      float w = getWidth() * fraction, radius = getResources().getDisplayMetrics().density * 3;
      if (w <= 0) return;
      if (fromEnd) rect.set(getWidth() - w, 0, getWidth(), getHeight());
      else rect.set(0, 0, w, getHeight());
      c.drawRoundRect(rect, radius, radius, fill);
    }
  }
}
