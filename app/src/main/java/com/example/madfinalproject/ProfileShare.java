package com.example.madfinalproject;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import androidx.core.content.FileProvider;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;

/** Images for the profile: the QR code, glyphs IconView lacks, and saving or sharing a rendered card. */
final class ProfileShare {
  private ProfileShare() {}

  static final String INSTAGRAM = "com.instagram.android", FACEBOOK = "com.facebook.katana", WHATSAPP = "com.whatsapp";

  /** Square QR code of the text with high error correction, so the Boundary "B" in the middle stays readable. */
  static Bitmap qr(String text, int sizePx, int markColor) throws WriterException {
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
    hints.put(EncodeHintType.MARGIN, 0);
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
    int n = matrix.getWidth();
    Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    Paint module = new Paint();
    module.setColor(0xff111111);
    float cell = sizePx / (float) n;
    for (int y = 0; y < n; y++)
      for (int x = 0; x < n; x++)
        if (matrix.get(x, y))
          canvas.drawRect(Math.round(x * cell), Math.round(y * cell), Math.round((x + 1) * cell), Math.round((y + 1) * cell), module);

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float c = sizePx / 2f, outer = sizePx * 0.13f, inner = sizePx * 0.105f;
    paint.setColor(Color.WHITE);
    canvas.drawRoundRect(new RectF(c - outer, c - outer, c + outer, c + outer), outer * 0.35f, outer * 0.35f, paint);
    paint.setColor(markColor);
    canvas.drawRoundRect(new RectF(c - inner, c - inner, c + inner, c + inner), inner * 0.3f, inner * 0.3f, paint);
    paint.setColor(Color.WHITE);
    paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(inner * 1.45f);
    Paint.FontMetrics fm = paint.getFontMetrics();
    canvas.drawText("B", c, c - (fm.ascent + fm.descent) / 2, paint);
    return bitmap;
  }

