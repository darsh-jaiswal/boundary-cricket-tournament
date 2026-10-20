package com.example.madfinalproject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Foreground release checks with a dismissible prompt outside active scoring. */
public final class AppUpdates implements AutoCloseable {
  static final long INTERVAL_MS = 60_000;
  static final String PROMPT_PREFS = "boundary-update-prompts";
  private static final long REMIND_AFTER_MS = 24 * 60 * 60_000L;
  interface Source { UpdateRelease load() throws Exception; }
  private final Activity activity;
  private final Source source;
  private final long interval, installedCode;
  private final String installedVersion;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private TextView banner;
  private UpdateRelease available;
  private boolean started, closed, inFlight, manual, promptScheduled;
  private AlertDialog dialog;
  private final Runnable prompt = () -> { promptScheduled = false; maybePrompt(); };
  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (!started || closed) return;
      check(false);
      main.postDelayed(this, interval);
    }
  };

  public AppUpdates(Activity activity) { this(activity, AppUpdates::fetch, INTERVAL_MS); }

  AppUpdates(Activity activity, Source source, long interval) {
    this.activity = activity;
    this.source = source;
    this.interval = interval;
    try {
      PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
      installedCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
      installedVersion = info.versionName;
    } catch (android.content.pm.PackageManager.NameNotFoundException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public View createBanner() {
    Ui.Palette palette = Ui.palette(activity);
    banner = Ui.text(activity, "", 14, palette.onAccent, true);
    banner.setGravity(Gravity.CENTER);
    banner.setMinHeight(Ui.dp(activity, 48));
    banner.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10), Ui.dp(activity, 14), Ui.dp(activity, 10));
    banner.setBackgroundColor(palette.accent);
    banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    banner.setOnClickListener(v -> showAvailable());
    render();
    return banner;
  }

  public void start() {
    if (closed || started) return;
    started = true;
    main.post(tick);
  }

  public void stop() {
    started = false;
    manual = false;
    main.removeCallbacks(tick);
    main.removeCallbacks(prompt);
    promptScheduled = false;
    if (dialog != null) { dialog.dismiss(); dialog = null; }
  }

  public void checkNow() {
    if (!started || closed) return;
    Toast.makeText(activity, "Checking for updates…", Toast.LENGTH_SHORT).show();
    check(true);
  }

  public void refresh() { if (started && !closed) check(false); }

  private void check(boolean explicit) {
    manual |= explicit;
    if (inFlight || closed) return;
    inFlight = true;
    io.execute(() -> {
      UpdateRelease release = null;
      Exception failure = null;
      try { release = source.load(); }
      catch (Exception error) { failure = error; }
      UpdateRelease result = release;
      Exception error = failure;
      main.post(() -> {
        if (closed) return;
        inFlight = false;
        boolean notify = manual && started;
        manual = false;
        if (error == null && result != null) {
          available = result.newerThan(installedCode, installedVersion) ? result : null;
          render();
          if (notify) {
            if (available != null) showAvailable();
            else showMessage("You're up to date", "You're using the latest available Boundary version (" + installedVersion + ").");
          } else if (!promptScheduled) { promptScheduled = true; main.postDelayed(prompt, 500); }
        } else {
          android.util.Log.w("AppUpdates", "Release check unavailable", error);
          if (notify) showMessage("Couldn't check for updates", "Check your internet connection and try again from the profile menu.");
        }
      });
    });
  }

  private void render() {
    if (banner == null) return;
    banner.setVisibility(available == null ? View.GONE : View.VISIBLE);
    if (available != null) banner.setText("Update available · v" + available.version + " · Tap to update");
  }

  private void showAvailable() {
    if (!started || closed || available == null || activity.isFinishing() || activity.isDestroyed()) return;
    if (dialog != null && dialog.isShowing()) return;
    // Keep the confirmed release stable if another check completes while the dialog is open.
    UpdateRelease chosen = available;
    dialog = new AlertDialog.Builder(activity).setTitle("Update Boundary")
        .setMessage("A new version of Boundary is available.\n\nVersion " + chosen.version + " · " + chosen.sizeLabel()
            + "\nDownload the update, then confirm installation when Android asks. Your saved data stays on this device.")
        .setNegativeButton("Later", (d, which) -> deferPrompt(chosen))
        .setOnCancelListener(d -> deferPrompt(chosen))
        .setPositiveButton("Update now", (d, which) -> {
          deferPrompt(chosen);
          try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(chosen.source))); }
          catch (ActivityNotFoundException missing) {
            showMessage("Couldn't open the download", "Install a browser, then tap the update banner again.");
          }
        }).show();
  }

  private void maybePrompt() {
    if (!started || closed || available == null || !activity.hasWindowFocus()) return;
    if (activity instanceof MainActivity && !((MainActivity) activity).canShowUpdatePrompt()) return;
    if (activity.getSharedPreferences(PROMPT_PREFS, 0).getLong(promptKey(available), 0) > System.currentTimeMillis()) return;
    showAvailable();
  }

  private String promptKey(UpdateRelease release) { return release.versionCode + ":" + release.version; }

  private void deferPrompt(UpdateRelease release) {
    activity.getSharedPreferences(PROMPT_PREFS, 0).edit()
        .putLong(promptKey(release), System.currentTimeMillis() + REMIND_AFTER_MS).apply();
  }

  private void showMessage(String title, String text) {
    if (!started || closed || activity.isFinishing() || activity.isDestroyed()) return;
    if (dialog != null) dialog.dismiss();
    dialog = new AlertDialog.Builder(activity).setTitle(title).setMessage(text).setPositiveButton("OK", null).show();
  }

  @Override public void close() {
    stop();
    closed = true;
    banner = null;
    io.shutdownNow();
    main.removeCallbacksAndMessages(null);
  }

  private static UpdateRelease fetch() throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(PlayerAccount.ORIGIN + "/android-release.json").openConnection();
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(7000);
    connection.setUseCaches(false);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Cache-Control", "no-cache");
    connection.setRequestProperty("Accept", "application/json");
    try {
      if (connection.getResponseCode() != 200) throw new java.io.IOException("Release check unavailable");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
        byte[] buffer = new byte[2048];
        int count;
        while ((count = in.read(buffer)) != -1) {
          if (bytes.size() + count > 16000) throw new java.io.IOException("Release response too large");
          bytes.write(buffer, 0, count);
        }
      }
      JSONObject json = new JSONObject(bytes.toString("UTF-8"));
      return new UpdateRelease(json.getString("version"), json.has("versionCode") ? json.getLong("versionCode") : 0,
          json.getString("source"), json.getString("sha256"), json.getLong("bytes"));
    } finally { connection.disconnect(); }
  }
}
