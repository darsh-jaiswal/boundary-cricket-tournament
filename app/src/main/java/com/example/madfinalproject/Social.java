package com.example.madfinalproject;

import android.app.Activity;
import android.content.Context;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Follow graph client. Profile QR codes carry the player's account id in a website link, so a
 * scan from any camera app also lands on the public player page.
 */
public final class Social {
  private Social() {}

  public static final class Person {
    public final String id, name;
    public Person(String id, String name) { this.id = id; this.name = name; }
  }

  public static final class Connections {
    public final List<Person> followers = new ArrayList<>(), following = new ArrayList<>();
  }

  /** A player as seen by the signed-in account, shown before following them. */
  public static final class Profile {
    public final String id, name;
    public final int followerCount, followingCount;
    /** Whether the signed-in account follows this player, and whether this player follows it. */
    public final boolean following, followsYou;
    public Profile(String id, String name, int followerCount, int followingCount, boolean following, boolean followsYou) {
      this.id = id; this.name = name; this.followerCount = followerCount; this.followingCount = followingCount;
      this.following = following; this.followsYou = followsYou;
    }
  }

  /** Called on the UI thread, unless the activity has gone; exactly one of result and error is set. */
  public interface Callback<T> { void done(T result, String error); }

  public static String qrPayload(String accountId) {
    try {
      return PlayerAccount.ORIGIN + "/player.html?id=" + URLEncoder.encode(accountId, "UTF-8");
    } catch (UnsupportedEncodingException e) { throw new AssertionError(e); }
  }

  /** The account id from a scanned Boundary profile link, or null when the code is something else. */
  public static String parseQr(String text) {
    if (text == null) return null;
    String link = text.trim();
    if (!link.startsWith(PlayerAccount.ORIGIN)) return null;
    String rest = link.substring(PlayerAccount.ORIGIN.length());
    if (!rest.isEmpty() && "/?#".indexOf(rest.charAt(0)) < 0) return null;
    int fragment = rest.indexOf('#');
    if (fragment >= 0) rest = rest.substring(0, fragment);
    int query = rest.indexOf('?');
    if (query < 0) return null;
    for (String pair : rest.substring(query + 1).split("&", -1)) {
      int eq = pair.indexOf('=');
      if (!(eq < 0 ? pair : pair.substring(0, eq)).equals("id")) continue;
      String id;
      try { id = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), "UTF-8"); }
      catch (IllegalArgumentException | UnsupportedEncodingException e) { return null; }
      return validId(id) ? id : null;
    }
    return null;
  }

  static boolean validId(String id) { return id != null && id.matches("user_[A-Za-z0-9]+"); }

  public static void follow(Activity activity, String accountId, boolean follow, Callback<Boolean> callback) {
    if (!validId(accountId)) { activity.runOnUiThread(() -> callback.done(null, NOT_A_PLAYER)); return; }
    call(activity, body(follow ? "follow" : "unfollow", accountId),
        r -> r.optBoolean("following", follow), callback);
  }

  public static void connections(Activity activity, Callback<Connections> callback) {
    call(activity, body("connections", null), r -> {
      Connections c = new Connections();
      people(r.optJSONArray("followers"), c.followers);
      people(r.optJSONArray("following"), c.following);
      return c;
    }, callback);
  }

  /** Name, counts and follow state of one player, for showing a scanned code before following. */
  public static void profile(Activity activity, String accountId, Callback<Profile> callback) {
    if (!validId(accountId)) { activity.runOnUiThread(() -> callback.done(null, NOT_A_PLAYER)); return; }
    call(activity, body("profile", accountId), r -> new Profile(r.getString("id"), r.optString("name", ""),
        r.optInt("followerCount"), r.optInt("followingCount"), r.optBoolean("following"), r.optBoolean("followsYou")), callback);
  }

  static final String NOT_DEPLOYED = "Connections need the latest Boundary server update.";
  static final String LINK_FIRST = "Link your phone account to follow players.";
  static final String NOT_A_PLAYER = "That is not a Boundary player code";
  static final String UNAVAILABLE = "Player service is unavailable. Please retry.";

  /**
   * A message fit to show the player. A server without these actions answers "Unknown action" or,
   * because unknown device actions fall through to its browser-session check, "Sign in to continue".
   */
  static String friendly(Exception e) {
    if (e instanceof IOException) return "Check your internet connection and try again.";
    String message = e instanceof IllegalStateException ? e.getMessage() : null;
    if (message == null || message.isEmpty()) return UNAVAILABLE;
    if (message.equals("Unknown action") || message.equals("Sign in to continue") || message.equals("Player service returned 404"))
      return NOT_DEPLOYED;
    return message;
  }

  private interface Parse<T> { T from(JSONObject response) throws Exception; }

  private static JSONObject body(String action, String id) {
    try { return new JSONObject().put("action", action).putOpt("id", id); }
    catch (org.json.JSONException e) { throw new IllegalStateException(e); }
  }

  private static <T> void call(Activity activity, JSONObject body, Parse<T> parse, Callback<T> callback) {
    Context context = activity.getApplicationContext();
    if (!PlayerAccount.linked(context)) { activity.runOnUiThread(() -> callback.done(null, LINK_FIRST)); return; }
    new Thread(() -> {
      T result = null;
      String error = null;
      try {
        result = parse.from(PlayerAccount.request(context, body));
      } catch (Exception e) {
        error = friendly(e);
        // PlayerAccount drops the link on any 401; an outdated server's 401 says nothing about this device.
        if (error.equals(NOT_DEPLOYED))
          context.getSharedPreferences("boundary-account", 0).edit().putBoolean("linked", true).apply();
      }
      T r = result;
      String err = error;
      activity.runOnUiThread(() -> { if (!activity.isDestroyed()) callback.done(r, err); });
    }, "boundary-social").start();
  }

  private static void people(JSONArray list, List<Person> into) {
    if (list == null) return;
    for (int i = 0; i < list.length(); i++) {
      JSONObject p = list.optJSONObject(i);
      if (p != null && validId(p.optString("id"))) into.add(new Person(p.optString("id"), p.optString("name", "")));
    }
  }
}
