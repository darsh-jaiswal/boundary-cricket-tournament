package com.example.madfinalproject;

import android.app.Activity;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

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

  /** Called on the UI thread; exactly one of result and error is set. */
  public interface Callback<T> { void done(T result, String error); }

  public static String qrPayload(String accountId) {
    return PlayerAccount.ORIGIN + "/player.html?id=" + Uri.encode(accountId);
  }

  /** The account id from a scanned Boundary profile link, or null when the code is something else. */
  public static String parseQr(String text) {
    if (text == null) return null;
    Uri uri = Uri.parse(text.trim());
    if (!PlayerAccount.ORIGIN.equals(uri.getScheme() + "://" + uri.getAuthority())) return null;
    String id = uri.getQueryParameter("id");
    return id != null && id.matches("user_[A-Za-z0-9]+") ? id : null;
  }

  public static void follow(Activity activity, String accountId, boolean follow, Callback<Boolean> callback) {
    activity.runOnUiThread(() -> callback.done(null, NOT_DEPLOYED));
  }

  public static void connections(Activity activity, Callback<Connections> callback) {
    activity.runOnUiThread(() -> callback.done(null, NOT_DEPLOYED));
  }

  static final String NOT_DEPLOYED = "Connections need the latest Boundary server update.";
}
