package com.example.madfinalproject;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Browser phone registration and private registered-player lookup; there is no local PIN. */
public final class PlayerAccountActivity extends AppCompatActivity {
  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private LinearLayout body;
  private TextView status;
  private EditText phone;
  private boolean busy;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(1);
    int pad = dp(22); body.setPadding(pad, pad, pad, pad); body.setBackgroundColor(0xfff3f6ef); scroll.addView(body);
    setContentView(scroll); UiInsets.apply(scroll);
    action("Back", this::finish); label("Your player account", 28);
    label("Register with a verified phone number. Only registered players can be selected for new matches.", 15);
    status = label(PlayerAccount.linked(this) ? "Phone account linked to this device." : "Link your phone account to add registered players.", 15);
    action("Register / link phone account", this::begin);
    action("Check account link", this::check);
    action("Open player registration", () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PlayerAccount.ORIGIN + "/player.html"))));
    if (getIntent().getBooleanExtra("pick", false)) {
      label("Add by phone number", 22);
      label("Ask the player for their registered phone number, including country code. Indian ten-digit mobile numbers use +91.", 14);
      phone = new EditText(this); phone.setHint("+91 mobile number"); phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE); body.addView(phone);
      action("Find registered player", this::lookup);
    }
    action("Unlink this device", () -> call(() -> {
      PlayerAccount.request(this, new JSONObject().put("action", "revoke"));
      runOnUiThread(() -> { getSharedPreferences("boundary-account", 0).edit().clear().apply(); status.setText("Device unlinked."); });
    }));
  }
  private void begin() { call(() -> {
    JSONObject result = PlayerAccount.request(this, new JSONObject().put("action", "begin"));
    getSharedPreferences("boundary-account", 0).edit().putString("token", result.getString("token")).putBoolean("linked", false).apply();
    runOnUiThread(() -> {
      status.setText("Device request " + result.optString("id").substring(0, 8).toUpperCase(java.util.Locale.ROOT)
          + ". Register, then approve this request in the browser. Return and tap Check account link.");
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.optString("url"))));
    });
  }); }
  private void check() { call(() -> {
    JSONObject result = PlayerAccount.request(this, null);
    boolean linked = "linked".equals(result.optString("status"));
    getSharedPreferences("boundary-account", 0).edit().putBoolean("linked", linked).apply();
    runOnUiThread(() -> status.setText(linked ? "Linked as " + result.optString("name") + ". You can add registered players."
        : "Still waiting. Register your player account and tap Link my Boundary app in the browser."));
  }); }
  private void lookup() {
    final String number = phone.getText().toString();
    call(() -> {
      JSONObject result = PlayerAccount.request(this, new JSONObject().put("action", "lookup").put("phone", number));
      if (!result.optBoolean("verified")) throw new IllegalStateException("Player phone is not verified");
      runOnUiThread(() -> new android.app.AlertDialog.Builder(this).setTitle(result.optString("name"))
          .setMessage("Registered phone account. Add this player to the team?")
          .setNegativeButton("Cancel", null).setPositiveButton("Add player", (dialog, which) -> {
            setResult(RESULT_OK, new Intent().putExtra("playerId", result.optString("id")).putExtra("playerName", result.optString("name")));
            finish();
          }).show());
    });
  }
  private interface Job { void run() throws Exception; }
  private void call(Job job) {
    if (busy) return; busy = true; status.setText("Connecting…");
    network.execute(() -> {
      try { job.run(); } catch (Exception error) { runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) status.setText(error.getMessage() == null ? "Player service unavailable" : error.getMessage()); }); }
      finally { runOnUiThread(() -> busy = false); }
    });
  }
  private TextView label(String value, int size) {
    TextView label = new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(0xff142a24);
    label.setPadding(0, dp(12), 0, dp(8)); body.addView(label); return label;
  }
  private void action(String value, Runnable run) {
    Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setTextColor(0xffffffff);
    button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff147653)); button.setOnClickListener(v -> run.run()); body.addView(button);
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  @Override protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }
}
