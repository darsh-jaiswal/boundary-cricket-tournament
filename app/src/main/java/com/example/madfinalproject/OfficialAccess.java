package com.example.madfinalproject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.Toast;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Device-local official access, separate from public spectator access. */
public final class OfficialAccess {
  private static long unlockedUntil;

  private OfficialAccess() {}

  public static boolean require(Context context) {
    if (SystemClock.elapsedRealtime() < unlockedUntil) return true;
    SharedPreferences prefs = context.getSharedPreferences("official-access", 0);
    boolean setup = !prefs.contains("hash");
    if (prefs.getLong("blockedUntil", 0) > System.currentTimeMillis()) {
      Toast.makeText(context, "Too many attempts. Try again in one minute.", Toast.LENGTH_LONG)
          .show();
      return false;
    }
    EditText entry = new EditText(context);
    entry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    entry.setHint("PIN");
    AlertDialog dialog =
        new AlertDialog.Builder(context)
            .setTitle(setup ? "Set official PIN" : "Unlock official controls")
            .setMessage(
                setup
                    ? "Create a 6–12 digit PIN for this scoring device. Spectators can browse"
                        + " without it."
                    : "Enter the scoring device PIN, then repeat your action.")
            .setView(entry)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(setup ? "Set PIN" : "Unlock", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    vv -> {
                      try {
                        if (prefs.getLong("blockedUntil", 0) > System.currentTimeMillis())
                          throw new IllegalArgumentException("Too many attempts. Wait one minute.");
                        String pin = entry.getText().toString();
                        if (!pin.matches("[0-9]{6,12}"))
                          throw new IllegalArgumentException("Use 6–12 digits.");
                        if (setup) {
                          byte[] salt = new byte[24];
                          new SecureRandom().nextBytes(salt);
                          prefs
                              .edit()
                              .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                              .putString(
                                  "hash", Base64.encodeToString(derive(pin, salt), Base64.NO_WRAP))
                              .commit();
                        } else {
                          byte[] salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP),
                              stored = Base64.decode(prefs.getString("hash", ""), Base64.NO_WRAP);
                          if (!MessageDigest.isEqual(stored, derive(pin, salt))) {
                            int attempts = prefs.getInt("attempts", 0) + 1;
                            prefs
                                .edit()
                                .putInt("attempts", attempts)
                                .putLong(
                                    "blockedUntil",
                                    attempts >= 5 ? System.currentTimeMillis() + 60000 : 0)
                                .apply();
                            throw new IllegalArgumentException("Incorrect PIN.");
                          }
                        }
                        prefs.edit().putInt("attempts", 0).putLong("blockedUntil", 0).apply();
                        unlockedUntil = SystemClock.elapsedRealtime() + 30 * 60 * 1000;
                        dialog.dismiss();
                        Toast.makeText(
                                context,
                                "Controls unlocked for 30 minutes. Repeat your action.",
                                Toast.LENGTH_LONG)
                            .show();
                      } catch (Exception e) {
                        entry.setError(e.getMessage());
                      }
                    }));
    dialog.show();
    return false;
  }

  private static byte[] derive(String pin, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
    } finally {
      spec.clearPassword();
    }
  }

  public static void lock() {
    unlockedUntil = 0;
  }
}
