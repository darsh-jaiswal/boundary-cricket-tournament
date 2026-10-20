package com.example.madfinalproject;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Career;
import com.example.madfinalproject.domain.Cricket;
import com.example.madfinalproject.domain.PlayerProfile;
import com.example.madfinalproject.domain.RegisteredPlayer;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The device owner's own profile: display details, photo and which saved squad entries are them.
 * Identity comes from Google-registered squad places for the linked account, plus places claimed here.
 */
public final class ProfileStore {
  private static final String PREFS = "boundary-profile";
  private final Context context;

  public ProfileStore(Context context) {
    this.context = context.getApplicationContext();
    if (prefs().contains("phone")) prefs().edit().remove("phone").apply();
  }

  // Legacy device profiles belong to the first linked account. Subsequent
  // accounts get separate preferences and photo files; switching users never
  // exposes the previous user's claims or personal details.
  private String profileKey() {
    String id = accountId();
    SharedPreferences legacy = context.getSharedPreferences(PREFS, 0);
    String owner = legacy.getString("accountOwner", "");
    if (owner.isEmpty() && !id.isEmpty() && PlayerAccount.linked(context)) {
      legacy.edit().putString("accountOwner", id).commit(); owner = id;
    }
    if (id.isEmpty()) return owner.isEmpty() ? PREFS : PREFS + "-guest";
    if (!id.matches("user_[A-Za-z0-9]+")) return PREFS + "-guest";
    return id.equals(owner) ? PREFS : PREFS + "-" + id;
  }

  private SharedPreferences prefs() { return context.getSharedPreferences(profileKey(), 0); }

  public String name() {
    if (hidden()) return "";
    String saved = prefs().getString("name", "");
    if (!saved.isEmpty()) return saved;
    return context.getSharedPreferences("boundary-account", 0).getString("name", "");
  }
  public String email() { return hidden() ? "" : prefs().getString("email", ""); }
  public String accountId() { return context.getSharedPreferences("boundary-account", 0).getString("id", ""); }

  public void saveDetails(String name, String email) {
    prefs().edit().putString("name", name.trim()).putString("email", email.trim()).apply();
  }

  public File photo() {
    String key = profileKey();
    return new File(new File(context.getFilesDir(), key.equals(PREFS) ? "profile" : "profile/" + key), "photo.jpg");
  }

  private boolean hidden() { return context.getSharedPreferences("boundary-session", 0).getBoolean("signedOut", false) && !PlayerAccount.linked(context); }
  public File visiblePhoto() { return hidden() ? null : photo(); }
  public boolean hasPhoto() { return !hidden() && photo().exists(); }

  /** Resolve the current account's storage before clearing it; keep legacy ownership stable. */
  public boolean clearCurrentProfile() {
    String key = profileKey();
    File currentPhoto = photo();
    SharedPreferences current = context.getSharedPreferences(key, 0);
    String legacyOwner = key.equals(PREFS) ? current.getString("accountOwner", "") : "";
    SharedPreferences.Editor clear = current.edit().clear();
    if (!legacyOwner.isEmpty()) clear.putString("accountOwner", legacyOwner);
    if (!clear.commit()) throw new IllegalStateException("Could not clear profile details");
    return !currentPhoto.exists() || currentPhoto.delete();
  }

  /** "team\0name" squad places claimed on this device. */
  public Set<String> claims() { return hidden() ? new LinkedHashSet<>() : new LinkedHashSet<>(prefs().getStringSet("claims", new HashSet<>())); }

  public void claim(String team, String name, boolean add) {
    Set<String> claims = claims();
    if (add) claims.add(Career.key(team, name)); else claims.remove(Career.key(team, name));
    prefs().edit().putStringSet("claims", claims).apply();
  }

  /** Everything this person plays as, across teams. */
  public Career.Who who(Repository repo) {
    Career.Who who = new Career.Who(name().isEmpty() ? "You" : name());
    String account = accountId();
    Map<String, String> teamNames = new HashMap<>();
    for (Cricket.Team t : repo.teams()) teamNames.put(t.id, t.name);
    if (!account.isEmpty())
      for (RegisteredPlayer p : repo.list("registered-player", RegisteredPlayer.class))
        if (account.equals(p.accountId) && teamNames.containsKey(p.teamId)) who.add(teamNames.get(p.teamId), p.name);
    for (String claim : claims()) {
      int split = claim.indexOf('\u0000');
      if (split > 0) who.add(claim.substring(0, split), claim.substring(split + 1));
    }
    return who;
  }

  public PlayerProfile attributes() {
    PlayerProfile p = new PlayerProfile();
    if (hidden()) return p;
    p.battingHand = prefs().getString("battingHand", "");
    p.battingOrder = prefs().getString("battingOrder", "");
    p.bowlingStyle = prefs().getString("bowlingStyle", "");
    p.role = prefs().getString("role", "");
    p.city = prefs().getString("city", "");
    return p;
  }

  public void saveAttributes(PlayerProfile p) {
    prefs().edit().putString("battingHand", p.battingHand).putString("battingOrder", p.battingOrder)
        .putString("bowlingStyle", p.bowlingStyle).putString("role", p.role).putString("city", p.city).apply();
  }

  /** Attributes of every saved player, keyed like Career.key, with the owner's own applied to their places. */
  public static Map<String, PlayerProfile> all(Repository repo, ProfileStore me) {
    Map<String, PlayerProfile> profiles = new HashMap<>();
    for (PlayerProfile p : repo.list("player-profile", PlayerProfile.class)) profiles.put(Career.key(p.team, p.name), p);
    if (me != null) {
      PlayerProfile mine = me.attributes();
      for (String k : me.who(repo).keys) profiles.put(k, mine);
    }
    return profiles;
  }

  public static void save(Repository repo, PlayerProfile p) {
    repo.save(PlayerProfile.key(p.team, p.name), "player-profile", p);
  }

  public static PlayerProfile find(Repository repo, String team, String name) {
    for (PlayerProfile p : repo.list("player-profile", PlayerProfile.class))
      if (p.team.equals(team) && p.name.equals(name)) return p;
    PlayerProfile p = new PlayerProfile();
    p.team = team;
    p.name = name;
    return p;
  }

  /** Squad places across saved teams, for claiming or picking a player to compare. */
  public static List<String[]> squadPlaces(Repository repo) {
    List<String[]> places = new java.util.ArrayList<>();
    for (Cricket.Team t : repo.teams()) for (String player : t.players) places.add(new String[] {t.name, player});
    return places;
  }
}
