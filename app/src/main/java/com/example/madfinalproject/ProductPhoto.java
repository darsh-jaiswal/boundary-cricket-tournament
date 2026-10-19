package com.example.madfinalproject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Original catalog photography, downloaded off the UI thread and cached for repeat visits. */
public final class ProductPhoto extends FrameLayout {
  private static final ExecutorService IO = Executors.newFixedThreadPool(2);
  private static final int MAX_BYTES = 4_000_000;
  private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
    @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
  };
  private final ImageView image;
  private final TextView note;
  private final String productId, path;
  private boolean loading;

  public ProductPhoto(Context context, String productId, String name, Ui.Palette palette) {
    super(context);
    this.productId = productId;
    path = Storefront.photoPath(productId);
    setBackgroundColor(palette.surface2);
    image = new ImageView(context);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setContentDescription(name);
    addView(image, new LayoutParams(-1, -1));
    note = Ui.text(context, path == null ? "Photo unavailable" : "Loading photo…", 12, palette.muted, false);
    note.setGravity(Gravity.CENTER);
    int padding = Ui.dp(context, 8);
    note.setPadding(padding, padding, padding, padding);
    addView(note, new LayoutParams(-1, -1));
    if (path != null) load();
  }

  private void load() {
    if (loading) return;
    Bitmap hit = MEMORY.get(path);
    if (hit != null) { show(hit); return; }
    loading = true;
    note.setText("Loading photo…");
    note.setOnClickListener(null);
    File cache = new File(getContext().getCacheDir(), "product-" + productId + "-v1.png");
    IO.execute(() -> {
      Bitmap result = null;
      try {
        if (cache.isFile()) {
          try (InputStream in = new FileInputStream(cache)) { result = decode(read(in)); }
          catch (Exception corrupt) { android.util.Log.w("ProductPhoto", "Cached photo could not be read", corrupt); }
        }
        if (result == null) {
          HttpURLConnection connection = (HttpURLConnection) new URL(PlayerAccount.ORIGIN + path).openConnection();
          connection.setConnectTimeout(10000);
          connection.setReadTimeout(15000);
          connection.setInstanceFollowRedirects(false);
          try {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Photo unavailable");
            byte[] bytes;
            try (InputStream in = connection.getInputStream()) { bytes = read(in); }
            result = decode(bytes);
            if (result == null) throw new java.io.IOException("Invalid product photo");
            File temp = File.createTempFile("product-photo-", ".tmp", cache.getParentFile());
            try {
              try (FileOutputStream out = new FileOutputStream(temp)) { out.write(bytes); }
              if (!temp.renameTo(cache)) android.util.Log.w("ProductPhoto", "Could not cache photo");
            } finally {
              if (temp.exists() && !temp.delete()) android.util.Log.w("ProductPhoto", "Could not clear temporary photo");
            }
          } finally { connection.disconnect(); }
        }
      } catch (Exception error) { android.util.Log.w("ProductPhoto", "Product photo unavailable", error); }
      Bitmap done = result;
      if (done != null) MEMORY.put(path, done);
      post(() -> {
        loading = false;
        if (done != null) show(done);
        else {
          note.setText("Photo unavailable · Tap to retry");
          note.setOnClickListener(v -> load());
        }
      });
    });
  }

  private void show(Bitmap bitmap) { image.setImageBitmap(bitmap); note.setVisibility(GONE); }

  private static byte[] read(InputStream in) throws java.io.IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = in.read(buffer)) != -1) {
      if (out.size() + count > MAX_BYTES) throw new java.io.IOException("Photo too large");
      out.write(buffer, 0, count);
    }
    return out.toByteArray();
  }

  private static Bitmap decode(byte[] bytes) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    options.inSampleSize = 1;
    while (options.outWidth / options.inSampleSize > 1000 || options.outHeight / options.inSampleSize > 1400)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
  }
}
