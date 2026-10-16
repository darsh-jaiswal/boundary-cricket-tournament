package com.example.madfinalproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.*;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.ArrayDeque;

/** Bounded camera segment loop; camera runs only while this activity is visible. */
public class StumpCamActivity extends AppCompatActivity {
  private PreviewView preview;
  private TextView status;
  private VideoCapture<Recorder> capture;
  private Recording recording;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean running, reviewRequested, finalizing;
  private volatile boolean alive = true;
  private final ArrayDeque<File> segments = new ArrayDeque<>();
  private File current;
  private VideoView replay;
  private ReplayServer replayServer;
  private File lastReplay;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(1);
    box.setPadding(20, 40, 20, 20);
    setContentView(box);
    UiInsets.apply(box);
    try {
      replayServer = new ReplayServer(() -> runOnUiThread(this::requestReplay));
    } catch (Exception ignored) {
    }
    TextView link = new TextView(this);
    link.setText(replayServer == null ? "Replay sharing unavailable" : replayServer.address());
    link.setTextIsSelectable(true);
    box.addView(link);
    status = new TextView(this);
    status.setText("Square-leg stump cam · Place the camera level with the crease.");
    box.addView(status);
    preview = new PreviewView(this);
    box.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    replay = new VideoView(this);
    replay.setVisibility(android.view.View.GONE);
    box.addView(replay, new LinearLayout.LayoutParams(-1, 0, 1));
    Button start = new Button(this);
    start.setId(R.id.start_camera);
    start.setText("Start rolling camera");
    box.addView(start);
    start.setOnClickListener(
        v -> {
          if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 31);
          else startCamera();
        });
    Button check = new Button(this);
    check.setId(R.id.check_replay);
    check.setText("Check run-out / no-ball");
    box.addView(check);
    check.setOnClickListener(v -> requestReplay());
    Button resume = new Button(this);
    resume.setId(R.id.resume_camera);
    resume.setText("Resume camera");
    box.addView(resume);
    resume.setOnClickListener(
        v -> {
          if (running || recording != null || finalizing) return;
          replay.stopPlayback();
          replay.setVisibility(android.view.View.GONE);
          preview.setVisibility(android.view.View.VISIBLE);
          if (capture != null) {
            running = true;
            segment();
          }
        });
  }

  private void requestReplay() {
    android.util.Log.i(
        "BoundaryReplay",
        "Review requested; recording="
            + (recording != null)
            + ", finalizing="
            + finalizing
            + ", segments="
            + segments.size());
    handler.removeCallbacksAndMessages(null);
    reviewRequested = true;
    running = false;
    if (recording != null) {
      finalizing = true;
      recording.stop();
      recording = null;
    } else if (!finalizing && !segments.isEmpty()) {
      reviewRequested = false;
      review(segments.getLast());
    } else if (!finalizing) {
      reviewRequested = false;
      status.setText("Start recording before requesting a replay.");
    } else status.setText("Preparing latest replay…");
  }

  private void startCamera() {
    if (running || recording != null || finalizing) return;
    ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
    future.addListener(
        () -> {
          try {
            ProcessCameraProvider provider = future.get();
            provider.unbindAll();
            Preview p = new Preview.Builder().build();
            p.setSurfaceProvider(preview.getSurfaceProvider());
            Recorder recorder =
                new Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            java.util.Arrays.asList(
                                Quality.SD, Quality.HD, Quality.FHD, Quality.LOWEST)))
                    .build();
            capture = VideoCapture.withOutput(recorder);
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, capture);
            running = true;
            segment();
          } catch (Exception e) {
            capture = null;
            running = false;
            status.setText("Camera unavailable: " + e.getMessage());
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void segment() {
    if (!running || capture == null || recording != null || finalizing || !alive) return;
    current = new File(getCacheDir(), "stump-" + System.currentTimeMillis() + ".mp4");
    File destination = current;
    recording =
        capture
            .getOutput()
            .prepareRecording(this, new FileOutputOptions.Builder(destination).build())
            .start(
                ContextCompat.getMainExecutor(this),
                event -> {
                  if (event instanceof VideoRecordEvent.Start) {
                    status.setText("Recording rolling segments · latest 8–12 seconds retained");
                  }
                  if (event instanceof VideoRecordEvent.Status
                      && event.getRecordingStats().getRecordedDurationNanos() >= 4_000_000_000L
                      && recording != null
                      && running) {
                    finalizing = true;
                    recording.stop();
                    recording = null;
                  }
                  if (event instanceof VideoRecordEvent.Finalize) {
                    android.util.Log.i(
                        "BoundaryReplay",
                        "Segment finalized; reviewRequested="
                            + reviewRequested
                            + ", running="
                            + running);
                    finalizing = false;
                    recording = null;
                    if (!alive) {
                      destination.delete();
                      return;
                    }
                    VideoRecordEvent.Finalize end = (VideoRecordEvent.Finalize) event;
                    if (!end.hasError()) {
                      segments.add(destination);
                      while (segments.size() > 2) {
                        File old = segments.removeFirst();
                        old.delete();
                      }
                      if (reviewRequested) {
                        reviewRequested = false;
                        review(destination);
                      } else if (running) segment();
                    } else {
                      destination.delete();
                      if (reviewRequested && !segments.isEmpty()) {
                        reviewRequested = false;
                        running = false;
                        review(segments.getLast());
                      } else {
                        running = false;
                        status.setText("Recording stopped: camera error " + end.getError());
                      }
                    }
                  }
                });
  }

  private void review(File file) {
    java.util.ArrayList<File> captured = new java.util.ArrayList<>(segments);
    new Thread(
            () -> {
              try {
                File out = new File(getCacheDir(), "review-" + System.currentTimeMillis() + ".mp4");
                ReplayBuffer.export(captured, out);
                if (!alive) {
                  out.delete();
                  return;
                }
                File old = lastReplay;
                lastReplay = out;
                if (replayServer != null) replayServer.setClip(out);
                if (old != null) old.delete();
                runOnUiThread(() -> playReview(out));
              } catch (Exception e) {
                android.util.Log.e("BoundaryReplay", "Replay export failed", e);
                runOnUiThread(() -> status.setText("Could not prepare replay: " + e.getMessage()));
              }
            },
            "five-second-replay")
        .start();
  }

  private void playReview(File file) {
    if (!alive) return;
    preview.setVisibility(android.view.View.GONE);
    replay.setVisibility(android.view.View.VISIBLE);
    replay.setVideoURI(Uri.fromFile(file));
    replay.setOnPreparedListener(
        mp -> {
          mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(.25f));
          mp.setLooping(true);
          replay.start();
        });
    MediaController controller = new MediaController(this);
    replay.setMediaController(controller);
    status.setText(
        "Review at 0.25× speed. Official makes the decision. Latest five seconds with keyframe"
            + " padding. Share the pairing link with the umpire on the same Wi-Fi.");
  }

  @Override
  protected void onDestroy() {
    alive = false;
    if (replayServer != null) replayServer.close();
    for (File f : segments) f.delete();
    if (lastReplay != null) lastReplay.delete();
    super.onDestroy();
  }

  @Override
  protected void onStop() {
    running = false;
    handler.removeCallbacksAndMessages(null);
    if (recording != null) {
      recording.stop();
      recording = null;
    }
    replay.stopPlayback();
    super.onStop();
  }

  @Override
  public void onRequestPermissionsResult(int req, String[] p, int[] g) {
    super.onRequestPermissionsResult(req, p, g);
    if (req == 31 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startCamera();
  }
}
