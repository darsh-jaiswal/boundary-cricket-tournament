package com.example.madfinalproject;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Google account sessions are issued only after browser approval and server-side verification. */
public final class PlayerAccount {
  private PlayerAccount() {}
  public static final String ORIGIN = "https://boundary-cricket-tournament.vercel.app";
  public static boolean linked(Context context) {
    return context.getSharedPreferences("boundary-account", 0).getBoolean("linked", false);
  }
  interface Revoker { void revoke(String token) throws Exception; }

  /** End local access immediately; retain only a private revocation queue for offline retry. */
  public static synchronized void disconnectLocally(Context context) {
    new ProfileStore(context); // Bind a legacy local profile before its linked account is cleared.
    android.content.SharedPreferences account = context.getSharedPreferences("boundary-account", 0);
    String token = account.getString("token", "");
    android.content.SharedPreferences pending = context.getSharedPreferences("boundary-revocations", 0);
    java.util.Set<String> tokens = new java.util.HashSet<>(pending.getStringSet("tokens", java.util.Collections.emptySet()));
    if (!token.isEmpty()) {
      tokens.add(token);
      if (!pending.edit().putStringSet("tokens", tokens).commit()) throw new IllegalStateException("Could not save sign-out. Please retry.");
    }
    // The queue is never used for authentication. Save it before discarding the active credential.
    if (!account.edit().clear().commit()) throw new IllegalStateException("Could not clear this session. Please retry.");
    context.getSharedPreferences("boundary-session", 0).edit().putBoolean("signedOut", true)
        .putLong("revision", sessionRevision(context) + 1).commit();
  }

  public static long sessionRevision(Context context) {
    return context.getSharedPreferences("boundary-session", 0).getLong("revision", 0);
  }

  /** Call off the UI thread. A failed remote revoke never restores local access. */
  public static void signOutDevice(Context context) throws Exception {
    disconnectLocally(context);
    retryRevocations(context);
  }

  public static void retryRevocations(Context context) throws Exception {
    retryRevocations(context, token -> {
      try { requestWithToken(context, new JSONObject().put("action", "revoke"), token); }
      catch (PlayerHttpError error) { if (error.status != 401) throw error; }
    });
  }

  static void retryRevocations(Context context, Revoker revoker) throws Exception {
    android.content.SharedPreferences pending = context.getSharedPreferences("boundary-revocations", 0);
    java.util.Set<String> tokens;
    synchronized (PlayerAccount.class) {
      tokens = new java.util.HashSet<>(pending.getStringSet("tokens", java.util.Collections.emptySet()));
    }
    for (String token : tokens) {
      revoker.revoke(token);
      synchronized (PlayerAccount.class) {
        java.util.Set<String> remaining = new java.util.HashSet<>(pending.getStringSet("tokens", java.util.Collections.emptySet()));
        remaining.remove(token);
        if (!pending.edit().putStringSet("tokens", remaining).commit()) throw new IllegalStateException("Could not save revocation status");
      }
    }
  }

  public static JSONObject request(Context context, JSONObject body) throws Exception {
    String token = context.getSharedPreferences("boundary-account", 0).getString("token", "");
    return requestWithToken(context, body, token);
  }

  public static JSONObject teamRequest(Context context, JSONObject body) throws Exception {
    String token = context.getSharedPreferences("boundary-account", 0).getString("token", "");
    return requestWithToken(context, body, token, "/api/teams");
  }
  public static JSONObject shopRequest(Context context, JSONObject body) throws Exception {
    String token = context.getSharedPreferences("boundary-account", 0).getString("token", "");
    return requestWithToken(context, body, token, "/api/shop");
  }
  public static JSONObject shopOrder(Context context, String orderId) throws Exception {
    if (!orderId.matches("[a-fA-F0-9-]{36}")) throw new IllegalArgumentException("Invalid order");
    String token = context.getSharedPreferences("boundary-account", 0).getString("token", "");
    return requestWithToken(context, null, token, "/api/shop?order=" + orderId);
  }

  private static final class PlayerHttpError extends IllegalStateException {
    final int status;
    PlayerHttpError(int status, String message) { super(message); this.status = status; }
  }

  private static JSONObject requestWithToken(Context context, JSONObject body, String token) throws Exception {
    return requestWithToken(context, body, token, "/api/players");
  }

  private static JSONObject requestWithToken(Context context, JSONObject body, String token, String endpoint) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(ORIGIN + endpoint).openConnection();
    connection.setConnectTimeout(15000); connection.setReadTimeout(25000);
    if (!token.isEmpty()) connection.setRequestProperty("Authorization", "BoundaryDevice " + token);
    try {
      if (body != null) {
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
      }
      int status = connection.getResponseCode();
      InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      if (stream == null) throw new IllegalStateException("Player service returned " + status);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = stream) {
        byte[] buffer = new byte[4096]; int count;
        while ((count = in.read(buffer)) != -1) { bytes.write(buffer, 0, count); if (bytes.size() > 64000) throw new IllegalStateException("Player response too large"); }
      }
      JSONObject result = new JSONObject(bytes.toString("UTF-8"));
      if (status >= 400) {
        if (status == 401) synchronized (PlayerAccount.class) {
          android.content.SharedPreferences current = context.getSharedPreferences("boundary-account", 0);
          if (!token.isEmpty() && token.equals(current.getString("token", ""))) current.edit().clear().apply();
        }
        throw new PlayerHttpError(status, result.optString("error", "Player service is unavailable"));
      }
      return result;
    } finally { connection.disconnect(); }
  }
}
