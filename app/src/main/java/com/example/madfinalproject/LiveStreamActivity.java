package com.example.madfinalproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.pedro.common.ConnectChecker;
import com.pedro.common.StreamingStatsReport;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.library.rtmp.RtmpCamera2;
import com.pedro.library.view.OpenGlView;
import java.util.ArrayList;
import java.util.Locale;

/** Foreground-only RTMP publisher. Endpoints and stream keys live only in this activity. */
public final class LiveStreamActivity extends AppCompatActivity implements SurfaceHolder.Callback {
  private static final int PERMISSIONS = 64;
  private static final long CONNECT_TIMEOUT_MS = 25_000L;
  private static final int IDLE = 0, CONNECTING = 1, ACCEPTED = 2;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private RtmpCamera2 camera;
  private OpenGlView preview;
  private EditText endpoint;
  private CheckBox microphone;
  private TextView status, statistics;
  private Button start, stop, showPreview, switchCamera, mute;
  private boolean visible, surfaceReady, pendingStart, pendingPreview, streamHasAudio, muted;
  private boolean permissionRequestInFlight;
  private int state, generation;
  private long acceptedAt;
  private final Runnable connectionTimeout = () -> {
    if (state == CONNECTING) fail("Connection timed out. Check the ingest address, stream key and network.");
  };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(16), dp(12), dp(16), dp(12));
    root.setBackgroundColor(0xfff3f6ef);
    setContentView(root);
    UiInsets.apply(root);
    Button back = action("Back", this::finish);
    root.addView(back);
    root.addView(label("Live broadcast", 26, true));
    root.addView(label("Send your camera to your streaming service's RTMP or RTMPS ingest address.", 14, false));
    preview = new OpenGlView(this);
    preview.setContentDescription("Live camera preview");
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    preview.getHolder().addCallback(this);

    ScrollView controls = new ScrollView(this);
    controls.setFillViewport(false);
    root.addView(controls, new LinearLayout.LayoutParams(-1, dp(322)));
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    controls.addView(body);
    body.addView(label("Full ingest URL including stream key", 14, true));
    endpoint = new EditText(this);
    endpoint.setHint("Paste your rtmps:// ingest URL and key");
    endpoint.setSingleLine(true);
    endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    endpoint.setSaveEnabled(false);
    endpoint.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    endpoint.setContentDescription("Full ingest URL including private stream key");
    body.addView(endpoint);
    microphone = new CheckBox(this);
    microphone.setText("Include microphone audio");
    microphone.setChecked(false);
    body.addView(microphone);
    status = label("Ready. Start a preview or paste your ingest address to broadcast.", 14, true);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    body.addView(status);
    statistics = label("", 13, false);
    body.addView(statistics);
    LinearLayout topActions = new LinearLayout(this);
    showPreview = action("Preview", () -> requestCapture(false));
    start = action("Start broadcast", () -> requestCapture(true));
    topActions.addView(showPreview, new LinearLayout.LayoutParams(0, -2, 1));
    topActions.addView(start, new LinearLayout.LayoutParams(0, -2, 1));
    body.addView(topActions);
    LinearLayout otherActions = new LinearLayout(this);
    stop = action("Stop", () -> {
      releaseCapture();
      status.setText("Broadcast stopped. Camera and microphone released.");
    });
    switchCamera = action("Switch camera", this::switchCamera);
    mute = action("Mute", this::toggleMute);
    otherActions.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
    otherActions.addView(switchCamera, new LinearLayout.LayoutParams(0, -2, 1));
    otherActions.addView(mute, new LinearLayout.LayoutParams(0, -2, 1));
    body.addView(otherActions);
    body.addView(label("720p · H.264 · 30 fps. Your stream key stays in this screen. Broadcasting stops when you leave. Check your provider's dashboard and viewer page to confirm public playback.", 12, false));
    updateControls();
  }

  private void requestCapture(boolean broadcast) {
    if (!visible || state != IDLE) return;
    if (broadcast && !validEndpoint()) return;
    pendingStart = broadcast;
    pendingPreview = !broadcast;
    ArrayList<String> missing = new ArrayList<>();
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
      missing.add(Manifest.permission.CAMERA);
    if (broadcast && microphone.isChecked()
        && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
      missing.add(Manifest.permission.RECORD_AUDIO);
    if (!missing.isEmpty()) {
      permissionRequestInFlight = true;
      requestPermissions(missing.toArray(new String[0]), PERMISSIONS);
      return;
    }
    resumePendingCapture();
  }

  private boolean validEndpoint() {
    String value = endpoint.getText().toString().trim();
    try {
      Uri uri = Uri.parse(value);
      String scheme = uri.getScheme();
      String path = uri.getPath();
      if (value.length() > 4096 || value.matches("(?s).*\\s.*")
          || !("rtmp".equalsIgnoreCase(scheme) || "rtmps".equalsIgnoreCase(scheme))
          || uri.getHost() == null || uri.getHost().isEmpty()
          || uri.getFragment() != null || path == null || path.length() < 2
          || uri.getPort() > 65535 || uri.getPort() == 0) {
        endpoint.setError("Enter a full RTMP or RTMPS ingest URL with stream key.");
        return false;
      }
      endpoint.setError(null);
      return true;
    } catch (RuntimeException invalid) {
      endpoint.setError("The ingest address is invalid.");
      return false;
    }
  }

  private void resumePendingCapture() {
    if (!visible || (!pendingStart && !pendingPreview)) return;
    if (!surfaceReady) {
      status.setText("Waiting for camera preview surface…");
      return;
    }
    boolean broadcast = pendingStart;
    pendingStart = pendingPreview = false;
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      status.setText("Allow camera access to preview or broadcast.");
      return;
    }
    if (broadcast && microphone.isChecked()
        && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      status.setText("Microphone access was denied. Turn off microphone audio to broadcast video only.");
      return;
    }
    try {
      if (camera == null) {
        int token = ++generation;
        camera = new RtmpCamera2(preview, new ConnectionEvents(token));
        camera.getStreamClient().setLogs(false);
        camera.getStreamClient().setTlsHostVerification(true);
        camera.getStreamClient().setSocketTimeout(10_000L);
        camera.getStreamClient().shouldFailOnRead(true);
        camera.startPreview(CameraHelper.Facing.BACK, 1280, 720);
      }
      if (!broadcast) {
        status.setText("Camera preview only. Nothing is being sent.");
        updateControls();
        return;
      }
      if (!validEndpoint()) return;
      int rotation = CameraHelper.getCameraOrientation(this);
      if (!camera.prepareVideo(1280, 720, 30, 2_000_000, rotation)) {
        fail("This device could not prepare 720p H.264 encoding. Close other camera apps and try again.");
        return;
      }
      streamHasAudio = microphone.isChecked();
      camera.getStreamClient().setOnlyVideo(!streamHasAudio);
      if (streamHasAudio && !camera.prepareAudio(64_000, 44_100, false)) {
        fail("Microphone encoding could not start. Turn off microphone audio and try video only.");
        return;
      }
      muted = false;
      state = CONNECTING;
      status.setText("Connecting to the streaming service…");
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      updateControls();
      handler.postDelayed(connectionTimeout, CONNECT_TIMEOUT_MS);
      camera.startStream(endpoint.getText().toString().trim());
    } catch (RuntimeException failure) {
      // SDK errors may contain the endpoint, so never log or show the raw exception.
      fail("Camera or connection could not start. Check permissions, close other camera apps and retry.");
    }
  }

  private void switchCamera() {
    if (camera == null || state == CONNECTING) return;
    try {
      camera.switchCamera();
    } catch (Exception unavailable) {
      // Some front cameras do not support the active stream resolution.
      fail("The other camera could not use this capture format. Capture stopped; preview can be restarted.");
    }
  }

  private void toggleMute() {
    if (camera == null || !streamHasAudio || state == IDLE) return;
    muted = !muted;
    if (muted) camera.disableAudio();
    else camera.enableAudio();
    mute.setText(muted ? "Unmute" : "Mute");
    status.setText(state == ACCEPTED
        ? (muted ? "Server accepted broadcast · microphone muted" : "Server accepted broadcast · microphone on")
        : (muted ? "Connecting · microphone muted" : "Connecting · microphone on"));
  }

  private void fail(String message) {
    releaseCapture();
    status.setText(message);
  }

  private void releaseCapture() {
    handler.removeCallbacks(connectionTimeout);
    pendingStart = pendingPreview = false;
    generation++;
    RtmpCamera2 old = camera;
    camera = null;
    state = IDLE;
    streamHasAudio = muted = false;
    acceptedAt = 0;
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (old != null) {
      try {
        // Also releases prepared encoders/microphone when preparation failed before startStream.
        old.stopStream();
      } catch (RuntimeException cleanupFailure) {
        // Continue releasing the camera even if transport shutdown failed.
      }
      try {
        old.stopPreview();
      } catch (RuntimeException cleanupFailure) {
        // Activity destruction still proceeds; never expose a secret-bearing SDK error.
      }
    }
    if (statistics != null) statistics.setText("");
    if (start != null) updateControls();
  }

  private void updateControls() {
    boolean idle = state == IDLE;
    endpoint.setEnabled(idle);
    microphone.setEnabled(idle);
    start.setEnabled(idle);
    showPreview.setEnabled(idle && camera == null);
    stop.setEnabled(camera != null);
    switchCamera.setEnabled(camera != null && state != CONNECTING);
    mute.setEnabled(!idle && streamHasAudio);
    mute.setText(muted ? "Unmute" : "Mute");
  }

  private final class ConnectionEvents implements ConnectChecker {
    private final int token;
    ConnectionEvents(int token) { this.token = token; }

    private void current(Runnable action) {
      runOnUiThread(() -> {
        if (visible && token == generation && camera != null && state != IDLE) action.run();
      });
    }

    @Override public void onConnectionStarted(String ignoredUrl) {
      current(() -> status.setText("Connecting · waiting for the server to accept publishing…"));
    }

    @Override public void onConnectionSuccess() {
      current(() -> {
        handler.removeCallbacks(connectionTimeout);
        state = ACCEPTED;
        acceptedAt = android.os.SystemClock.elapsedRealtime();
        status.setText("Server accepted broadcast · check the provider's viewer page for playback");
        updateControls();
      });
    }

    @Override public void onConnectionFailed(String ignoredReason) {
      current(() -> fail("Broadcast connection failed. Check the ingest URL, stream key and network, then retry."));
    }

    @Override public void onDisconnect() {
      current(() -> fail("Streaming service disconnected. Camera and microphone stopped."));
    }

    @Override public void onAuthError() {
      current(() -> fail("The streaming service rejected authentication. Check the ingest URL and key."));
    }

    @Override public void onAuthSuccess() {
      current(() -> status.setText("Authenticated · waiting for publishing acceptance…"));
    }

    @Override public void onNewBitrate(long bitrate) {
      current(() -> {
        if (state != ACCEPTED) return;
        long seconds = Math.max(0L, (android.os.SystemClock.elapsedRealtime() - acceptedAt) / 1000L);
        statistics.setText(String.format(Locale.US, "Sending %.0f kbps · %02d:%02d elapsed%s",
            bitrate / 1000d, seconds / 60L, seconds % 60L, muted ? " · muted" : ""));
      });
    }

    @Override public void onStreamingStats(StreamingStatsReport report) {
      // No secret-bearing transport metadata is stored or logged.
    }
  }

  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(requestCode, permissions, grants);
    if (requestCode == PERMISSIONS) {
      permissionRequestInFlight = false;
      resumePendingCapture();
    }
  }

  @Override protected void onResume() {
    super.onResume();
    visible = true;
    resumePendingCapture();
  }

  @Override protected void onPause() {
    visible = false;
    boolean resumeStart = permissionRequestInFlight && pendingStart;
    boolean resumePreview = permissionRequestInFlight && pendingPreview;
    releaseCapture();
    // The platform permission dialog may pause the activity before delivering its result.
    pendingStart = resumeStart;
    pendingPreview = resumePreview;
    if (status != null) status.setText("Capture stopped when you left this screen. Start again to broadcast.");
    super.onPause();
  }

  @Override protected void onDestroy() {
    visible = false;
    releaseCapture();
    preview.getHolder().removeCallback(this);
    endpoint.setText("");
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  @Override public void surfaceCreated(SurfaceHolder holder) {
    surfaceReady = true;
    resumePendingCapture();
  }

  @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

  @Override public void surfaceDestroyed(SurfaceHolder holder) {
    surfaceReady = false;
    releaseCapture();
  }

  private TextView label(String text, int size, boolean bold) {
    TextView view = new TextView(this);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(0xff162d1e);
    view.setPadding(0, dp(4), 0, dp(4));
    if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return view;
  }

  private Button action(String title, Runnable listener) {
    Button button = new Button(this);
    button.setText(title);
    button.setTextColor(Color.WHITE);
    button.setBackgroundTintList(ColorStateList.valueOf(0xff1d633e));
    button.setOnClickListener(view -> listener.run());
    return button;
  }

  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