  /**
   * Draws a laid-out view into a bitmap about {@code widthPx} wide. Software drawing ignores outline
   * clipping, so views drawn this way round their corners with drawables only.
   */
  static Bitmap snapshot(View v, int widthPx, int background) {
    float scale = widthPx / (float) Math.max(1, v.getWidth());
    Bitmap b = Bitmap.createBitmap(Math.max(1, Math.round(v.getWidth() * scale)), Math.max(1, Math.round(v.getHeight() * scale)),
        Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(b);
    canvas.drawColor(background);
    canvas.scale(scale, scale);
    v.draw(canvas);
    return b;
  }

  /** Shares the image (and text, when given) to one app, falling back to the system chooser. */
  static void share(Activity activity, Bitmap image, String name, String text, String targetPackage) throws IOException {
    File dir = new File(activity.getCacheDir(), "share");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not prepare the image");
    File file = new File(dir, name + ".png");
    try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
    Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
    Intent send = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    send.setClipData(ClipData.newRawUri(name, uri));
    if (text != null && !text.isEmpty()) send.putExtra(Intent.EXTRA_TEXT, text);
    if (targetPackage != null) {
      try {
        activity.startActivity(new Intent(send).setPackage(targetPackage));
        return;
      } catch (ActivityNotFoundException notInstalled) {
        // fall through to the chooser
      }
    }
    activity.startActivity(Intent.createChooser(send, "Share via"));
  }

  /** Below Android 10 the caller must hold WRITE_EXTERNAL_STORAGE. */
  static boolean needsStoragePermission() { return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q; }

  /** Saves a PNG into Pictures/Boundary so it shows up in the gallery. */
  static void saveToGallery(Context context, Bitmap image, String name) throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContentResolver resolver = context.getContentResolver();
      ContentValues values = new ContentValues();
      values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
      values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
      values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Boundary");
      values.put(MediaStore.Images.Media.IS_PENDING, 1);
      Uri uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
      if (uri == null) throw new IOException("The gallery is unavailable");
      try (OutputStream out = resolver.openOutputStream(uri)) {
        if (out == null || !image.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("Could not write the image");
      } catch (IOException | RuntimeException e) {
        resolver.delete(uri, null, null);
        throw e;
      }
      values.clear();
      values.put(MediaStore.Images.Media.IS_PENDING, 0);
      resolver.update(uri, values, null, null);
      return;
    }
    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Boundary");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not open Pictures/Boundary");
    File file = new File(dir, name + ".png");
    try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
    MediaScannerConnection.scanFile(context, new String[] {file.getAbsolutePath()}, new String[] {"image/png"}, null);
  }

  /** Glyphs IconView does not have: instagram, facebook, whatsapp, and tennis or leather balls. */
  static final class Glyph extends View {
    private final String name;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Glyph(Context c, String name, int color) {
      super(c);
      this.name = name;
      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeCap(Paint.Cap.ROUND);
      stroke.setStrokeJoin(Paint.Join.ROUND);
      stroke.setStrokeWidth(1.8f);
      stroke.setColor(color);
      fill.setColor(color);
      setContentDescription(name);
    }

    @Override protected void onDraw(Canvas canvas) {
      float s = Math.min(getWidth(), getHeight()) / 24f;
      canvas.save();
      canvas.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
      canvas.scale(s, s);
      path.reset();
      switch (name) {
        case "instagram":
          canvas.drawRoundRect(new RectF(3.5f, 3.5f, 20.5f, 20.5f), 5.5f, 5.5f, stroke);
          canvas.drawCircle(12, 12, 4, stroke);
          canvas.drawCircle(16.9f, 7.1f, 1.1f, fill);
          break;
        case "facebook":
          stroke.setStrokeWidth(2.2f);
          path.moveTo(13.2f, 21);
          path.lineTo(13.2f, 9.5f);
          path.cubicTo(13.2f, 6.2f, 14.6f, 4.6f, 17.6f, 4.8f);
          canvas.drawPath(path, stroke);
          canvas.drawLine(9.6f, 11.4f, 16.8f, 11.4f, stroke);
          break;
        case "whatsapp":
          canvas.drawArc(new RectF(3.8f, 3.3f, 20.8f, 20.3f), 128, 332, false, stroke);
          path.moveTo(6.5f, 17.9f);
          path.lineTo(4.2f, 20.6f);
          path.lineTo(8.1f, 19.4f);
          canvas.drawPath(path, stroke);
          path.reset();
          path.moveTo(9.5f, 7.9f);
          path.lineTo(10.7f, 10.1f);
          path.lineTo(9.9f, 11.1f);
          path.cubicTo(10.6f, 12.6f, 11.9f, 13.9f, 13.4f, 14.6f);
          path.lineTo(14.4f, 13.8f);
          path.lineTo(16.5f, 15.0f);
          path.lineTo(15.9f, 16.4f);
          path.cubicTo(12.2f, 16.8f, 7.7f, 12.3f, 8.1f, 8.5f);
          path.close();
          canvas.drawPath(path, fill);
          break;
        case "tennis":
        case "leather":
          ball(canvas, name.equals("tennis"));
          break;
        default:
          canvas.drawCircle(12, 12, 8, stroke);
      }
      canvas.restore();
    }

    /** Optic-yellow tennis ball with curved seams, or a cherry leather ball with its stitched seam. */
    private void ball(Canvas canvas, boolean tennis) {
      Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
      body.setColor(tennis ? 0xffc6dc3a : 0xffa3242a);
      canvas.drawCircle(12, 12, 10, body);
      path.addCircle(12, 12, 10, Path.Direction.CW);
      canvas.save();
      canvas.clipPath(path);
      Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
      seam.setStyle(Paint.Style.STROKE);
      seam.setColor(Color.WHITE);
      if (tennis) {
        seam.setStrokeWidth(1.7f);
        canvas.drawArc(new RectF(-12, 1, 8.5f, 23), -60, 120, false, seam);
        canvas.drawArc(new RectF(15.5f, 1, 36, 23), 120, 120, false, seam);
      } else {
        seam.setStrokeWidth(1.1f);
        canvas.drawLine(10.6f, 1, 10.6f, 23, seam);
        canvas.drawLine(13.4f, 1, 13.4f, 23, seam);
        seam.setStrokeWidth(0.8f);
        for (float y = 4; y <= 20; y += 2.6f) {
          canvas.drawLine(9, y, 10.6f, y + 0.6f, seam);
          canvas.drawLine(13.4f, y + 0.6f, 15, y, seam);
        }
      }
      canvas.restore();
    }
  }
}
