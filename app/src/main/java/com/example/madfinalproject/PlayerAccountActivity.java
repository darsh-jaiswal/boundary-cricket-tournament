package com.example.madfinalproject;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Browser Google registration and private registered-player lookup; there is no local PIN. */
public final class PlayerAccountActivity extends AppCompatActivity {
  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private LinearLayout body;
  private TextView status;
  private EditText identifier;
  private boolean busy;
  private Ui.Palette pal;
  private TextView signIn, refresh, unlink, find;
  private android.view.View googleSignIn;
  private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean resumed;
  private Runnable refreshed = () -> {};
  private final Runnable poll = new Runnable() {
    @Override public void run() {
      if (!resumed) return;
      if (!busy && !PlayerAccount.linked(PlayerAccountActivity.this)
          && getSharedPreferences("boundary-account", 0).getString("pendingId", "").isEmpty()
          && !getSharedPreferences("boundary-account", 0).getString("token", "").isEmpty()) check();
      main.postDelayed(this, 2000);
    }
  };

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    Ui.applyAppearance(this); pal = Ui.palette(this);
    ScrollView scroll = new ScrollView(this); body = Ui.column(this);
    int pad = dp(20); body.setPadding(pad, pad, pad, pad); scroll.setFillViewport(true);
    scroll.setBackgroundColor(pal.bg); scroll.addView(body);
    android.view.View refreshable = PullRefresh.wrap(this, scroll, done -> {
      if (busy || getSharedPreferences("boundary-account", 0).getString("token", "").isEmpty()) { updateAccountUi(); done.run(); return; }
      refreshed = done; check();
    });
    setContentView(refreshable); UiInsets.apply(refreshable);
    TextView back = Ui.pill(this, pal, "Back", false, this::finish); body.addView(back);
    label("Your player account", 26);
    label("Sign in with Google, choose your @username, then join your team.", 15);
    status = label("", 15); status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    googleSignIn = Ui.googleSignIn(this, this::begin); body.addView(googleSignIn);
    signIn = action("Manage username / Google account", this::begin);
    refresh = action("Refresh account status", this::check);
    if (getIntent().getBooleanExtra("pick", false)) {
      label("Add a player", 22);
      label("Find a registered player by @username. If they are new, invite them on WhatsApp first.", 14);
      identifier = new EditText(this); identifier.setHint("@username, email or player code");
      identifier.setSingleLine(true); identifier.setTextColor(pal.ink); identifier.setHintTextColor(pal.muted);
      identifier.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      identifier.setMinHeight(dp(52)); body.addView(identifier, new LinearLayout.LayoutParams(-1, -2));
      find = action("Find player", this::lookup);
      action("Invite player on WhatsApp", this::invite);
    }
    unlink = action("Unlink this device", () -> {
      PlayerAccount.disconnectLocally(this); updateAccountUi();
      network.execute(() -> { try { PlayerAccount.retryRevocations(getApplicationContext()); }
        catch (Exception offline) { android.util.Log.w("BoundaryLogout", "Remote revocation pending"); } });
    });
    updateAccountUi();
    if (getIntent().getData() != null) receiveSignIn(getIntent());
    else if (state == null && getIntent().getBooleanExtra("autoBegin", false)) begin();
  }
  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent); setIntent(intent); receiveSignIn(intent);
  }
  private void receiveSignIn(Intent intent) {
    Uri uri = intent.getData();
    if (uri == null || !"boundary".equals(uri.getScheme()) || !"signin".equals(uri.getHost())) return;
    String id = uri.getQueryParameter("id"), code = uri.getQueryParameter("code");
    android.content.SharedPreferences prefs = getSharedPreferences("boundary-account", 0);
    if (id == null || !id.equals(prefs.getString("pendingId", "")) || code == null || !code.matches("[a-f0-9]{64}")) {
      status.setText("This sign-in belongs to a different request. Start sign-in from this app."); return;
    }
    call(() -> {
      long revision = PlayerAccount.sessionRevision(this);
      JSONObject result = PlayerAccount.request(this, new JSONObject().put("action", "exchange").put("id", id).put("code", code));
      synchronized (PlayerAccount.class) {
        if (revision != PlayerAccount.sessionRevision(this)) return;
        prefs.edit().putBoolean("linked", true).putString("id", result.getString("id"))
            .putString("name", result.getString("name")).putString("username", result.optString("username"))
            .remove("pendingId").remove("linkUrl").apply();
        getSharedPreferences("boundary-session", 0).edit().putBoolean("signedOut", false).putBoolean("guest", false).apply();
      }
      runOnUiThread(() -> {
        if (isFinishing() || isDestroyed()) return;
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
      });
    });
  }
  private void updateAccountUi() {
    android.content.SharedPreferences prefs = getSharedPreferences("boundary-account", 0);
    boolean linked = PlayerAccount.linked(this), pending = !prefs.getString("token", "").isEmpty();
    String username = prefs.getString("username", "");
    status.setText(linked ? "Signed in as " + prefs.getString("name", "Player") + (username.isEmpty() ? "." : " · @" + username)
        : pending ? "Complete sign-in in the Clerk portal. Boundary opens when your profile is ready."
        : "Sign in with Google to play, manage teams and score matches.");
    signIn.setText(linked ? "Manage username / Google account" : pending ? "Continue Google registration" : "Continue with Google");
    signIn.setVisibility(linked ? android.view.View.VISIBLE : android.view.View.GONE);
    googleSignIn.setVisibility(linked ? android.view.View.GONE : android.view.View.VISIBLE);
    refresh.setVisibility(pending ? android.view.View.VISIBLE : android.view.View.GONE);
    unlink.setVisibility(pending ? android.view.View.VISIBLE : android.view.View.GONE);
    if (find != null) { find.setEnabled(linked); find.setAlpha(linked ? 1f : .5f); }
  }
  @Override protected void onResume() {
    super.onResume();
    resumed = true; main.post(poll);
    if (!busy && getSharedPreferences("boundary-account", 0).getString("pendingId", "").isEmpty()
        && !getSharedPreferences("boundary-account", 0).getString("token", "").isEmpty()) check();
  }
  @Override protected void onPause() { resumed = false; main.removeCallbacks(poll); super.onPause(); }
  private void invite() {
    String team = getIntent().getStringExtra("teamName");
    String url = PlayerAccount.ORIGIN + "/player.html" + (team == null ? "" : "?team=" + Uri.encode(team));
    String text = "Join " + (team == null ? "my cricket team" : team) + " on Boundary! Sign in with Google and choose your @username: "
        + url + "\nSend me your @username after registering so I can add you to the squad.";
    Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage("com.whatsapp");
    try { startActivity(share); }
    catch (android.content.ActivityNotFoundException missing) { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invite player")); }
  }
  private void begin() {
    android.content.SharedPreferences prefs = getSharedPreferences("boundary-account", 0);
    if (PlayerAccount.linked(this)) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PlayerAccount.ORIGIN + "/player.html"))); return; }
    String pendingUrl = prefs.getString("linkUrl", "");
    if (!prefs.getString("pendingId", "").isEmpty() && prefs.getLong("pendingExpires", 0) < System.currentTimeMillis()) {
      prefs.edit().clear().apply(); pendingUrl = "";
    }
    if (!pendingUrl.isEmpty()) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pendingUrl))); return; }
    call(() -> {
    long revision = PlayerAccount.sessionRevision(this);
    JSONObject result = PlayerAccount.request(this, new JSONObject().put("action", "begin").put("version", 2));
    synchronized (PlayerAccount.class) {
    if (revision != PlayerAccount.sessionRevision(this)) return;
    getSharedPreferences("boundary-account", 0).edit().putString("token", result.getString("token")).putString("pendingId", result.getString("id")).putLong("pendingExpires", System.currentTimeMillis() + 9 * 60_000L).putString("linkUrl", result.getString("url")).putBoolean("linked", false).apply();
    }
    runOnUiThread(() -> {
      if (isFinishing() || isDestroyed()) return;
      updateAccountUi();
      status.setText("Opening Clerk sign-in…");
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.optString("url"))));
    });
  }); }
  private void check() { call(() -> {
    long revision = PlayerAccount.sessionRevision(this);
    String token = getSharedPreferences("boundary-account", 0).getString("token", "");
    JSONObject result = PlayerAccount.request(this, null);
    boolean linked = "linked".equals(result.optString("status"));
    synchronized (PlayerAccount.class) {
    if (revision != PlayerAccount.sessionRevision(this) || !token.equals(getSharedPreferences("boundary-account", 0).getString("token", ""))) return;
    android.content.SharedPreferences.Editor account = getSharedPreferences("boundary-account", 0).edit().putBoolean("linked", linked);
    // Kept so the profile can find this person's Google-registered squad places across teams.
    if (linked) account.putString("id", result.optString("id")).putString("name", result.optString("name")).putString("username", result.optString("username", ""));
    account.apply();
    if (linked) getSharedPreferences("boundary-session", 0).edit().putBoolean("signedOut", false).putBoolean("guest", false).apply();
    }
    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) {
      updateAccountUi();
      if (linked && getIntent().getBooleanExtra("autoBegin", false)) finish();
    } });
  }); }
  private void lookup() {
    final String player = identifier.getText().toString().trim();
    call(() -> {
      JSONObject result = PlayerAccount.request(this, new JSONObject().put("action", "lookup").put("identifier", player));
      if (!result.optBoolean("verified")) throw new IllegalStateException("Player is not Google registered");
      runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return;
        new android.app.AlertDialog.Builder(this).setTitle(result.optString("name") + (result.isNull("username") ? "" : " · @" + result.optString("username", "")))
          .setMessage("Registered Google player. Add this player to the team?")
          .setNegativeButton("Cancel", null).setPositiveButton("Add player", (dialog, which) -> {
            setResult(RESULT_OK, new Intent().putExtra("playerId", result.optString("id")).putExtra("playerName", result.optString("name")));
            finish();
          }).show(); });
    });
  }
  private interface Job { void run() throws Exception; }
  private void call(Job job) {
    if (busy || isFinishing() || isDestroyed()) return; busy = true; status.setText("Connecting…");
    network.execute(() -> {
      try { job.run(); } catch (Exception error) { runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) {
        updateAccountUi(); status.setText(error.getMessage() == null ? "Player service unavailable" : error.getMessage());
      } }); }
      finally { runOnUiThread(() -> { busy = false; Runnable done = refreshed; refreshed = () -> {}; done.run(); }); }
    });
  }
  private TextView label(String value, int size) {
    TextView label = Ui.text(this, value, size, pal.ink, size >= 22);
    label.setPadding(0, dp(12), 0, dp(8)); body.addView(label); return label;
  }
  private TextView action(String value, Runnable run) {
    TextView button = Ui.pill(this, pal, value, true, run); button.setMinHeight(dp(48));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10); body.addView(button, lp);
    return button;
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  @Override protected void onDestroy() { resumed = false; main.removeCallbacksAndMessages(null); network.shutdownNow(); super.onDestroy(); }
}
