package com.example.madfinalproject;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Phone account sessions are issued only after browser approval and server-side verification. */
public final class PlayerAccount {
  private PlayerAccount() {}
  public static final String ORIGIN = "https://boundary-cricket-tournament.vercel.app";
  public static boolean linked(Context context) {
    return context.getSharedPreferences("boundary-account", 0).getBoolean("linked", false);
  }
  public static JSONObject request(Context context, JSONObject body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(ORIGIN + "/api/players").openConnection();
    connection.setConnectTimeout(15000); connection.setReadTimeout(25000);
    String token = context.getSharedPreferences("boundary-account", 0).getString("token", "");
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
        if (status == 401) context.getSharedPreferences("boundary-account", 0).edit().putBoolean("linked", false).apply();
        throw new IllegalStateException(result.optString("error", "Player service is unavailable"));
      }
      return result;
    } finally { connection.disconnect(); }
  }
}
