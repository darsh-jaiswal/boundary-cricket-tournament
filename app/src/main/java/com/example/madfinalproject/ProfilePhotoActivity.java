package com.example.madfinalproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** "Add your profile photo": pick from the gallery or take a selfie; stored privately on this phone. */
public final class ProfilePhotoActivity extends AppCompatActivity {
  private Ui.Palette p;
  private ProfileStore store;
  private ImageView preview;
  private File pending;
  private ActivityResultLauncher<PickVisualMediaRequest> gallery;
  private ActivityResultLauncher<Uri> camera;
  private ActivityResultLauncher<String> cameraPermission;

  @Override protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    p = Ui.palette(this);
    store = new ProfileStore(this);
    gallery = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> { if (uri != null) save(uri); });
    camera = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
      if (Boolean.TRUE.equals(ok) && pending != null) save(Uri.fromFile(pending));
    });
    cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
      if (Boolean.TRUE.equals(granted)) selfie();
      else Toast.makeText(this, "Camera permission is needed to take a selfie", Toast.LENGTH_SHORT).show();
    });

    LinearLayout root = Ui.column(this);
    root.setBackgroundColor(p.bg);
    root.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 24));
    setContentView(root);
    UiInsets.apply(root);
    root.addView(Ui.iconButton(this, p, "back", 44, p.surface2, p.ink, this::finish));
    TextView title = Ui.text(this, "Add your profile photo", 26, p.ink, true);
    title.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 6));
    root.addView(title);
    root.addView(Ui.text(this, "Give us your best cricket photo.", 15, p.muted, false));

    FrameLayout frame = new FrameLayout(this);
    frame.setBackground(Ui.round(p.surface2, Ui.dp(this, 22)));
    frame.setClipToOutline(true);
    preview = new ImageView(this);
    preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, 0, 1);
    fl.setMargins(0, Ui.dp(this, 22), 0, Ui.dp(this, 22));
    root.addView(frame, fl);
    showCurrent(frame);

    root.addView(Ui.pill(this, p, "Upload from gallery", true, () -> gallery.launch(new PickVisualMediaRequest.Builder()
        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build())), new LinearLayout.LayoutParams(-1, -2));
    TextView selfie = Ui.text(this, "Take a selfie", 16, p.accent, true);
    selfie.setGravity(Gravity.CENTER);
    selfie.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
    selfie.setOnClickListener(v -> {
      if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) selfie();
      else cameraPermission.launch(Manifest.permission.CAMERA);
    });
    root.addView(selfie, new LinearLayout.LayoutParams(-1, -2));
  }

  private void showCurrent(FrameLayout frame) {
    if (store.hasPhoto()) {
      preview.setImageBitmap(Ui.decode(store.photo(), Ui.dp(this, 360)));
      return;
    }
    IconView placeholder = new IconView(this, "image", p.muted);
    frame.addView(placeholder, new FrameLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72), Gravity.CENTER));
  }

  private void selfie() {
    try {
      File dir = new File(getCacheDir(), "camera");
      if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Could not prepare the camera file");
      pending = new File(dir, "selfie.jpg");
      camera.launch(FileProvider.getUriForFile(this, getPackageName() + ".files", pending));
    } catch (RuntimeException e) {
      Toast.makeText(this, "Camera is unavailable: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  /** Downscales to 1080 px on the long edge, applies EXIF rotation and replaces the stored photo. */
  private void save(Uri uri) {
    try {
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
      int sample = 1;
      while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1080) sample *= 2;
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = sample;
      Bitmap bitmap;
      try (InputStream in = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
      if (bitmap == null) throw new IllegalStateException("That file is not a readable image");
      int rotation = 0;
      try (InputStream in = getContentResolver().openInputStream(uri)) {
        int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        rotation = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
            : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
      } catch (Exception ignored) { }
      if (rotation != 0) {
        Matrix m = new Matrix();
        m.postRotate(rotation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
      }
      float scale = Math.min(1f, 1080f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
      if (scale < 1f) bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
      File target = store.photo();
      File dir = target.getParentFile();
      if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Could not save the photo");
      File temp = new File(dir, "photo.tmp");
      try (FileOutputStream out = new FileOutputStream(temp)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out); }
      if (!temp.renameTo(target)) throw new IllegalStateException("Could not save the photo");
      if (pending != null && pending.exists() && !pending.delete()) pending.deleteOnExit();
      preview.setImageBitmap(bitmap);
      Toast.makeText(this, "Profile photo saved", Toast.LENGTH_SHORT).show();
      setResult(RESULT_OK);
    } catch (Exception e) {
      Toast.makeText(this, "Could not use that photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }
}
