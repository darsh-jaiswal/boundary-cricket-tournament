package com.example.madfinalproject;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Size;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Scan a Boundary profile QR code with the camera (or from a gallery image) to follow that player. */
public final class QrScanActivity extends AppCompatActivity {
  private static final String HINT = "Scan a Boundary profile QR code";
  private static final int WHITE = 0xffffffff, SCRIM = 0x8c000000;

  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private Ui.Palette p;
  private PreviewView preview;
  private ImageAnalysis analysis;
  private TextView hint, blockedText, allow;
  private LinearLayout blocked;
  private ActivityResultLauncher<String> permission;
  private ActivityResultLauncher<PickVisualMediaRequest> gallery;
  private boolean cameraStarted, sheetOpen;
  private volatile boolean paused;
  private byte[] frame;
  /** The code just shown in a sheet stays in view after closing it; don't reopen it straight away. */
  private String lastId;
  private long closedAt;
  private final Runnable restoreHint = () -> {
    hint.setText(HINT);
    hint.setBackground(Ui.round(SCRIM, Ui.dp(this, 20)));
  };

  @Override protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
    p = Ui.palette(this);
    permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
      if (Boolean.TRUE.equals(granted)) startCamera();
      else showBlocked("Allow camera access to scan profile codes, or pick a QR code image from your gallery.", true);
    });
    gallery = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> { if (uri != null) decodeImage(uri); });

    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    preview = new PreviewView(this);
    preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
    root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    root.addView(new Viewfinder(this), new FrameLayout.LayoutParams(-1, -1));
    FrameLayout controls = new FrameLayout(this);
    controls.setPadding(dp(16), dp(8), dp(16), dp(24));
    root.addView(controls, new FrameLayout.LayoutParams(-1, -1));
    setContentView(root);
    UiInsets.apply(controls);

    LinearLayout top = Ui.row(this);
    top.addView(Ui.iconButton(this, p, "back", 44, SCRIM, WHITE, this::finish));
    TextView title = Ui.text(this, "Scan QR code", 18, WHITE, true);
    title.setPadding(dp(14), 0, 0, 0);
    top.addView(title);
    controls.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

    blocked = Ui.column(this);
    blocked.setGravity(Gravity.CENTER_HORIZONTAL);
    blocked.setBackground(Ui.round(p.surface, dp(20)));
    blocked.setPadding(dp(20), dp(20), dp(20), dp(20));
    blocked.addView(new IconView(this, "camera", p.ink), new LinearLayout.LayoutParams(dp(40), dp(40)));
    blockedText = Ui.text(this, "", 15, p.ink, false);
    blockedText.setGravity(Gravity.CENTER);
    blockedText.setPadding(0, dp(12), 0, dp(16));
    blocked.addView(blockedText);
    allow = Ui.pill(this, p, "Allow camera", true, this::askAgain);
    blocked.addView(allow);
    blocked.setVisibility(View.GONE);
    controls.addView(blocked, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

    LinearLayout bottom = Ui.column(this);
    bottom.setGravity(Gravity.CENTER_HORIZONTAL);
    hint = Ui.text(this, HINT, 15, WHITE, true);
    hint.setGravity(Gravity.CENTER);
    hint.setPadding(dp(16), dp(10), dp(16), dp(10));
    restoreHint.run();
    bottom.addView(hint);
    bottom.addView(Ui.space(this, 1, 16));
    TextView pick = Ui.pill(this, p, "Pick from gallery", false, () -> gallery.launch(new PickVisualMediaRequest.Builder()
        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
    pick.setTextColor(WHITE);
    pick.setBackground(Ui.outline(SCRIM, 0x66ffffff, dp(1), dp(24)));
    bottom.addView(pick);
    controls.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

    if (hasCamera()) startCamera();
    else permission.launch(Manifest.permission.CAMERA);
  }

  @Override protected void onResume() {
    super.onResume();
    if (!cameraStarted && hasCamera()) startCamera(); // back from system settings with access granted
  }

  @Override protected void onDestroy() {
    if (analysis != null) analysis.clearAnalyzer();
    worker.shutdown();
    super.onDestroy();
  }

  private boolean hasCamera() {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
  }

  private void askAgain() {
    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) permission.launch(Manifest.permission.CAMERA);
    else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
  }

  private void showBlocked(String message, boolean canAsk) {
    blockedText.setText(message);
    allow.setVisibility(canAsk ? View.VISIBLE : View.GONE);
    blocked.setVisibility(View.VISIBLE);
  }

  private void startCamera() {
    if (cameraStarted) return;
    cameraStarted = true;
    blocked.setVisibility(View.GONE);
    ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
    future.addListener(() -> {
      if (isDestroyed()) return;
      try {
        ProcessCameraProvider provider = future.get();
        Preview shown = new Preview.Builder().build();
        shown.setSurfaceProvider(preview.getSurfaceProvider());
        analysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(new ResolutionSelector.Builder().setResolutionStrategy(new ResolutionStrategy(
                new Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
            .build();
        if (!sheetOpen) analysis.setAnalyzer(worker, this::analyze);
        provider.unbindAll();
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, shown, analysis);
      } catch (Exception e) {
        showBlocked("The camera is unavailable right now. You can still pick a QR code image from your gallery.", false);
      }
    }, ContextCompat.getMainExecutor(this));
  }

  /** Runs on the worker thread for the latest camera frame only. */
  private void analyze(ImageProxy image) {
    try {
      if (paused) return;
      ImageProxy.PlaneProxy y = image.getPlanes()[0];
      ByteBuffer buffer = y.getBuffer();
      int size = buffer.remaining();
      if (frame == null || frame.length < size) frame = new byte[size];
      buffer.get(frame, 0, size);
      String text = Decoder.luminance(frame, y.getRowStride(), image.getWidth(), image.getHeight());
      if (text != null) runOnUiThread(() -> found(text));
    } catch (RuntimeException ignored) {
      // A malformed frame is skipped; the next one is analysed.
    } finally {
      image.close();
    }
  }

  private void decodeImage(Uri uri) {
    hint.removeCallbacks(restoreHint);
    hint.setText("Reading image…");
    worker.execute(() -> {
      String text = null;
      try {
        Bitmap bitmap = load(uri, 2048);
        if (bitmap != null) {
          int w = bitmap.getWidth(), h = bitmap.getHeight();
          int[] pixels = new int[w * h];
          bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
          text = Decoder.pixels(pixels, w, h);
        }
      } catch (Exception | OutOfMemoryError ignored) {
        // Reported below as "no code found".
      }
      String result = text;
      runOnUiThread(() -> {
        if (isDestroyed()) return;
        if (result != null) { restoreHint.run(); found(result); }
        else notice("No QR code found in that image");
      });
    });
  }

  private Bitmap load(Uri uri, int maxSide) throws Exception {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
    int sample = 1;
    while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    try (InputStream in = getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, options); }
  }

  private void found(String text) {
    if (sheetOpen || isFinishing()) return;
    String id = Social.parseQr(text);
    if (id == null) { notice("That's not a Boundary profile code"); return; }
    if (id.equals(lastId) && SystemClock.elapsedRealtime() - closedAt < 2500) return;
    paused = true;
    if (analysis != null) analysis.clearAnalyzer();
    preview.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
    showSheet(id);
  }

  private void notice(String message) {
    hint.setText(message);
    hint.setBackground(Ui.round(0xd9c0392b, dp(20)));
    hint.removeCallbacks(restoreHint);
    hint.postDelayed(restoreHint, 2200);
  }

  private void resumeScanning() {
    sheetOpen = false;
    closedAt = SystemClock.elapsedRealtime();
    paused = false;
    if (analysis != null) analysis.setAnalyzer(worker, this::analyze);
  }

  private void showSheet(String id) {
    sheetOpen = true;
    lastId = id;
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    LinearLayout box = Ui.column(this);
    GradientDrawable bg = Ui.round(p.surface, 0);
    float r = dp(24);
    bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    box.setBackground(bg);
    box.setPadding(dp(20), dp(10), dp(20), dp(24));
    View grip = new View(this);
    grip.setBackground(Ui.round(p.line, dp(2)));
    LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(36), dp(4));
    gl.gravity = Gravity.CENTER_HORIZONTAL;
    gl.bottomMargin = dp(18);
    box.addView(grip, gl);

    LinearLayout head = Ui.row(this);
    TextView avatar = (TextView) Ui.avatar(this, p, null, "", 60);
    head.addView(avatar);
    LinearLayout who = Ui.column(this);
    who.setPadding(dp(14), 0, 0, 0);
    TextView name = Ui.text(this, "Boundary player", 20, p.ink, true);
    who.addView(name);
    TextView counts = Ui.text(this, "Looking up player…", 14, p.muted, false);
    counts.setPadding(0, dp(6), 0, 0);
    who.addView(counts);
    head.addView(who, Ui.weight(1));
    box.addView(head);

    TextView message = Ui.text(this, "", 14, p.muted, false);
    message.setPadding(0, dp(16), 0, 0);
    message.setVisibility(View.GONE);
    box.addView(message);

    LinearLayout actions = Ui.column(this);
    actions.setPadding(0, dp(20), 0, 0);
    box.addView(actions);
    TextView close = Ui.text(this, "Scan another code", 15, p.accent, true);
    close.setGravity(Gravity.CENTER);
    close.setPadding(0, dp(14), 0, dp(4));
    close.setOnClickListener(v -> sheet.dismiss());
    actions.addView(close, new LinearLayout.LayoutParams(-1, -2));

    sheet.setContentView(box);
    sheet.setOnShowListener(d -> {
      View container = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
      if (container != null) container.setBackgroundColor(Color.TRANSPARENT);
    });
    sheet.setOnDismissListener(d -> resumeScanning());
    sheet.show();

    Social.profile(this, id, (profile, error) -> {
      if (!sheet.isShowing()) return;
      if (error != null) {
        counts.setVisibility(View.GONE);
        message.setText(error);
        message.setVisibility(View.VISIBLE);
        if (Social.LINK_FIRST.equals(error)) actions.addView(Ui.pill(this, p, "Link Google account", true, () -> {
          sheet.dismiss();
          startActivity(new Intent(this, PlayerAccountActivity.class));
        }), 0, new LinearLayout.LayoutParams(-1, -2));
        return;
      }
      String shownName = profile.name.isEmpty() ? "Boundary player" : profile.name;
      name.setText(shownName);
      avatar.setText(Ui.initials(shownName));
      int[] followers = {profile.followerCount};
      boolean[] following = {profile.following}, busy = {false};
      Runnable paintCounts = () -> counts.setText(countLine(followers[0], profile.followingCount, profile.followsYou));
      paintCounts.run();
      if (id.equals(new ProfileStore(this).accountId())) {
        message.setText("This is your profile code. Share it so other players can follow you.");
        message.setVisibility(View.VISIBLE);
        return;
      }
      TextView pill = Ui.pill(this, p, "", true, () -> { });
      Runnable paintPill = () -> {
        pill.setText(following[0] ? "Following" : "Follow");
        pill.setTextColor(following[0] ? p.ink : p.onAccent);
        pill.setBackground(following[0] ? Ui.outline(p.surface2, p.line, dp(1), dp(24)) : Ui.round(p.accent, dp(24)));
      };
      paintPill.run();
      pill.setOnClickListener(v -> {
        if (busy[0]) return;
        busy[0] = true;
        pill.setAlpha(0.6f);
        message.setVisibility(View.GONE);
        Social.follow(this, id, !following[0], (now, followError) -> {
          busy[0] = false;
          pill.setAlpha(1f);
          if (followError != null) {
            message.setText(followError);
            message.setVisibility(View.VISIBLE);
            return;
          }
          if (now != following[0]) followers[0] = Math.max(0, followers[0] + (now ? 1 : -1));
          following[0] = now;
          paintPill.run();
          paintCounts.run();
        });
      });
      actions.addView(pill, 0, new LinearLayout.LayoutParams(-1, -2));
    });
  }

  static String countLine(int followers, int following, boolean followsYou) {
    return followers + (followers == 1 ? " follower" : " followers") + " · " + following + " following"
        + (followsYou ? " · Follows you" : "");
  }

  private int dp(float v) { return Ui.dp(this, v); }

  /** Dims the camera outside a rounded square and draws its corner brackets. */
  private static final class Viewfinder extends View {
    private final Paint scrim = new Paint(), corners = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shade = new Path(), brackets = new Path();
    private final RectF box = new RectF();

    Viewfinder(Context c) {
      super(c);
      scrim.setColor(0x99000000);
      corners.setColor(WHITE);
      corners.setStyle(Paint.Style.STROKE);
      corners.setStrokeWidth(Ui.dp(c, 4));
      corners.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      float side = Math.min(w, h) * 0.68f, cx = w / 2f, cy = h * 0.45f, r = Ui.dp(getContext(), 28);
      box.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
      shade.reset();
      shade.setFillType(Path.FillType.EVEN_ODD);
      shade.addRect(0, 0, w, h, Path.Direction.CW);
      shade.addRoundRect(box, r, r, Path.Direction.CW);
      float arm = side * 0.16f + r;
      brackets.reset();
      corner(box.left, box.top, 1, 1, r, arm);
      corner(box.right, box.top, -1, 1, r, arm);
      corner(box.right, box.bottom, -1, -1, r, arm);
      corner(box.left, box.bottom, 1, -1, r, arm);
    }

    /** One bracket around the corner at (x, y); dx and dy point into the box. */
    private void corner(float x, float y, int dx, int dy, float r, float arm) {
      brackets.moveTo(x, y + dy * arm);
      brackets.lineTo(x, y + dy * r);
      brackets.quadTo(x, y, x + dx * r, y);
      brackets.lineTo(x + dx * arm, y);
    }

    @Override protected void onDraw(Canvas canvas) {
      canvas.drawPath(shade, scrim);
      canvas.drawPath(brackets, corners);
    }
  }

  /** Finds a QR code's text in camera or gallery pixels. Plain zxing, so it runs in JVM unit tests. */
  static final class Decoder {
    private static final Map<DecodeHintType, Object> HINTS = new EnumMap<>(DecodeHintType.class);
    static { HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE); }
    private Decoder() {}

    /** A camera frame's Y (luminance) plane, whose rows may be padded out to rowStride bytes. */
    static String luminance(byte[] y, int rowStride, int width, int height) {
      return decode(new PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false));
    }

    static String pixels(int[] argb, int width, int height) {
      return decode(new RGBLuminanceSource(width, height, argb));
    }

    /** Light-on-dark codes, such as a profile shown in dark mode, only read once inverted. */
    private static String decode(LuminanceSource source) {
      QRCodeReader reader = new QRCodeReader();
      for (LuminanceSource s : new LuminanceSource[] {source, source.invert()}) {
        try {
          return reader.decode(new BinaryBitmap(new HybridBinarizer(s)), HINTS).getText();
        } catch (ReaderException ignored) {
          // Not found in this polarity.
        } finally {
          reader.reset();
        }
      }
      return null;
    }
  }
}
