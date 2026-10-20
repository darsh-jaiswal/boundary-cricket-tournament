package com.example.madfinalproject;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Cricket;
import com.example.madfinalproject.domain.Leaderboards;
import com.example.madfinalproject.domain.PlayerProfile;
import com.example.madfinalproject.domain.RegisteredPlayer;
import com.example.madfinalproject.domain.Cricket.*;
import java.util.*;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
  // Resolved from Ui.palette in onCreate, so the whole workspace follows Appearance (light, dark, device).
  private int INK = 0xff17251d,
      GREEN = 0xff147653,
      PAPER = 0xfff3f6ef,
      MUTED = 0xff66756b,
      LIME = 0xffd0ed8c,
      SURFACE = Color.WHITE,
      SURFACE2 = 0xffe9efe6,
      ON_ACCENT = Color.WHITE;
  private Ui.Palette pal;
  private ProfileStore profile;
  private AppUpdates updates;
  private StoreView storeView;
  private Repository repo;
  private Match match;
  private String page = "Home", role = "Organizer";
  private LinearLayout root, body;
  private ScrollView pageScroll;
  private String renderedPageKey;
  private TextToSpeech tts;
  private boolean audioReady, pa;
  private boolean logoutUiVisible;
  private boolean renderedLinked, renderedGuest;
  private static volatile boolean logoutWorkerRunning;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private AlertDialog pending;

  @Override
  public void onCreate(Bundle state) {
    Ui.applyAppearance(this);
    super.onCreate(state);
    pal = Ui.palette(this);
    INK = pal.ink; GREEN = pal.accent; PAPER = pal.bg; MUTED = pal.muted;
    SURFACE = pal.surface; SURFACE2 = pal.surface2; ON_ACCENT = pal.onAccent;
    profile = new ProfileStore(this);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    repo = new Repository(this);
    String id = getPreferences(0).getString("active", "");
    for (Match m : repo.matches()) if (m.id.equals(id)) match = m;
    role = getPreferences(0).getString("role", "Organizer");
    if (role.equals("Manager") || role.equals("Captain")) {
      role = "Organizer";
      getPreferences(0).edit().putString("role", role).apply();
    }
    tts = new TextToSpeech(this, status -> audioReady = status == TextToSpeech.SUCCESS);
    updates = new AppUpdates(this);
    draw();
  }

  @Override
  protected void onResume() {
    super.onResume();
    updates.start();
    boolean linked = PlayerAccount.linked(this), guest = getSharedPreferences("boundary-session", 0).getBoolean("guest", false);
    if (linked != renderedLinked || guest != renderedGuest) draw();
    logoutUiVisible = true;
    completePendingLogout();
    retryDeviceRevocations();
  }

  @Override
  protected void onPause() {
    updates.stop();
    logoutUiVisible = false;
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    updates.close();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    handler.removeCallbacksAndMessages(null);
    repo.close();
    super.onDestroy();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color, boolean bold) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    v.setLineSpacing(dp(2), 1);
    return v;
  }

  private GradientDrawable bg(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private Button button(String s, boolean primary, Runnable action) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(12);
    b.setTextColor(primary ? ON_ACCENT : INK);
    b.setBackground(bg(primary ? GREEN : SURFACE2, 14));
    b.setStateListAnimator(null);
    b.setMinHeight(dp(48));
    b.setOnClickListener(v -> guard(action));
    return b;
  }

  private void guard(Runnable r) {
    try {
      r.run();
    } catch (Exception ex) {
      message(
          "Could not complete action", ex.getMessage() == null ? ex.toString() : ex.getMessage());
    }
  }

  private void message(String title, String s) {
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(s)
            .setPositiveButton("OK", null)
            .show();
    TextView message = dialog.findViewById(android.R.id.message);
    if (message != null) message.setTextIsSelectable(true);
  }

  private void gap() {
    View v = new View(this);
    body.addView(v, new LinearLayout.LayoutParams(1, dp(12)));
  }

  private void heading(String eyebrow, String h, String detail) {
    body.addView(text(eyebrow.toUpperCase(Locale.ROOT), 10, GREEN, true));
    TextView title = text(h, 31, INK, true);
    title.setPadding(0, dp(5), 0, dp(4));
    body.addView(title);
    body.addView(text(detail, 13, MUTED, false));
    gap();
  }

  private LinearLayout card(String eyebrow, String title, String detail) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(1);
    c.setPadding(dp(17), dp(16), dp(17), dp(16));
    c.setBackground(bg(SURFACE, 18));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(12);
    body.addView(c, p);
    c.addView(text(eyebrow.toUpperCase(Locale.ROOT), 10, GREEN, true));
    TextView h = text(title, 18, INK, true);
    h.setPadding(0, dp(4), 0, dp(4));
    c.addView(h);
    if (!detail.isEmpty()) c.addView(text(detail, 13, MUTED, false));
    return c;
  }

  private void action(LinearLayout box, String label, Runnable r) {
    Button b = button(label, true, r);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(49));
    p.topMargin = dp(9);
    box.addView(b, p);
  }

  private void grid(String[] labels, Consumer<String> callback) {
    for (int i = 0; i < labels.length; i += 3) {
      LinearLayout row = new LinearLayout(this);
      for (int j = i; j < Math.min(labels.length, i + 3); j++) {
        String label = labels[j];
        Button b =
            button(label, label.equals("4") || label.equals("6"), () -> callback.accept(label));
        if (label.matches("[0-6]"))
          b.setOnLongClickListener(
              v -> {
                guard(() -> ballDialog(Integer.parseInt(label), Extra.NONE));
                return true;
              });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(b, p);
      }
      body.addView(row);
    }
  }

  private void draw() {
    String pageKey = page + ":" + role + ":" + (match == null ? "" : match.id);
    int previousY = pageScroll != null && pageKey.equals(renderedPageKey)
        ? pageScroll.getScrollY() : 0;
    renderedPageKey = pageKey;
    root = new LinearLayout(this);
    root.setOrientation(1);
    root.setBackgroundColor(PAPER);
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return insets;
        });
    // draw() swaps the content view on every page change; insets are not re-dispatched unless asked.
    ViewCompat.requestApplyInsets(root);
    renderedLinked = PlayerAccount.linked(this);
    renderedGuest = getSharedPreferences("boundary-session", 0).getBoolean("guest", false);
    if (!renderedLinked && !renderedGuest) {
      root.addView(updates.createBanner());
      welcome();
      return;
    }
    root.addView(topBar());
    root.addView(updates.createBanner());
    if (isMyCricket()) {
      HorizontalScrollView strip = new HorizontalScrollView(this);
      strip.setHorizontalScrollBarEnabled(false);
      LinearLayout tabs = new LinearLayout(this);
      String[] keys = {"Matches", "Cups", "Teams", "Stats", "Score"};
      String[] names = {"Matches", "Tournaments", "Teams", "Stats", "Scoring"};
      tabs.setPadding(dp(14), 0, dp(14), dp(6));
      for (int index = 0; index < keys.length; index++) {
        final String key = keys[index];
        TextView tab = Ui.chip(this, pal, names[index], key.equals(page), () -> { page = key; draw(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(4), 0, dp(4), 0);
        tabs.addView(tab, lp);
      }
      strip.addView(tabs);
      root.addView(strip);
    }
    ScrollView scroll = new ScrollView(this);
    pageScroll = scroll;
    // Rebuilding the scoring page must not move the scorer back to the header.
    scroll.setFocusableInTouchMode(true);
    scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    body = new LinearLayout(this);
    body.setOrientation(1);
    body.setPadding(dp(18), dp(10), dp(18), dp(24));
    scroll.addView(body);
    root.addView(PullRefresh.wrap(this, scroll, this::refreshPage), new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(bottomNav());
    switch (page) {
      case "Matches":
        matchDirectory();
        break;
      case "Stats":
        heading("Player performances", "Stats & rankings", "Career figures from matches saved on this device.");
        action(body, "Open player statistics", () -> startActivity(new Intent(this, PlayerDirectoryActivity.class)));
        break;
      case "Score":
        score();
        break;
      case "Cups":
        cups();
        break;
      case "Teams":
        teams();
        break;
      case "Store":
        store();
        break;
      case "Tools":
        tools();
        break;
      case "Search":
        searchPage();
        break;
      default:
        home();
    }
    scroll.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override public void onGlobalLayout() {
        scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        if (pageScroll == scroll) scroll.scrollTo(0, previousY);
      }
    });
  }

  private boolean isMyCricket() {
    return Arrays.asList("Matches", "Cups", "Teams", "Stats", "Score").contains(page);
  }

  /** First launch and logout both return to an explicit signed-out entry screen. */
  private void welcome() {
    LinearLayout box = Ui.column(this);
    box.setGravity(Gravity.CENTER_HORIZONTAL);
    box.setPadding(dp(28), dp(40), dp(28), dp(24));
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true); scroll.addView(box);
    root.addView(PullRefresh.wrap(this, scroll, done -> { updates.refresh(); draw(); done.run(); }), new LinearLayout.LayoutParams(-1, 0, 1));
    box.addView(Ui.text(this, "BOUNDARY", 34, GREEN, true));
    TextView tagline = Ui.text(this, "YOUR CRICKET IDENTITY", 12, MUTED, true);
    tagline.setPadding(0, dp(8), 0, 0); box.addView(tagline);
    View space = new View(this); box.addView(space, new LinearLayout.LayoutParams(1, 0, 1));
    boolean signedOut = getSharedPreferences("boundary-session", 0).getBoolean("signedOut", false);
    TextView title = Ui.text(this, signedOut ? "You're signed out" : "Your cricket starts here", 27, INK, true);
    title.setGravity(Gravity.CENTER); box.addView(title);
    TextView detail = Ui.text(this, "Continue with Google, choose your permanent @username, and join your team.", 15, MUTED, false);
    detail.setGravity(Gravity.CENTER); detail.setPadding(0, dp(12), 0, dp(24)); box.addView(detail);
    box.addView(Ui.googleSignIn(this, () ->
        startActivity(new Intent(this, PlayerAccountActivity.class).putExtra("autoBegin", true))), new LinearLayout.LayoutParams(-1, -2));
    TextView legal = Ui.text(this, "By continuing, you agree to the Terms & Conditions and acknowledge the Privacy Policy.", 12, MUTED, false);
    legal.setGravity(Gravity.CENTER); legal.setPadding(0, dp(18), 0, dp(10)); box.addView(legal);
    LinearLayout links = Ui.row(this);
    links.addView(Ui.chip(this, pal, "Terms", false, () -> openPolicy("/terms.html")));
    links.addView(Ui.chip(this, pal, "Privacy", false, () -> openPolicy("/privacy.html"))); box.addView(links);
    TextView guest = Ui.text(this, "Explore as a guest", 14, GREEN, true);
    guest.setMinHeight(dp(48)); guest.setGravity(Gravity.CENTER); guest.setPadding(dp(16), dp(16), dp(16), 0);
    guest.setOnClickListener(v -> {
      getSharedPreferences("boundary-session", 0).edit().putBoolean("guest", true).apply();
      role = "Spectator"; getPreferences(0).edit().putString("role", role).apply(); draw();
    }); box.addView(guest);
  }

  private View plusButton;

  /** Avatar on the left opens the profile panel; the + on the right opens the create menu. */
  private View topBar() {
    LinearLayout head = Ui.row(this);
    head.setPadding(dp(16), dp(10), dp(16), dp(10));
    head.setBackgroundColor(PAPER);
    View avatar = Ui.avatar(this, pal, profile.visiblePhoto(), profile.name(), 44);
    avatar.setContentDescription("Open profile panel");
    avatar.setOnClickListener(v -> guard(this::drawer));
    head.addView(avatar);
    String title = page.equals("Home") ? "" : isMyCricket() ? "My Cricket"
        : page.equals("Tools") ? "Umpire tools" : page;
    TextView t = Ui.text(this, title, 20, INK, true);
    t.setPadding(dp(14), 0, 0, 0);
    head.addView(t, Ui.weight(1));
    View plus = Ui.iconButton(this, pal, "plus", 48, GREEN, ON_ACCENT, () -> guard(this::quickCreate));
    plus.setContentDescription("Create match, tournament, team or broadcast");
    plusButton = plus;
    head.addView(plus);
    return head;
  }

  private View bottomNav() {
    LinearLayout nav = Ui.row(this);
    nav.setBackgroundColor(SURFACE);
    nav.setPadding(dp(6), dp(6), dp(6), dp(6));
    String[][] tabs = {{"Home", "Home", "home"}, {"Matches", "My Cricket", "bat"}, {"Store", "Store", "store"}};
    for (String[] tab : tabs) {
      boolean on = tab[0].equals(page) || tab[0].equals("Matches") && isMyCricket();
      LinearLayout item = Ui.column(this);
      item.setGravity(Gravity.CENTER);
      item.setPadding(0, dp(4), 0, dp(2));
      android.widget.FrameLayout iconWrap = new android.widget.FrameLayout(this);
      if (on) iconWrap.setBackground(Ui.round(pal.accentSoft, dp(16)));
      iconWrap.addView(new IconView(this, tab[2], on ? GREEN : MUTED),
          new android.widget.FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
      item.addView(iconWrap, new LinearLayout.LayoutParams(dp(60), dp(32)));
      TextView label = Ui.text(this, tab[1], 11, on ? GREEN : MUTED, on);
      label.setPadding(0, dp(4), 0, 0);
      label.setGravity(Gravity.CENTER);
      item.addView(label, new LinearLayout.LayoutParams(-2, -2));
      item.setContentDescription(tab[1]);
      item.setOnClickListener(v -> { page = tab[0]; draw(); });
      nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
    return nav;
  }

  /** The + menu, anchored under the button like a dropdown. */
  private void quickCreate() {
    LinearLayout menu = Ui.column(this);
    menu.setBackground(Ui.round(SURFACE, dp(18)));
    menu.setElevation(dp(10));
    menu.setPadding(0, dp(8), 0, dp(8));
    android.widget.PopupWindow popup = new android.widget.PopupWindow(menu, dp(270), -2, true);
    popup.setElevation(dp(10));
    Object[][] groups = {
        {new String[] {"Start a match", "Go live"}},
        {new String[] {"Add a tournament / series", "Add team"}},
        {new String[] {"Get help"}}};
    for (int g = 0; g < groups.length; g++) {
      if (g > 0) {
        View line = Ui.divider(this, pal);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.7f)));
        lp.setMargins(dp(18), dp(6), dp(18), dp(6));
        menu.addView(line, lp);
      }
      for (String label : (String[]) groups[g][0]) {
        TextView item = Ui.text(this, label, 16, INK, false);
        item.setPadding(dp(22), dp(14), dp(22), dp(14));
        item.setOnClickListener(v -> { popup.dismiss(); guard(() -> create(label)); });
        menu.addView(item);
      }
    }
    if (plusButton != null) popup.showAsDropDown(plusButton, -dp(270) + plusButton.getWidth(), dp(6));
    else popup.showAtLocation(root, Gravity.TOP | Gravity.END, dp(16), dp(80));
  }

  /** Profile panel sliding in from the left, as in the reference design minus the excluded entries. */
  private void drawer() {
    com.google.android.material.sidesheet.SideSheetDialog sheet = new com.google.android.material.sidesheet.SideSheetDialog(this);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(SURFACE);
    LinearLayout panel = Ui.column(this);
    panel.setPadding(dp(16), dp(16), dp(16), dp(24));
    scroll.addView(panel);

    LinearLayout header = Ui.column(this);
    header.setBackground(Ui.round(SURFACE2, dp(20)));
    header.setPadding(dp(16), dp(16), dp(16), dp(16));
    LinearLayout who = Ui.row(this);
    who.setGravity(Gravity.TOP);
    View avatar = Ui.avatar(this, pal, profile.visiblePhoto(), profile.name(), 64);
    avatar.setOnClickListener(v -> { sheet.dismiss(); startActivity(new Intent(this, ProfilePhotoActivity.class)); });
    who.addView(avatar);
    LinearLayout details = Ui.column(this);
    details.setPadding(dp(14), 0, dp(8), 0);
    String accountName = getSharedPreferences("boundary-account", 0).getString("name", "");
    String name = profile.name().isEmpty() ? (accountName.isEmpty() ? "Your cricket profile" : accountName) : profile.name();
    details.addView(Ui.text(this, name, 18, INK, true));
    for (String line : new String[] {profile.email()}) {
      if (line.isEmpty()) continue;
      TextView t = Ui.text(this, line, 13, MUTED, false);
      t.setPadding(0, dp(4), 0, 0);
      details.addView(t);
    }
    LinearLayout social = Ui.row(this);
    social.setPadding(0, dp(10), 0, 0);
    social.addView(Ui.chip(this, pal, "Connections", false, () -> { sheet.dismiss();
      startActivity(new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_TAB, "Connections")); }));
    social.addView(Ui.space(this, 8, 1));
    social.addView(Ui.iconButton(this, pal, "qr", 34, SURFACE, INK, () -> { sheet.dismiss();
      startActivity(new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_SHEET, "qr")); }));
    who.addView(details, Ui.weight(1));
    who.addView(Ui.iconButton(this, pal, "edit", 36, SURFACE, INK, () -> { sheet.dismiss(); editProfile(); }));
    header.addView(who);
    if (!PlayerAccount.linked(this)) {
      header.addView(Ui.googleSignIn(this, () -> { sheet.dismiss(); startActivity(new Intent(this, PlayerAccountActivity.class)); }));
    }
    // Actions need the whole card width; inside the name column the QR button was clipped.
    TextView connections = (TextView) social.getChildAt(0);
    connections.setSingleLine(false);
    connections.setMinHeight(dp(48));
    connections.setLayoutParams(Ui.weight(1));
    View qr = social.getChildAt(2);
    qr.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
    qr.setContentDescription("Share your cricket profile QR code");
    header.addView(social, new LinearLayout.LayoutParams(-1, -2));
    TextView workspace = Ui.text(this, "Workspace · " + role + "  ▾", 14, ON_ACCENT, true);
    workspace.setBackground(Ui.round(GREEN, dp(14)));
    workspace.setPadding(dp(14), dp(10), dp(14), dp(10));
    LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
    wl.topMargin = dp(14);
    workspace.setOnClickListener(v -> { sheet.dismiss(); roles(); });
    header.addView(workspace, wl);
    panel.addView(header);

    LinearLayout items = Ui.column(this);
    items.setPadding(0, dp(10), 0, 0);
    panel.addView(items);
    drawerItem(items, "bat", "Your Cricket Profile", () -> { sheet.dismiss(); startActivity(new Intent(this, ProfileActivity.class)); });
    drawerItem(items, "performance", "My Performance", () -> { sheet.dismiss(); startActivity(new Intent(this, PerformanceActivity.class)); });
    drawerItem(items, "store", "Boundary Store", () -> { sheet.dismiss(); page = "Store"; draw(); });
    if (role.equals("Umpire"))
      drawerItem(items, "settings", "Umpire tools", () -> { sheet.dismiss(); page = "Tools"; draw(); });
    items.addView(drawerRule());
    drawerItem(items, "appearance", "Appearance", () -> { sheet.dismiss(); appearance(); });
    drawerItem(items, "info", "Check for updates", () -> { sheet.dismiss(); updates.checkNow(); });
    items.addView(drawerRule());
    drawerGroup(items, "support", "Support", new String[][] {
        {"help", "Get help"}, {"support", "Contact support"}, {"info", "Report a problem"}}, label -> {
      sheet.dismiss();
      if (label.equals("Get help")) help();
      else if (label.equals("Contact support")) contactSupport("Boundary support request", "");
      else contactSupport("Boundary problem report", "What happened, and what did you expect?\n");
    });
    items.addView(drawerRule());
    drawerGroup(items, "others", "Others", new String[][] {
        {"star", "Rate us"}, {"info", "About us"}, {"shield", "Privacy Policy"},
        {"doc", "Terms of Service"}, {"return", "Return & Exchange Policy"}}, label -> {
      sheet.dismiss();
      switch (label) {
        case "Rate us": rateUs(); break;
        case "About us": message("About Boundary", "Boundary is an offline-first cricket scoring and tournament app for local, college, turf and club cricket.\n\nVersion " + appVersion()); break;
        case "Privacy Policy": openPolicy("/privacy.html"); break;
        case "Terms of Service": openPolicy("/terms.html"); break;
        default: openPolicy("/returns.html");
      }
    });
    items.addView(drawerRule());
    drawerGroup(items, "settings", "Account Settings", new String[][] {
        {"bell", "Notification Preference"}, {"language", "Change Language"}, {"receipt", "Purchase History"},
        {"trash", "Delete account"}, {"clear", "Clear data"}, {"logout", "Logout"}}, label -> {
      sheet.dismiss();
      switch (label) {
        case "Notification Preference":
          startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
          break;
        case "Change Language":
          if (Build.VERSION.SDK_INT >= 33) {
            try {
              startActivity(new Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + getPackageName())));
              break;
            } catch (ActivityNotFoundException ignored) { }
          }
          message("Change Language", "Boundary is available in English. More languages will appear here as they are added.");
          break;
        case "Purchase History": startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PlayerAccount.ORIGIN + "/shop.html#orders"))); break;
        case "Delete account": deleteAccount(); break;
        case "Clear data": clearData(); break;
        default: logout();
      }
    });

    LinearLayout foot = Ui.column(this);
    foot.setGravity(Gravity.CENTER);
    foot.setPadding(0, dp(28), 0, 0);
    foot.addView(Ui.text(this, "Boundary", 22, MUTED, true));
    TextView version = Ui.text(this, "Version " + appVersion(), 12, MUTED, false);
    version.setPadding(0, dp(4), 0, 0);
    foot.addView(version);
    panel.addView(foot);

    int width = Math.min(dp(420), Math.round(getResources().getDisplayMetrics().widthPixels * 0.86f));
    sheet.setContentView(scroll, new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
    // The Material side sheet container is fixed at 256dp by default; widen it to fit the panel.
    View container = sheet.findViewById(com.google.android.material.R.id.m3_side_sheet);
    if (container != null) {
      ViewGroup.LayoutParams lp = container.getLayoutParams();
      lp.width = width;
      container.setLayoutParams(lp);
    }
    sheet.setSheetEdge(Gravity.START); // needs the sheet view, so only after setContentView
    sheet.show();
  }

  private View drawerRule() {
    View v = Ui.divider(this, pal);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.7f)));
    lp.setMargins(dp(8), dp(4), dp(8), dp(4));
    v.setLayoutParams(lp);
    return v;
  }

  private LinearLayout drawerItem(LinearLayout parent, String icon, String label, Runnable action) {
    LinearLayout r = Ui.row(this);
    r.setPadding(dp(8), dp(14), dp(8), dp(14));
    r.addView(new IconView(this, icon, INK), new LinearLayout.LayoutParams(dp(24), dp(24)));
    TextView t = Ui.text(this, label, 16, INK, false);
    t.setPadding(dp(18), 0, 0, 0);
    r.addView(t, Ui.weight(1));
    if (action != null) r.setOnClickListener(v -> guard(action));
    parent.addView(r);
    return r;
  }

  private void drawerGroup(LinearLayout parent, String icon, String label, String[][] children, Consumer<String> onPick) {
    LinearLayout head = drawerItem(parent, icon, label, null);
    IconView arrow = new IconView(this, "chevron-down", INK);
    head.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(22)));
    LinearLayout box = Ui.column(this);
    box.setVisibility(View.GONE);
    box.setPadding(dp(22), 0, 0, 0);
    for (String[] child : children) drawerItem(box, child[0], child[1], () -> onPick.accept(child[1]));
    parent.addView(box);
    head.setOnClickListener(v -> {
      boolean open = box.getVisibility() != View.VISIBLE;
      box.setVisibility(open ? View.VISIBLE : View.GONE);
      arrow.setRotation(open ? 180 : 0);
    });
  }

  /** Light, Dark or Use device theme, saved and applied straight away. */
  private void appearance() {
    com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
    LinearLayout box = Ui.column(this);
    box.setBackgroundColor(SURFACE);
    box.setPadding(dp(20), dp(20), dp(20), dp(24));
    box.addView(Ui.text(this, "Appearance", 24, INK, true));
    String[][] options = {{"light", "Light"}, {"dark", "Dark"}, {"system", "Use device theme"}};
    final String[] chosen = {Ui.appearance(this)};
    List<LinearLayout> rows = new ArrayList<>();
    LinearLayout list = Ui.column(this);
    list.setPadding(0, dp(18), 0, dp(18));
    for (String[] option : options) {
      LinearLayout r = Ui.row(this);
      r.setPadding(dp(16), dp(16), dp(16), dp(16));
      TextView dot = new TextView(this);
      r.addView(dot, new LinearLayout.LayoutParams(dp(22), dp(22)));
      TextView t = Ui.text(this, option[1], 16, INK, false);
      t.setPadding(dp(16), 0, 0, 0);
      r.addView(t);
      r.setTag(option[0]);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.bottomMargin = dp(10);
      list.addView(r, lp);
      rows.add(r);
    }
    Runnable paint = () -> {
      for (LinearLayout r : rows) {
        boolean on = r.getTag().equals(chosen[0]);
        r.setBackground(on ? Ui.outline(pal.accentSoft, GREEN, dp(1), dp(18)) : Ui.round(SURFACE2, dp(18)));
        r.getChildAt(0).setBackground(on ? Ui.outline(GREEN, GREEN, dp(5), dp(11)) : Ui.outline(0, MUTED, dp(2), dp(11)));
      }
    };
    for (LinearLayout r : rows) r.setOnClickListener(v -> { chosen[0] = (String) r.getTag(); paint.run(); });
    paint.run();
    box.addView(list);
    TextView save = Ui.pill(this, pal, "Save preference", true, () -> {
      getSharedPreferences(Ui.PREFS, 0).edit().putString(Ui.APPEARANCE, chosen[0]).apply();
      sheet.dismiss();
      Ui.applyAppearance(this);
      recreate();
    });
    box.addView(save, new LinearLayout.LayoutParams(-1, -2));
    sheet.setContentView(box);
    sheet.show();
  }

  private void editProfile() {
    LinearLayout box = form();
    EditText name = field(box, "Display name", profile.name(), false),
        email = field(box, "Email (shown on your panel only)", profile.email(), false);
    PlayerProfile p = profile.attributes();
    EditText city = field(box, "City", p.city, false);
    String[] orders = new String[PlayerProfile.BATTING_ORDERS.length + 1];
    orders[0] = "Batting order not recorded";
    System.arraycopy(PlayerProfile.BATTING_ORDERS, 0, orders, 1, PlayerProfile.BATTING_ORDERS.length);
    Spinner order = spinner(box, "Batting order", orders);
    order.setSelection(Math.max(0, Arrays.asList(orders).indexOf(p.battingOrder)));
    Spinner hand = spinner(box, "Batting hand", new String[] {"Not recorded", "RHB", "LHB"});
    hand.setSelection(p.battingHand.equals("RHB") ? 1 : p.battingHand.equals("LHB") ? 2 : 0);
    String[] styles = new String[PlayerProfile.BOWLING_STYLES.length + 1];
    styles[0] = "Does not bowl / not recorded";
    System.arraycopy(PlayerProfile.BOWLING_STYLES, 0, styles, 1, PlayerProfile.BOWLING_STYLES.length);
    Spinner style = spinner(box, "Bowling style", styles);
    style.setSelection(Math.max(0, Arrays.asList(styles).indexOf(p.bowlingStyle)));
    box.addView(text("Your stats come from squad places that are you: Google-registered places for your linked account, plus any you mark with “This is me” in Teams.", 12, MUTED, false));
    dialog("Edit profile", box, "Save profile", () -> {
      profile.saveDetails(val(name), val(email));
      p.city = val(city);
      p.battingOrder = order.getSelectedItemPosition() == 0 ? "" : orders[order.getSelectedItemPosition()];
      p.battingHand = hand.getSelectedItemPosition() == 0 ? "" : (String) hand.getSelectedItem();
      p.bowlingStyle = style.getSelectedItemPosition() == 0 ? "" : styles[style.getSelectedItemPosition()];
      profile.saveAttributes(p);
      draw();
    });
  }

  private void rateUs() {
    LinearLayout box = form();
    box.addView(text("How is Boundary working for your matches?", 15, INK, true));
    RatingBar stars = new RatingBar(this);
    stars.setNumStars(5);
    stars.setStepSize(1);
    box.addView(stars, new LinearLayout.LayoutParams(-2, -2));
    EditText note = field(box, "Anything we should improve? (optional)", "", false);
    dialog("Rate us", box, "Send rating", () -> contactSupport("Boundary rating: " + (int) stars.getRating() + "/5", val(note)));
  }

  private void logout() {
    new AlertDialog.Builder(this).setTitle("Log out of Boundary?")
        .setMessage("This signs you out of this app immediately and opens your browser to finish signing out of Boundary. Your registered username, saved matches, teams and recovery codes are kept.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Log out", (d, w) -> {
          try { PlayerAccount.disconnectLocally(getApplicationContext()); }
          catch (Exception error) { message("Couldn't sign out", error.getMessage()); return; }
          getPreferences(0).edit().putString("role", "Spectator").putBoolean("browserSignOutPending", true)
              .putBoolean("logoutInProgress", false).remove("logoutError").apply();
          getSharedPreferences("boundary-session", 0).edit().putBoolean("guest", false).apply();
          completePendingLogout();
          retryDeviceRevocations();
        }).show();
  }

  private void retryDeviceRevocations() {
    synchronized (MainActivity.class) {
      if (logoutWorkerRunning) return;
      if (getSharedPreferences("boundary-revocations", 0).getStringSet("tokens", Collections.emptySet()).isEmpty()) return;
      logoutWorkerRunning = true;
    }
    Context app = getApplicationContext();
    new Thread(() -> {
      try { PlayerAccount.retryRevocations(app); }
      catch (Exception offline) { android.util.Log.w("BoundaryLogout", "Remote revocation pending; local session is signed out"); }
      finally { logoutWorkerRunning = false; }
    }, "Boundary-logout-revoke").start();
  }

  private void completePendingLogout() {
    if (!logoutUiVisible || isFinishing() || isDestroyed()) return;
    android.content.SharedPreferences prefs = getPreferences(0);
    if (prefs.getBoolean("logoutInProgress", false)) {
      if (logoutWorkerRunning) {
        handler.postDelayed(this::completePendingLogout, 300);
        return;
      }
      // A fresh process has no old worker. Recover rather than polling a persisted flag forever.
      android.content.SharedPreferences.Editor recovery = prefs.edit().putBoolean("logoutInProgress", false);
      if (getSharedPreferences("boundary-account", 0).getString("token", "").isEmpty())
        recovery.putString("role", "Spectator").putBoolean("browserSignOutPending", true);
      else recovery.putString("logoutError", "Logout was interrupted. Connect to the internet and choose Logout again.");
      recovery.apply();
    }
    if (prefs.contains("logoutError")) {
      String error = prefs.getString("logoutError", "Connect and try again.");
      prefs.edit().remove("logoutError").apply(); message("Logout did not finish", error);
    }
    if (!prefs.getBoolean("browserSignOutPending", false)) return;
    prefs.edit().remove("browserSignOutPending").apply();
    role = "Spectator"; page = "Home"; draw();
    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PlayerAccount.ORIGIN + "/logout.html"))); }
    catch (ActivityNotFoundException missing) {
      message("App signed out", "The browser could not open. Visit " + PlayerAccount.ORIGIN + "/logout.html to finish signing out of Boundary in your browser.");
    }
  }

  private void deleteAccount() {
    new AlertDialog.Builder(this).setTitle("Delete your account?")
        .setMessage("This logs this device out, removes your profile details and photo from this phone, and asks Boundary support to delete your registered player record. Match scorecards that others saved are not affected.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete account", (d, w) -> {
          String id = profile.accountId();
          if (!profile.clearCurrentProfile()) Toast.makeText(this, "Could not remove the photo file", Toast.LENGTH_SHORT).show();
          new Thread(() -> {
            try { PlayerAccount.request(this, new org.json.JSONObject().put("action", "revoke")); } catch (Exception ignored) { }
            getSharedPreferences("boundary-account", 0).edit().clear().apply();
            runOnUiThread(() -> { contactSupport("Delete my Boundary account", "Please delete my registered player account" + (id.isEmpty() ? "." : " (" + id + ").")); draw(); });
          }, "Boundary-delete").start();
        }).show();
  }

  private void clearData() {
    new AlertDialog.Builder(this).setTitle("Clear all data on this phone?")
        .setMessage("Every saved match, team, tournament, recovery code and unpublished score on this phone will be erased. Export or note your private match recovery codes first. This cannot be undone.")
        .setNegativeButton("Keep my data", null)
        .setPositiveButton("Erase everything", (d, w) -> {
          android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
          if (am != null) am.clearApplicationUserData();
        }).show();
  }

  private void create(String choice) {
    switch (choice) {
      case "Start a match": if (writable()) matchSetup(null, null); break;
      case "Go live": startActivity(new Intent(this, LiveStreamActivity.class)); break;
      case "Add a tournament / series": newCup(); break;
      case "Add team": page = "Teams"; draw(); break;
      default: help();
    }
  }

  private void help() {
    String[] items = {"How do I score my first match?", "Who can edit a match?", "How do I add players?",
        "Where are voice commands and the stump cam?", "Contact support"};
    String[] answers = {
        "Add two teams in My Cricket → Teams, add players by @username, then tap + → Start a match. Hold a run button to record zones and overthrows.",
        "Organizer manages teams and tournaments. Scorer records deliveries and corrections. Umpire adds umpire tools and voice scoring. These three can edit on this device. Player and Spectator view matches without editing. Workspaces are local modes, not server-assigned permissions.",
        "Each player signs in with Google and chooses a unique @username. In Teams, choose Add registered player to find their username, or Invite player on WhatsApp. The invitation opens registration; ask the player to send back their username, then find and add them to the squad.",
        "Switch the workspace to Umpire from the profile panel. Umpire tools then appears in the panel with voice commands and the stump cam."};
    new AlertDialog.Builder(this).setTitle("Get help").setItems(items, (d, w) -> {
      if (w < answers.length) message(items[w], answers[w]);
      else contactSupport("Boundary support request", "");
    }).show();
  }

  private void contactSupport(String subject, String body) {
    Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body + "\n\nApp version " + appVersion() + " · Android " + Build.VERSION.RELEASE);
    try { startActivity(mail); }
    catch (ActivityNotFoundException none) { openPolicy("/terms.html#contact"); }
  }

  private String appVersion() {
    try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
    catch (Exception e) { return ""; }
  }

  /** My Cricket → Matches: the ten most recent matches as cards; each opens the match detail. */
  private void matchDirectory() {
    body.addView(Ui.pill(this, pal, "Start a match", true, () -> guard(() -> { if (writable()) matchSetup(null, null); })),
        new LinearLayout.LayoutParams(-1, -2));
    List<Match> recent = new ArrayList<>(repo.matches());
    recent.sort((x, y) -> Long.compare(y.played(), x.played()));
    if (recent.isEmpty()) {
      gap();
      body.addView(text("No matches yet. Add two teams in Teams, then start your first match.", 15, MUTED, false));
      return;
    }
    body.addView(Ui.sectionTitle(this, pal, "Last " + Math.min(10, recent.size()) + " matches", null));
    for (Match m : recent.subList(0, Math.min(10, recent.size()))) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.bottomMargin = dp(12);
      body.addView(matchCard(m), lp);
    }
  }

  private void roles() {
    String[] rs = {"Organizer", "Scorer", "Umpire", "Player", "Spectator"};
    String[] descriptions = {"Organizer — teams, fixtures and tournaments", "Scorer — ball-by-ball scoring and corrections",
        "Umpire — scoring workspace, umpire tools and voice scoring", "Player — your profile and read-only matches",
        "Spectator — view matches and scorecards"};
    new AlertDialog.Builder(this)
        .setTitle("Workspace role")
        .setItems(
            descriptions,
            (d, w) -> {
              String selectedRole = rs[w];
              if (selectedRole.equals("Player") && !PlayerAccount.linked(this)) {
                startActivity(new Intent(this, PlayerAccountActivity.class));
                return;
              }
              role = selectedRole;
              getPreferences(0).edit().putString("role", role).apply();
              if (match != null && (role.equals("Scorer") || role.equals("Umpire"))) page = "Score";
              draw();
            })
        .show();
  }

  private boolean writable() {
    if (role.equals("Spectator") || role.equals("Player")) {
      message(
          "Read-only workspace",
          "Switch to Organizer, Scorer or Umpire to make changes on this device.");
      return false;
    }
    return true;
  }

  private void home() {
    body.setPadding(0, dp(4), 0, dp(24));
    LinearLayout pad = Ui.column(this);
    pad.setPadding(dp(16), 0, dp(16), 0);
    body.addView(pad);

    LinearLayout search = Ui.row(this);
    search.setBackground(Ui.round(SURFACE2, dp(28)));
    search.setPadding(dp(18), dp(14), dp(18), dp(14));
    search.addView(new IconView(this, "search", MUTED), new LinearLayout.LayoutParams(dp(24), dp(24)));
    TextView hint = Ui.text(this, "Search players, teams or matches", 16, MUTED, false);
    hint.setPadding(dp(14), 0, 0, 0);
    search.addView(hint);
    search.setOnClickListener(v -> { page = "Search"; draw(); });
    pad.addView(search);

    LinearLayout follow = Ui.row(this);
    follow.setPadding(0, dp(22), 0, dp(4));
    android.widget.FrameLayout me = new android.widget.FrameLayout(this);
    me.addView(Ui.avatar(this, pal, profile.visiblePhoto(), profile.name(), 70));
    View add = Ui.iconButton(this, pal, "plus", 26, GREEN, ON_ACCENT, null);
    android.widget.FrameLayout.LayoutParams addLp = new android.widget.FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM | Gravity.END);
    me.addView(add, addLp);
    me.setOnClickListener(v -> startActivity(new Intent(this, ProfilePhotoActivity.class)));
    me.setContentDescription("Add your profile photo");
    follow.addView(me, new LinearLayout.LayoutParams(dp(74), dp(74)));
    TextView bubble = Ui.text(this, "Follow your favourite cricketers to see their updates here.", 14, MUTED, false);
    bubble.setBackground(Ui.outline(SURFACE, pal.line, dp(1), dp(16)));
    bubble.setPadding(dp(16), dp(14), dp(16), dp(14));
    bubble.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_TAB, "Connections")));
    LinearLayout.LayoutParams bubbleLp = Ui.weight(1);
    bubbleLp.leftMargin = dp(14);
    follow.addView(bubble, bubbleLp);
    pad.addView(follow);

    pad.addView(Ui.sectionTitle(this, pal, "Matches of your interest", () -> { page = "Matches"; draw(); }));
    List<Match> matches = repo.matches();
    HorizontalScrollView carousel = new HorizontalScrollView(this);
    carousel.setHorizontalScrollBarEnabled(false);
    carousel.setClipToPadding(false);
    carousel.setPadding(dp(16), 0, dp(16), 0);
    LinearLayout cards = Ui.row(this);
    cards.setGravity(Gravity.TOP);
    int shown = 0;
    for (Match m : matches) {
      if (shown++ == 8) break;
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300), -2);
      lp.rightMargin = dp(12);
      cards.addView(matchCard(m), lp);
    }
    if (matches.isEmpty()) {
      LinearLayout empty = Ui.card(this, pal);
      empty.addView(Ui.text(this, "No matches yet", 18, INK, true));
      TextView d = Ui.text(this, "Add two teams, then start your first match. Scoring works without internet.", 14, MUTED, false);
      d.setPadding(0, dp(6), 0, dp(12));
      empty.addView(d);
      empty.addView(Ui.pill(this, pal, "Start a match", true, () -> guard(() -> { if (writable()) matchSetup(null, null); })));
      cards.addView(empty, new LinearLayout.LayoutParams(dp(300), -2));
    }
    carousel.addView(cards);
    body.addView(carousel);

    LinearLayout pad2 = Ui.column(this);
    pad2.setPadding(dp(16), dp(20), dp(16), 0);
    body.addView(pad2);
    LinearLayout live = Ui.row(this);
    android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xff0d4f37, 0xff147653});
    g.setCornerRadius(dp(18));
    live.setBackground(g);
    live.setPadding(dp(18), dp(18), dp(18), dp(18));
    android.widget.FrameLayout camera = new android.widget.FrameLayout(this);
    camera.setBackground(Ui.round(0x33ffffff, dp(14)));
    camera.addView(new IconView(this, "live", Color.WHITE), new android.widget.FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
    live.addView(camera, new LinearLayout.LayoutParams(dp(96), dp(84)));
    LinearLayout copy = Ui.column(this);
    copy.setPadding(dp(16), 0, 0, 0);
    copy.addView(Ui.text(this, "Let your cricket be seen, shared, and celebrated.", 17, Color.WHITE, true));
    TextView start = Ui.text(this, "Start streaming", 13, 0xff0d4f37, true);
    start.setBackground(Ui.round(0xffd0ed8c, dp(14)));
    start.setPadding(dp(14), dp(8), dp(14), dp(8));
    LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-2, -2);
    startLp.topMargin = dp(10);
    copy.addView(start, startLp);
    live.addView(copy, Ui.weight(1));
    live.setOnClickListener(v -> startActivity(new Intent(this, LiveStreamActivity.class)));
    pad2.addView(live);

    pad2.addView(Ui.sectionTitle(this, pal, "Cricketers following you", () ->
        startActivity(new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_TAB, "Connections"))));
    LinearLayout followers = Ui.card(this, pal);
    followers.addView(Ui.text(this, "Share your Boundary QR code so teammates can follow you.", 14, MUTED, false));
    LinearLayout actions = Ui.row(this);
    actions.setPadding(0, dp(12), 0, 0);
    actions.addView(Ui.pill(this, pal, "My QR code", true, () -> startActivity(new Intent(this, ProfileActivity.class).putExtra(ProfileActivity.EXTRA_SHEET, "qr"))));
    actions.addView(Ui.space(this, 10, 1));
    actions.addView(Ui.pill(this, pal, "Live matches", false, () -> startActivity(new Intent(this, SpectatorActivity.class))));
    followers.addView(actions);
    pad2.addView(followers);

    body = pad2;
    for (Scorebook sheet : repo.list("scorebook", Scorebook.class)) {
      StringBuilder summary = new StringBuilder(sheet.team + "\n");
      for (ImportedPlayer p : sheet.players)
        summary
            .append(p.name)
            .append(" ")
            .append(p.runs)
            .append(" (")
            .append(p.balls)
            .append(")\n");
      gap();
      card("Imported past scorecard", sheet.name, summary.toString());
    }
  }

  /** CricHeroes-style match card: status strip, title, meta line, both scores, result, actions. */
  private View matchCard(Match m) {
    LinearLayout card = Ui.column(this);
    card.setBackground(Ui.outline(SURFACE, pal.line, dp(1), dp(18)));
    card.setClipToOutline(true);
    String status = m.result();
    boolean liveNow = status.equals("Live");
    String tournamentName = "";
    for (Tournament t : repo.tournaments()) if (t.id.equals(m.tournament)) tournamentName = t.name;
    TextView strip = Ui.text(this, liveNow ? "Live now" : "Your match", 13, liveNow ? ON_ACCENT : GREEN, true);
    strip.setBackground(Ui.round(liveNow ? GREEN : pal.accentSoft, 0));
    strip.setPadding(dp(16), dp(10), dp(16), dp(10));
    card.addView(strip, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout inner = Ui.column(this);
    inner.setPadding(dp(16), dp(12), dp(16), dp(14));
    card.addView(inner);
    LinearLayout titleRow = Ui.row(this);
    titleRow.addView(Ui.text(this, tournamentName.isEmpty() ? "Individual Match" : tournamentName, 16, INK, true), Ui.weight(1));
    TextView badge = Ui.text(this, liveNow ? "Live" : "Result", 12, liveNow ? ON_ACCENT : INK, true);
    badge.setBackground(Ui.round(liveNow ? pal.danger : SURFACE2, dp(12)));
    badge.setPadding(dp(10), dp(4), dp(10), dp(4));
    titleRow.addView(badge);
    inner.addView(titleRow);
    String when = new java.text.SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(new Date(m.played()));
    TextView meta = Ui.text(this, when + "  |  " + m.rules.overs + " Ov.  |  " + (m.venue.isEmpty() ? "Ground not recorded" : m.venue), 13, MUTED, false);
    meta.setSingleLine(true);
    meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
    meta.setPadding(0, dp(6), 0, dp(10));
    inner.addView(meta);
    inner.addView(Ui.divider(this, pal));
    String winner = m.winner();
    for (int i = 0; i < 2; i++) {
      String team = i == 0 ? m.teamA : m.teamB;
      String score = "Yet to bat";
      for (int k = 0; k < Math.min(2, m.innings.size()); k++)
        if (m.innings.get(k).battingTeam.equals(team)) {
          State s = m.stateAt(k);
          score = s.runs + "/" + s.wickets + "  (" + s.overs(m.rules) + " Ov)";
        }
      boolean strong = team.equals(winner) || winner == null && i == 0;
      LinearLayout line = Ui.row(this);
      line.setPadding(0, dp(10), 0, 0);
      line.addView(Ui.text(this, team, 16, strong ? INK : MUTED, strong), Ui.weight(1));
      line.addView(Ui.text(this, score, 15, strong ? INK : MUTED, strong));
      inner.addView(line);
    }
    TextView result = Ui.text(this, liveNow ? m.current().battingTeam + " batting" : status, 14, INK, false);
    result.setPadding(0, dp(12), 0, dp(10));
    inner.addView(result);
    LinearLayout chips = Ui.row(this);
    chips.setGravity(Gravity.END);
    chips.addView(Ui.chip(this, pal, "Summary", false, () -> matchDetail(m, "Summary")));
    chips.addView(Ui.space(this, 8, 1));
    chips.addView(Ui.chip(this, pal, "Squads", false, () -> matchDetail(m, "Squads")));
    inner.addView(chips);
    card.setOnClickListener(v -> matchDetail(m, liveNow ? "Scorecard" : "Summary"));
    return card;
  }

  private void matchDetail(Match m, String tab) {
    startActivity(new Intent(this, MatchDetailActivity.class)
        .putExtra(MatchDetailActivity.EXTRA_MATCH_ID, m.id).putExtra(MatchDetailActivity.EXTRA_TAB, tab));
  }

  /** MatchDetailActivity's "Score this match" comes back here with the match to open. */
  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    String id = intent.getStringExtra(EXTRA_SCORE_MATCH);
    if (id == null) return;
    for (Match m : repo.matches()) if (m.id.equals(id)) { open(m); return; }
  }

  public static final String EXTRA_SCORE_MATCH = "scoreMatch";

  /** Search across saved players, teams and matches. */
  private void searchPage() {
    EditText query = new EditText(this);
    query.setHint("Search players, teams or matches");
    query.setSingleLine();
    query.setTextColor(INK);
    query.setHintTextColor(MUTED);
    query.setBackground(Ui.round(SURFACE2, dp(28)));
    query.setPadding(dp(18), dp(14), dp(18), dp(14));
    body.addView(query, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout results = Ui.column(this);
    results.setPadding(0, dp(12), 0, 0);
    body.addView(results);
    List<Team> teams = repo.teams();
    List<Match> matches = repo.matches();
    Runnable render = () -> {
      results.removeAllViews();
      String q = query.getText().toString().trim().toLowerCase(Locale.ROOT);
      if (q.isEmpty()) { results.addView(Ui.text(this, "Type a name to search this device's players, teams and scorecards.", 14, MUTED, false)); return; }
      int found = 0;
      for (Team t : teams) {
        for (String p : t.players)
          if (p.toLowerCase(Locale.ROOT).contains(q) && found++ < 40)
            results.addView(searchRow("user", p, t.name, () -> playerOptions(t.name, p)));
        if (t.name.toLowerCase(Locale.ROOT).contains(q) && found++ < 40)
          results.addView(searchRow("team", t.name, t.players.size() + " players", () -> players(t)));
      }
      for (Match m : matches)
        if ((m.name + " " + m.venue).toLowerCase(Locale.ROOT).contains(q) && found++ < 40)
          results.addView(searchRow("bat", m.name, m.result(), () -> open(m)));
      if (found == 0) results.addView(Ui.text(this, "Nothing matches “" + q + "”.", 14, MUTED, false));
    };
    query.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) { render.run(); }
      public void afterTextChanged(android.text.Editable e) {}
    });
    render.run();
    query.requestFocus();
  }

  private View searchRow(String icon, String title, String detail, Runnable action) {
    LinearLayout r = Ui.row(this);
    r.setPadding(dp(4), dp(12), dp(4), dp(12));
    r.addView(Ui.iconButton(this, pal, icon, 40, SURFACE2, INK, null));
    LinearLayout text = Ui.column(this);
    text.setPadding(dp(14), 0, 0, 0);
    text.addView(Ui.text(this, title, 16, INK, true));
    TextView d = Ui.text(this, detail, 13, MUTED, false);
    d.setPadding(0, dp(4), 0, 0);
    text.addView(d);
    r.addView(text, Ui.weight(1));
    r.setOnClickListener(v -> guard(action));
    return r;
  }

  private void open(Match m) {
    match = m;
    getPreferences(0).edit().putString("active", m.id).apply();
    page = "Score";
    draw();
  }

  private void persist() {
    match.revision++;
    repo.save(match);
    for (Tournament cup : repo.tournaments())
      if (cup.id.equals(match.tournament)) {
        cup.advance(repo.matches());
        repo.save(cup);
      }
    getPreferences(0).edit().putString("active", match.id).apply();
    Publication.queue(this, repo, match);
  }

  private void score() {
    if (match == null) {
      heading(
          "Match centre",
          "No match selected",
          "Create a match from Home, or open a scheduled fixture.");
      return;
    }
    State s = match.state();
    heading(
        "Match centre · Saved on this phone",
        match.current().battingTeam,
        match.name + " · " + match.venue);
    if (match.tossWinner != null && !match.tossWinner.isEmpty())
      body.addView(text("Toss: " + match.tossWinner + " chose to "
          + ("BOWL".equals(match.tossDecision) ? "bowl" : "bat"), 12, MUTED, false));
    if (role.equals("Scorer") || role.equals("Umpire")) {
      LinearLayout desk = card("OFFICIAL WORKSPACE · " + role.toUpperCase(Locale.ROOT),
          match.name, "Scorer: " + (match.scorer.isEmpty() ? "Unassigned" : match.scorer)
              + "   Umpire: " + (match.umpire.isEmpty() ? "Unassigned" : match.umpire));
      action(desk, "🎙  Voice scoring · push to talk", this::voice);
      desk.addView(text("Hold the microphone in the next screen, speak a command, then release."
          + " Review the recognized words before applying.", 12, MUTED, false));
    }
    LinearLayout board = new LinearLayout(this);
    board.setOrientation(1);
    board.setPadding(dp(20), dp(20), dp(20), dp(20));
    board.setBackground(bg(0xff202525, 22));
    if (match.activeIndex() >= 2)
      board.addView(text("SUPER OVER " + (match.activeIndex() / 2)
          + (match.activeIndex() % 2 == 0 ? " · FIRST INNINGS" : " · CHASE"),
          12, LIME, true));
    board.addView(
        text(
            (s.closed ? "INNINGS COMPLETE" : "LIVE") + (s.freeHit ? " · FREE HIT" : ""),
            10,
            LIME,
            true));
    board.addView(text(s.runs + " / " + s.wickets, 48, Color.WHITE, true));
    board.addView(
        text(
            s.overs(match.rules) + " overs    CRR " + fmt(s.rate(match.rules)),
            13,
            0xffd3dfd5,
            false));
    if (s.target > 0)
      board.addView(
          text(
              "Target "
                  + s.target
                  + " · Need "
                  + Math.max(0, s.target - s.runs)
                  + " from "
                  + Math.max(0, s.maxBalls - s.legal)
                  + " balls",
              13,
              LIME,
              true));
    body.addView(board);
    gap();
    List<Progress> deliveries = match.progressAt(match.activeIndex());
    if (!deliveries.isEmpty()) {
      int first = Math.max(0, deliveries.size() - 6);
      StringBuilder recentBalls = new StringBuilder();
      for (int i = first; i < deliveries.size(); i++) {
        if (recentBalls.length() > 0) recentBalls.append("   •   ");
        recentBalls.append(deliveries.get(i).label);
      }
      body.addView(text("RECENT BALLS   " + recentBalls, 12, MUTED, true));
      gap();
    }
    {
      int p = Cricket.probability(s, match.rules);
      body.addView(text("Win estimate  " + p + "%", 12, GREEN, true));
      ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(100);
      bar.setProgress(p);
      body.addView(bar, new LinearLayout.LayoutParams(-1, dp(9)));
      gap();
    }
    StringBuilder bat = new StringBuilder();
    for (String name : new String[] {s.striker, s.nonStriker})
      if (!name.isEmpty()) {
        Batting b = s.bat(name);
        bat.append(name)
            .append(name.equals(s.striker) ? " *" : "")
            .append("   ")
            .append(b.runs)
            .append(" (")
            .append(b.balls)
            .append(")\n");
      }
    String bowl =
        s.bowler.isEmpty()
            ? "Select next bowler"
            : s.bowler + "   " + s.bowl(s.bowler).wickets + "/" + s.bowl(s.bowler).runs;
    card("At the crease", bat.toString().trim(), "Bowling: " + bowl);
    if (!s.closed && !role.equals("Spectator") && !role.equals("Player")) {
      action(body, s.bowler.isEmpty() ? "Choose bowler" : "Change bowler", this::bowler);
      grid(
          new String[] {"0", "1", "2", "3", "4", "6"},
          v -> {
            if (!writable()) return;
            Event e = new Event();
            e.bat = Integer.parseInt(v);
            e.completed = e.bat % 2;
            e.boundary = e.bat == 4 || e.bat == 6;
            record(e);
          });
      body.addView(
          text(
              "Tap to score. Hold a run button to record zones and overthrows.", 11, MUTED, false));
      grid(
          new String[] {"Wide", "No ball", "Byes", "Leg byes", "Penalty", "Wicket"},
          v -> {
            switch (v) {
              case "Wide":
                ballDialog(0, Extra.WIDE);
                break;
              case "No ball":
                ballDialog(0, Extra.NO_BALL);
                break;
              case "Byes":
                ballDialog(0, Extra.BYE);
                break;
              case "Leg byes":
                ballDialog(0, Extra.LEG_BYE);
                break;
              case "Penalty":
                ballDialog(0, Extra.PENALTY);
                break;
              default:
                wicket();
            }
          });
      grid(
          new String[] {"Undo", "History", "Batters", "Retire hurt", "Voice", "Rain / time"},
          v -> {
            switch (v) {
              case "Undo":
                confirmReversal(
                    "Undo last delivery",
                    () -> {
                      match.undo(1);
                      persist();
                      draw();
                    });
                break;
              case "History":
                history();
                break;
              case "Batters":
                batters();
                break;
              case "Retire hurt":
                retire();
                break;
              case "Voice":
                voice();
                break;
              default:
                interrupt();
            }
          });
      if (canResumeBatter(s)) action(body, "Return retired batter", this::resumeBatter);
      action(body, "Add scorer note", this::scorerNote);
      action(body, "End innings early", this::endInningsEarly);
    } else if (s.closed && match.innings.size() == 1) action(body, "Start the chase", this::chase);
    if (match.canStartSuperChase() && !role.equals("Spectator") && !role.equals("Player"))
      action(body, "Start Super Over chase", () -> superOver(true));
    else if (match.canStartSuperOver() && !role.equals("Spectator") && !role.equals("Player"))
      action(body, "Start Super Over", () -> superOver(false));
    if (!"Live".equals(match.result())) card("Match result", match.result(), story());
    gap();
    grid(
        new String[] {"Scorecard", "Share", "Rules", "History", "Match graphs", "Match awards", "Squad availability"},
        v -> {
          if (v.equals("Scorecard")) scorecard();
          else if (v.equals("Share")) share();
          else if (v.equals("History")) history();
          else if (v.equals("Match graphs"))
            startActivity(new Intent(this, MatchAnalysisActivity.class).putExtra("match", match.id));
          else if (v.equals("Match awards"))
            startActivity(new Intent(this, MatchAwardsActivity.class).putExtra("match", match.id));
          else if (v.equals("Squad availability"))
            startActivity(new Intent(this, AttendanceActivity.class).putExtra("match", match.id)
                .putExtra("role", role));
          else
            rules(
                match.rules,
                () -> {
                  persist();
                  draw();
                });
        });
    gap();
    StringBuilder recent = new StringBuilder();
    for (int i = Math.max(0, s.commentary.size() - 8); i < s.commentary.size(); i++)
      recent.append(s.commentary.get(i)).append("\n\n");
    card(
        "Ball by ball",
        "Match commentary",
        recent.length() == 0 ? "Waiting for the first delivery." : recent.toString());
  }

  private static String fmt(double n) {
    return String.format(Locale.US, "%.2f", n);
  }

  private LinearLayout form() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(1);
    box.setPadding(dp(18), dp(5), dp(18), dp(5));
    return box;
  }

  private EditText field(LinearLayout box, String label, String value, boolean number) {
    box.addView(text(label, 12, GREEN, true));
    EditText e = new EditText(this);
    e.setText(value);
    e.setSingleLine();
    if (number) e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    box.addView(e);
    return e;
  }

  private Spinner spinner(LinearLayout box, String label, String[] values) {
    box.addView(text(label, 12, GREEN, true));
    Spinner s = new Spinner(this);
    s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
    box.addView(s, new LinearLayout.LayoutParams(-1, dp(48)));
    return s;
  }

  private CheckBox check(LinearLayout box, String label, boolean value) {
    CheckBox c = new CheckBox(this);
    c.setText(label);
    c.setChecked(value);
    box.addView(c);
    return c;
  }

  private int num(EditText e) {
    String x = e.getText().toString();
    return x.isEmpty() ? 0 : Integer.parseInt(x);
  }

  private String val(EditText e) {
    return e.getText().toString().trim();
  }

  private void dialog(String title, LinearLayout box, String positive, Runnable apply) {
    ScrollView s = new ScrollView(this);
    s.addView(box);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(s)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positive, null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    vv -> {
                      try {
                        apply.run();
                        d.dismiss();
                      } catch (Exception e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                      }
                    }));
    d.show();
  }

  private void matchSetup(Tournament cup, Fixture fixture) {
    LinearLayout box = form();
    List<Team> ts = repo.teams();
    if (ts.size() < 2) {
      message("Add teams first", "Register at least two teams and two players per team in Teams.");
      return;
    }
    String[] names = ts.stream().map(t -> t.name).toArray(String[]::new);
    Spinner a = spinner(box, "Team 1", names), b = spinner(box, "Team 2", names);
    b.setSelection(1);
    if (fixture != null) {
      for (int i = 0; i < names.length; i++) {
        if (names[i].equals(fixture.a)) a.setSelection(i);
        if (names[i].equals(fixture.b)) b.setSelection(i);
      }
    }
    Spinner toss = spinner(box, "Toss winner", new String[] {"Not recorded", "Team 1", "Team 2"});
    Spinner decision = spinner(box, "Toss decision", new String[] {"Bat first", "Bowl first"});
    Spinner batting = spinner(box, "Batting first if toss is not recorded", new String[] {"Team 1", "Team 2"});
    EditText venue = field(box, "Ground / turf", "", false),
        scorer = field(box, "Scorer", "", false),
        umpire = field(box, "Umpire", "", false);
    String[] ballTypes = labels(PlayerProfile.BALL_TYPES, false),
        matchTypes = labels(PlayerProfile.MATCH_TYPES, false),
        pitchTypes = labels(PlayerProfile.PITCH_TYPES, true);
    Spinner ballType = spinner(box, "Ball type", ballTypes),
        matchType = spinner(box, "Match type", matchTypes),
        pitchType = spinner(box, "Pitch type", pitchTypes);
    dialog(
        "Create match",
        box,
        "Set rules",
        () -> {
          Team ta = ts.get(a.getSelectedItemPosition()), tb = ts.get(b.getSelectedItemPosition());
          if (ta.id.equals(tb.id))
            throw new IllegalArgumentException("Choose two different teams.");
          if (fixture != null && !(ta.name.equals(fixture.a) && tb.name.equals(fixture.b)
              || ta.name.equals(fixture.b) && tb.name.equals(fixture.a)))
            throw new IllegalArgumentException("Choose the two scheduled fixture teams.");
          if (ta.players.size() < 2 || tb.players.size() < 2)
            throw new IllegalArgumentException("Each team needs at least two named players.");
          Team first = ta, second = tb;
          if (toss.getSelectedItemPosition() > 0) {
            boolean winnerIsFirst = toss.getSelectedItemPosition() == 1;
            boolean winnerBats = decision.getSelectedItemPosition() == 0;
            if (winnerIsFirst != winnerBats) { first = tb; second = ta; }
          } else if (batting.getSelectedItemPosition() == 1) { first = tb; second = ta; }
          Match m = new Match(first.name, second.name);
          m.tossWinner = toss.getSelectedItemPosition() == 0 ? ""
              : toss.getSelectedItemPosition() == 1 ? ta.name : tb.name;
          m.tossDecision = m.tossWinner.isEmpty() ? ""
              : decision.getSelectedItemPosition() == 0 ? "BAT" : "BOWL";
          m.venue = val(venue);
          m.scorer = val(scorer);
          m.umpire = val(umpire);
          m.ballType = PlayerProfile.BALL_TYPES[ballType.getSelectedItemPosition()];
          m.matchType = PlayerProfile.MATCH_TYPES[matchType.getSelectedItemPosition()];
          m.pitchType = pitchType.getSelectedItemPosition() == 0 ? ""
              : PlayerProfile.PITCH_TYPES[pitchType.getSelectedItemPosition() - 1];
          m.rules.players = Math.max(2, Math.min(first.players.size(), second.players.size()));
          m.rules.bowlerOvers = m.rules.overs;
          if (cup != null) m.tournament = cup.id;
          rules(
              m.rules,
              () -> {
                if (m.rules.players > Math.min(ta.players.size(), tb.players.size()))
                  throw new IllegalArgumentException("Players per side exceeds a selected team squad.");
                playingSquadSetup(m, ta, tb, cup, fixture);
              });
        });
  }

  private static String[] labels(String[] codes, boolean notRecorded) {
    List<String> out = new ArrayList<>();
    if (notRecorded) out.add("Not recorded");
    for (String code : codes) out.add(PlayerProfile.label(code));
    return out.toArray(new String[0]);
  }

  private void playingSquadSetup(Match m, Team teamOne, Team teamTwo,
      Tournament cup, Fixture fixture) {
    Team first = m.teamA.equals(teamOne.name) ? teamOne : teamTwo;
    Team second = m.teamB.equals(teamOne.name) ? teamOne : teamTwo;
    choosePlayingSquad(first, m.rules.players, selectedFirst ->
        choosePlayingSquad(second, m.rules.players, selectedSecond -> {
          org.json.JSONArray ids = new org.json.JSONArray();
          Set<String> distinct = new HashSet<>();
          for (Team team : Arrays.asList(first, second)) {
            List<String> names = team == first ? selectedFirst : selectedSecond;
            for (String name : names) {
              String id = registeredId(team.id, name);
              if (id.isEmpty()) { message("Google registration required", name + " must be added by their Google email or player code first."); return; }
              if (!distinct.add(id)) { message("Duplicate player", "The same registered player cannot play for both sides or occupy two squad places."); return; }
              ids.put(id);
            }
          }
          Toast.makeText(this, "Checking player registrations?", Toast.LENGTH_SHORT).show();
          new Thread(() -> {
            try {
              PlayerAccount.request(this, new org.json.JSONObject().put("action", "validate").put("ids", ids));
              runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                m.rosterA.clear(); m.rosterB.clear(); m.rosterA.addAll(selectedFirst); m.rosterB.addAll(selectedSecond);
                openingBatters(m, first, selectedFirst, cup, fixture);
              });
            } catch (Exception error) { runOnUiThread(() -> {
              if (!isFinishing() && !isDestroyed()) message("Player registration check", error.getMessage() == null ? "Connect to the internet and check your Google player account." : error.getMessage());
            }); }
          }, "Boundary-player-check").start();
        }));
  }

  private String registeredId(String teamId, String name) {
    for (RegisteredPlayer p : repo.list("registered-player", RegisteredPlayer.class))
      if (p.teamId.equals(teamId) && p.name.equals(name)) return p.accountId;
    return "";
  }

  private void openingBatters(Match m, Team first, List<String> selectedFirst, Tournament cup, Fixture fixture) {
    LinearLayout box = form();
    String[] names = selectedFirst.toArray(new String[0]);
    Spinner striker = spinner(box, "Opening striker", names), partner = spinner(box, "Opening partner", names);
    partner.setSelection(1);
    dialog("Opening batters ? " + first.name, box, "Start match", () -> {
      String p = (String) striker.getSelectedItem(), q = (String) partner.getSelectedItem();
      if (p.equals(q)) throw new IllegalArgumentException("Choose different openers.");
      m.innings.add(new Innings(m.teamA, m.teamB, p, q, 0));
      String oldFixtureMatchId = fixture == null ? "" : fixture.matchId;
      try { if (fixture == null) repo.save(m); else { fixture.matchId = m.id; repo.saveFixtureMatch(m, cup); } }
      catch (RuntimeException error) { if (fixture != null) fixture.matchId = oldFixtureMatchId; m.innings.clear(); throw error; }
      open(m);
    });
  }

  private void choosePlayingSquad(Team team, int size, Consumer<List<String>> chosen) {
    LinearLayout box = form();
    box.addView(text("Select exactly " + size + " players for " + team.name + ".", 14, INK, true));
    List<CheckBox> checks = new ArrayList<>();
    int eligible = 0;
    for (int i = 0; i < team.players.size(); i++) {
      boolean registered = !registeredId(team.id, team.players.get(i)).isEmpty();
      CheckBox player = check(box, team.players.get(i) + (registered ? " ? Google registered" : " ? Registration required"), registered && eligible < size);
      if (registered) eligible++;
      player.setEnabled(registered); checks.add(player);
    }
    dialog("Playing squad · " + team.name, box, "Continue", () -> {
      List<String> selected = new ArrayList<>();
      for (int i = 0; i < checks.size(); i++)
        if (checks.get(i).isChecked()) selected.add(team.players.get(i));
      if (selected.size() != size)
        throw new IllegalArgumentException("Select exactly " + size + " players.");
      chosen.accept(selected);
    });
  }

  private void rules(Rules r, Runnable saved) {
    if (!writable()) return;
    LinearLayout box = form();
    EditText bpo = field(box, "Balls per over (4, 5, 6, 8)", "" + r.ballsPerOver, true),
        overs = field(box, "Overs", "" + r.overs, true),
        players = field(box, "Players per side", "" + r.players, true),
        wide = field(box, "Wide penalty", "" + r.wide, true),
        nb = field(box, "No-ball penalty", "" + r.noBall, true),
        limit = field(box, "Bowler over limit", "" + r.bowlerOvers, true),
        pp = field(box, "Powerplay overs", "" + r.powerplay, true);
    CheckBox last = check(box, "Last batter may bat solo", r.lastMan),
        legal =
            check(box, "Final-over wides / no-balls count as deliveries", r.finalOverExtrasLegal);
    action(box, "Choose match format preset", () -> new AlertDialog.Builder(this)
        .setTitle("Match format")
        .setItems(new String[] {"T20 · 20 six-ball overs", "T10 · 10 six-ball overs",
            "Hundred-style · 20 five-ball overs", "Box cricket · 10 four-ball overs",
            "Eight-ball classic · 10 overs"}, (d, choice) -> {
          int[] balls = {6, 6, 5, 4, 8};
          int[] counts = {20, 10, 20, 10, 10};
          bpo.setText("" + balls[choice]);
          overs.setText("" + counts[choice]);
          limit.setText("" + Math.max(1, counts[choice] / 5));
          pp.setText("" + Math.min(6, Math.max(1, counts[choice] / 5)));
        }).show());
    dialog(
        "Gully-to-Pro rules",
        box,
        "Save rules",
        () -> {
          if (match != null && r == match.rules) {
            for (Innings in : match.innings)
              for (Event event : in.events)
                if (!event.reversed && event.kind == Kind.BALL)
                  throw new IllegalArgumentException(
                      "Match rules are locked after the first delivery. Correct deliveries in History, or create a new match for a different format.");
          }
          Rules next = new Rules();
          next.ballsPerOver = num(bpo);
          next.overs = num(overs);
          next.players = num(players);
          next.wide = num(wide);
          next.noBall = num(nb);
          next.bowlerOvers = num(limit);
          next.powerplay = num(pp);
          next.lastMan = last.isChecked();
          next.finalOverExtrasLegal = legal.isChecked();
          next.validate();
          if (match != null && r == match.rules && match.rosterA.size() == r.players
              && match.rosterB.size() == r.players && next.players != r.players)
            throw new IllegalArgumentException("Playing squad size is fixed for this match.");
          r.ballsPerOver = next.ballsPerOver;
          r.overs = next.overs;
          r.players = next.players;
          r.wide = next.wide;
          r.noBall = next.noBall;
          r.bowlerOvers = next.bowlerOvers;
          r.powerplay = next.powerplay;
          r.lastMan = next.lastMan;
          r.finalOverExtrasLegal = next.finalOverExtrasLegal;
          saved.run();
        });
  }

  private void ballDialog(int runs, Extra extra) {
    if (!writable()) return;
    State state = match.state();
    if (state.closed) {
      message("Innings complete", "Start the chase or correct the delivery history.");
      return;
    }
    if (state.bowler.isEmpty()) {
      bowler();
      return;
    }
    LinearLayout box = form();
    EditText
        bat =
            field(
                box,
                extra == Extra.NONE || extra == Extra.NO_BALL
                    ? "Bat runs"
                    : "Extras (excluding automatic penalty)",
                "" + runs,
                true),
        physical = field(box, "Runs completed before any overthrow", "" + (runs % 2), true),
        over = field(box, "Overthrow runs", "0", true);
    CheckBox boundary = check(box, "Boundary (four or six)", runs == 4 || runs == 6);
    EditText noBallExtras =
        extra == Extra.NO_BALL
            ? field(box, "Additional no-ball extras (not off bat)", "0", true)
            : null;
    Spinner zone =
        spinner(
            box,
            "Scoring zone",
            new String[] {
              "Unrecorded",
              "Third man",
              "Point",
              "Cover",
              "Straight off",
              "Straight on",
              "Midwicket",
              "Square leg",
              "Fine leg"
            });
    dialog(
        extra == Extra.NONE ? "Record delivery" : extra.name().replace('_', ' '),
        box,
        "Save ball",
        () -> {
          Event e = new Event();
          e.extra = extra;
          if (extra == Extra.NONE || extra == Extra.NO_BALL) e.bat = num(bat);
          else e.extras = num(bat);
          if (noBallExtras != null) e.extras = num(noBallExtras);
          e.completed = num(physical);
          e.overthrow = num(over);
          e.boundary = boundary.isChecked();
          e.zone = zone.getSelectedItemPosition() - 1;
          record(e);
        });
  }

  private void record(Event e) {
    match.add(e);
    persist();
    if (pa && audioReady) {
      State s = match.state();
      if ((e.boundary || e.dismissal != Dismissal.NONE || s.legal % match.rules.ballsPerOver == 0)
          && !s.commentary.isEmpty())
        tts.speak(s.commentary.get(s.commentary.size() - 1), TextToSpeech.QUEUE_ADD, null, e.id);
      if (!e.incoming.isEmpty())
        tts.speak(
            "Welcome to the crease, " + e.incoming,
            TextToSpeech.QUEUE_ADD,
            null,
            e.id + "-incoming");
      if (e.kind == Kind.BOWLER)
        tts.speak(
            "New over. " + e.player + " to bowl. " + s.runs + " for " + s.wickets,
            TextToSpeech.QUEUE_ADD,
            null,
            e.id + "-over");
    }
    draw();
  }

  private String[] squad(String team) {
    if (match != null) {
      java.util.List<String> saved = team.equals(match.teamA) ? match.rosterA : match.rosterB;
      if (saved != null && !saved.isEmpty()) return saved.toArray(new String[0]);
    }
    for (Team t : repo.teams()) if (t.name.equals(team)) return t.players.toArray(new String[0]);
    return new String[] {"Player"};
  }

  private String[] available() {
    State s = match.state();
    List<String> ps = new ArrayList<>();
    for (String p : squad(match.current().battingTeam)) {
      Batting b = s.batting.get(p);
      if (!p.equals(s.striker) && !p.equals(s.nonStriker)
          && (b == null || (!b.out && !b.retired))) ps.add(p);
    }
    if (ps.isEmpty()) ps.add("");
    return ps.toArray(new String[0]);
  }

  private void bowler() {
    if (!writable()) return;
    LinearLayout box = form();
    Spinner p = spinner(box, "Bowler", squad(match.current().bowlingTeam));
    Spinner style =
        spinner(
            box,
            "Bowling style",
            new String[] {
              "Right-arm pace", "Left-arm pace", "Off spin", "Leg spin", "Left-arm orthodox"
            });
    State before = match.state();
    boolean midOver = before.legal % match.rules.ballsPerOver != 0 && !before.bowler.isEmpty();
    CheckBox replacement = midOver
        ? check(box, "Replace current bowler mid-over due to injury or absence", false) : null;
    dialog(
        "Select bowler",
        box,
        midOver ? "Change bowler" : "Begin over",
        () -> {
          String name = (String) p.getSelectedItem();
          State s = match.state();
          if (s.legal % match.rules.ballsPerOver != 0 && !s.bowler.isEmpty()
              && (replacement == null || !replacement.isChecked()))
            throw new IllegalArgumentException(
                "Confirm the mid-over replacement, or finish the current over.");
          if (name.equals(s.bowler))
            throw new IllegalArgumentException("Choose a different bowler.");
          if (s.bowl(name).balls >= match.rules.bowlerOvers * match.rules.ballsPerOver)
            throw new IllegalArgumentException("This bowler has reached the over limit.");
          Event e = new Event();
          e.kind = Kind.BOWLER;
          e.player = name;
          e.note = (midOver ? "Mid-over replacement · " : "") + style.getSelectedItem();
          record(e);
        });
  }

  private void batters() {
    if (!writable()) return;
    LinearLayout box = form();
    Spinner p = spinner(box, "Striker", squad(match.current().battingTeam)),
        q = spinner(box, "Non-striker", squad(match.current().battingTeam));
    if (q.getCount() > 1) q.setSelection(1);
    dialog(
        "Select batter ends",
        box,
        "Save ends",
        () -> {
          if (p.getSelectedItem().equals(q.getSelectedItem()))
            throw new IllegalArgumentException("Choose different batters.");
          Event e = new Event();
          e.kind = Kind.BATTERS;
          e.player = (String) p.getSelectedItem();
          e.partner = (String) q.getSelectedItem();
          record(e);
        });
  }

  private void wicket() {
    if (!writable()) return;
    LinearLayout box = form();
    Spinner
        type =
            spinner(
                box,
                "Dismissal",
                new String[] {
                  "Bowled", "Caught", "LBW", "Run out", "Stumped", "Hit wicket", "Obstructing"
                }),
        incoming = spinner(box, "Incoming batter", available());
    Spinner delivery =
        spinner(box, "Delivery call", new String[] {"Legal delivery", "Wide", "No ball", "Bye", "Leg bye"});
    CheckBox non = check(box, "Non-striker dismissed", false),
        cross = check(box, "Crossed on incomplete run", false);
    EditText completed = field(box, "Runs completed", "0", true);
    String[] fielders = squad(match.current().bowlingTeam);
    String[] options = new String[fielders.length + 1];
    options[0] = "Not recorded";
    System.arraycopy(fielders, 0, options, 1, fielders.length);
    Spinner fielder = spinner(box, "Fielder", options);
    dialog(
        "Record wicket",
        box,
        "Save wicket",
        () -> {
          Event e = new Event();
          e.dismissal = Dismissal.values()[type.getSelectedItemPosition() + 1];
          e.incoming = (String) incoming.getSelectedItem();
          e.nonStrikerOut = non.isChecked();
          e.crossed = cross.isChecked();
          e.bat = num(completed);
          e.completed = num(completed);
          e.extra =
              delivery.getSelectedItemPosition() == 1
                  ? Extra.WIDE
                  : delivery.getSelectedItemPosition() == 2 ? Extra.NO_BALL
                  : delivery.getSelectedItemPosition() == 3 ? Extra.BYE
                  : delivery.getSelectedItemPosition() == 4 ? Extra.LEG_BYE : Extra.NONE;
          if (e.extra == Extra.WIDE || e.extra == Extra.BYE || e.extra == Extra.LEG_BYE) {
            e.extras = e.bat;
            e.bat = 0;
          }
          e.fielder = fielder.getSelectedItemPosition() == 0 ? "" : (String) fielder.getSelectedItem();
          if ((e.dismissal == Dismissal.CAUGHT || e.dismissal == Dismissal.STUMPED)
              && e.fielder.isEmpty())
            throw new IllegalArgumentException("Select the fielder for a catch or stumping.");
          if (e.dismissal == Dismissal.CAUGHT && (e.extra == Extra.WIDE || e.extra == Extra.NO_BALL))
            throw new IllegalArgumentException("A caught dismissal is not valid from a wide or no-ball.");
          if (e.dismissal == Dismissal.STUMPED && e.extra == Extra.NO_BALL)
            throw new IllegalArgumentException("A batter cannot be stumped from a no-ball.");
          record(e);
        });
  }

  private void retire() {
    if (!writable()) return;
    LinearLayout box = form();
    Spinner
        p =
            spinner(
                box,
                "Retiring batter",
                new String[] {match.state().striker, match.state().nonStriker}),
        incoming = spinner(box, "Replacement", available());
    dialog(
        "Retired hurt",
        box,
        "Substitute",
        () -> {
          Event e = new Event();
          e.kind = Kind.RETIRE;
          e.player = (String) p.getSelectedItem();
          e.incoming = (String) incoming.getSelectedItem();
          record(e);
        });
  }

  private boolean canResumeBatter(State state) {
    if (!state.striker.isEmpty() && !state.nonStriker.isEmpty()) return false;
    for (String player : squad(match.current().battingTeam)) {
      Batting batting = state.batting.get(player);
      if (batting != null && batting.retired && !batting.out) return true;
    }
    return false;
  }

  private void resumeBatter() {
    if (!writable()) return;
    State state = match.state();
    if (state.closed || !canResumeBatter(state))
      throw new IllegalArgumentException("No retired batter can return to a vacant end.");
    List<String> retired = new ArrayList<>();
    for (String player : squad(match.current().battingTeam)) {
      Batting batting = state.batting.get(player);
      if (batting != null && batting.retired && !batting.out) retired.add(player);
    }
    List<String> ends = new ArrayList<>();
    if (state.striker.isEmpty()) ends.add("Striker");
    if (state.nonStriker.isEmpty()) ends.add("Non-striker");
    LinearLayout box = form();
    Spinner player = spinner(box, "Returning batter", retired.toArray(new String[0]));
    Spinner end = spinner(box, "Vacant end", ends.toArray(new String[0]));
    dialog("Return retired batter", box, "Return to crease", () -> {
      Event event = new Event();
      event.kind = Kind.RESUME;
      event.player = (String) player.getSelectedItem();
      event.nonStrikerOut = end.getSelectedItem().equals("Non-striker");
      record(event);
    });
  }

  private void endInningsEarly() {
    if (!writable()) return;
    if (match.state().closed) throw new IllegalArgumentException("This innings has already finished.");
    new AlertDialog.Builder(this)
        .setTitle("End innings early?")
        .setMessage("This will close the current innings at " + match.state().runs + "/"
            + match.state().wickets + ". You can reverse it from History or Undo.")
        .setNegativeButton("Keep scoring", null)
        .setPositiveButton("End innings", (dialog, which) -> guard(() -> {
          if (!writable()) return;
          Event event = new Event();
          event.kind = Kind.END_INNINGS;
          record(event);
        }))
        .show();
  }

  private void scorerNote() {
    if (!writable()) return;
    LinearLayout box = form();
    EditText note = field(box, "Match note", "", false);
    note.setSingleLine(false);
    note.setMinLines(3);
    dialog("Scorer note", box, "Save note", () -> {
      Event event = new Event();
      event.kind = Kind.NOTE;
      event.note = val(note);
      record(event);
    });
  }

  private void chase() {
    if (!writable()) return;
    LinearLayout box = form();
    String[] ps = squad(match.teamB);
    Spinner a = spinner(box, "Opening striker", ps), b = spinner(box, "Opening partner", ps);
    if (ps.length > 1) b.setSelection(1);
    dialog(
        "Start second innings",
        box,
        "Start chase",
        () -> {
          if (a.getSelectedItem().equals(b.getSelectedItem()))
            throw new IllegalArgumentException("Choose different openers.");
          match.startChase((String) a.getSelectedItem(), (String) b.getSelectedItem());
          persist();
          draw();
        });
  }

  private void superOver(boolean chase) {
    if (!writable()) return;
    LinearLayout box = form();
    Spinner first = chase ? null : spinner(box, "Batting first",
        new String[] {match.teamA, match.teamB});
    String batting = chase ? match.innings.get(match.innings.size() - 1).bowlingTeam
        : match.teamA;
    Spinner a = spinner(box, "Opening striker", squad(batting));
    Spinner b = spinner(box, "Opening partner", squad(batting));
    if (b.getCount() > 1) b.setSelection(1);
    if (first != null) first.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(android.widget.AdapterView<?> parent,
          android.view.View view, int position, long id) {
        String[] players = squad((String) first.getSelectedItem());
        a.setAdapter(new android.widget.ArrayAdapter<>(MainActivity.this,
            android.R.layout.simple_spinner_dropdown_item, players));
        b.setAdapter(new android.widget.ArrayAdapter<>(MainActivity.this,
            android.R.layout.simple_spinner_dropdown_item, players));
        if (players.length > 1) b.setSelection(1);
      }
      @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    });
    box.addView(text("One over per team, two wickets maximum. Another Super Over can break a tie.",
        12, MUTED, false));
    dialog(chase ? "Super Over chase" : "Start Super Over", box, "Start innings", () -> {
      if (chase) match.startSuperChase((String) a.getSelectedItem(), (String) b.getSelectedItem());
      else match.startSuperOver((String) first.getSelectedItem(),
          (String) a.getSelectedItem(), (String) b.getSelectedItem());
      persist();
      draw();
    });
  }

  private void confirmReversal(String title, Runnable r) {
    if (!writable()) return;
    if (pending != null) pending.dismiss();
    TextView msg =
        text("Applying in 3 seconds. Tap Cancel to keep the current decision.", 15, INK, false);
    msg.setPadding(dp(22), dp(15), dp(22), dp(15));
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(msg)
            .setNegativeButton("Cancel", null)
            .create();
    pending = d;
    d.show();
    if (audioReady)
      tts.speak(
          title + ". Cancel within three seconds.", TextToSpeech.QUEUE_FLUSH, null, "reversal");
    handler.postDelayed(
        () -> {
          if (d.isShowing()) {
            d.dismiss();
            guard(r);
          }
        },
        3000);
  }

  private void history() {
    if (match.innings.size() == 1) {
      historyInnings(0);
      return;
    }
    String[] inningsLabels = new String[match.innings.size()];
    for (int i = 0; i < inningsLabels.length; i++) {
      Innings in = match.innings.get(i);
      inningsLabels[i] = (i < 2 ? "MAIN MATCH | " : "SUPER OVER " + (i / 2) + " | ")
          + in.battingTeam + (i % 2 == 1 ? " | chase" : " | first innings");
    }
    new AlertDialog.Builder(this)
        .setTitle("Choose innings to review")
        .setItems(inningsLabels, (d, index) -> historyInnings(index))
        .show();
  }

  private void historyInnings(int inningsIndex) {
    List<Event> events = match.innings.get(inningsIndex).events;
    String[] lines = new String[events.size()];
    for (int i = 0; i < lines.length; i++) lines[i] = (i + 1) + " · " + events.get(i).label();
    new AlertDialog.Builder(this)
        .setTitle("Full delivery journal")
        .setItems(lines, (d, index) -> editEvent(inningsIndex, index))
        .setNegativeButton("Close", null)
        .show();
  }

  private void editEvent(int index) {
    editEvent(match.activeIndex(), index);
  }

  private void editEvent(int inningsIndex, int index) {
    if (!writable()) return;
    Event e = match.innings.get(inningsIndex).events.get(index);
    if (e.kind != Kind.BALL) {
      LinearLayout controls = form();
      CheckBox reversed = check(controls, "Remove this action from the score", e.reversed);
      dialog("Correct " + e.label(), controls, "Apply correction", () ->
          confirmReversal("Recalculate from action " + (index + 1), () -> {
            e.reversed = reversed.isChecked();
            persist();
            draw();
          }));
      return;
    }
    LinearLayout box = form();
    EditText runs = field(box, "Bat runs", "" + e.bat, true),
        extras = field(box, "Additional extras", "" + e.extras, true),
        completed = field(box, "Completed runs", "" + e.completed, true),
        over = field(box, "Overthrows", "" + e.overthrow, true);
    Spinner ex =
        spinner(
            box,
            "Extra type",
            Arrays.stream(Extra.values()).map(Enum::name).toArray(String[]::new));
    ex.setSelection(e.extra.ordinal());
    Spinner dismissal = spinner(box, "Dismissal",
        Arrays.stream(Dismissal.values()).map(v -> v.name().replace('_', ' ')).toArray(String[]::new));
    dismissal.setSelection(e.dismissal.ordinal());
    Spinner dismissedEnd = spinner(box, "Dismissed end", new String[] {"Striker", "Non-striker"});
    dismissedEnd.setSelection(e.nonStrikerOut ? 1 : 0);
    EditText incoming = field(box, "Incoming batter (blank if none)", e.incoming, false);
    EditText fielder = field(box, "Fielder", e.fielder, false);
    CheckBox crossed = check(box, "Crossed before the wicket", e.crossed),
        boundary = check(box, "Boundary", e.boundary),
        reversed = check(box, "Decision reversed / remove entire event", e.reversed);
    dialog(
        "Correct event " + (index + 1),
        box,
        "Apply correction",
        () -> {
          int bat = num(runs), extraRuns = num(extras), phys = num(completed), overRuns = num(over);
          confirmReversal(
              "Recalculate match from event " + (index + 1),
              () -> {
                e.bat = bat;
                e.extras = extraRuns;
                e.completed = phys;
                e.overthrow = overRuns;
                e.extra = Extra.values()[ex.getSelectedItemPosition()];
                e.dismissal = Dismissal.values()[dismissal.getSelectedItemPosition()];
                e.nonStrikerOut = dismissedEnd.getSelectedItemPosition() == 1;
                e.incoming = val(incoming);
                e.fielder = val(fielder);
                e.crossed = crossed.isChecked();
                e.boundary = boundary.isChecked();
                e.reversed = reversed.isChecked();
                e.note = "Corrected event " + (index + 1);
                match.revision++;
                persist();
                draw();
              });
        });
  }

  private void interrupt() {
    if (!writable()) return;
    LinearLayout box = form();
    State s = match.state();
    EditText remaining =
        field(
            box,
            "Revised remaining legal balls",
            "" + Math.max(1, (s.maxBalls - s.legal) / 2),
            true);
    box.addView(
        text(
            "Local resource estimate; this is not the licensed official DLS method.",
            12,
            MUTED,
            false));
    dialog(
        "Rain / turf time adjustment",
        box,
        "Calculate target",
        () -> {
          int total = s.legal + num(remaining), target = Cricket.revisedTarget(s, total);
          message(
              "Revised target",
              target == 0
                  ? "First innings shortened to " + total + " legal balls."
                  : "Revised target: " + target + " from " + num(remaining) + " remaining balls.");
          Event e = new Event();
          e.kind = Kind.INTERRUPTION;
          e.revisedBalls = total;
          e.revisedTarget = target;
          record(e);
        });
  }

  private void scorecard() {
    StringBuilder result = new StringBuilder();
    for (Innings in : match.innings) {
      int inningsIndex = match.innings.indexOf(in);
      State s = match.stateAt(inningsIndex);
      result
          .append(inningsIndex < 2 ? "MAIN MATCH | " : "SUPER OVER " + (inningsIndex / 2) + " | ")
          .append(in.battingTeam)
          .append("  ")
          .append(s.runs)
          .append('/')
          .append(s.wickets)
          .append(" (")
          .append(s.overs(match.rules))
          .append(")\n\n");
      for (Map.Entry<String, Batting> entry : s.batting.entrySet()) {
        Batting b = entry.getValue();
        result
            .append(entry.getKey())
            .append(b.out ? "" : " *")
            .append("  ")
            .append(b.runs)
            .append(" (")
            .append(b.balls)
            .append(")  4s:")
            .append(b.fours)
            .append("  6s:")
            .append(b.sixes)
            .append("  SR ")
            .append(fmt(b.balls == 0 ? 0 : b.runs * 100.0 / b.balls))
            .append('\n');
      }
      result.append("\nBowling\n");
      for (Map.Entry<String, Bowling> entry : s.bowling.entrySet()) {
        Bowling b = entry.getValue();
        result
            .append(entry.getKey())
            .append("  ")
            .append(b.balls / match.rules.ballsPerOver)
            .append('.')
            .append(b.balls % match.rules.ballsPerOver)
            .append("  ")
            .append(b.wickets)
            .append('/')
            .append(b.runs)
            .append(" Econ ")
            .append(fmt(b.balls == 0 ? 0 : b.runs * (double) match.rules.ballsPerOver / b.balls))
            .append('\n');
      }
      result
          .append("\nExtras: ")
          .append(s.extras)
          .append("\nFall of wickets\n")
          .append(String.join("\n", s.falls))
          .append("\n\n");
    }
    message("Full scorecard", result.toString());
  }

  private String story() {
    State s = match.state();
    String top = "";
    int best = -1;
    for (Innings in : match.innings)
      for (Map.Entry<String, Batting> b : match.stateAt(match.innings.indexOf(in)).batting.entrySet())
        if (b.getValue().runs > best) {
          best = b.getValue().runs;
          top = b.getKey();
        }
    return match.result()
        + " at "
        + (match.venue.isEmpty() ? "the ground" : match.venue)
        + ". "
        + top
        + " led the batting with "
        + Math.max(0, best)
        + " runs. "
        + s.wickets
        + " wickets fell in the chase, which ended on "
        + s.runs
        + " after "
        + s.overs(match.rules)
        + " overs.";
  }

  private void share() {
    Intent i = new Intent(Intent.ACTION_SEND);
    i.setType("text/plain");
    State s = match.state();
    i.putExtra(
        Intent.EXTRA_TEXT,
        match.name
            + "\n"
            + match.current().battingTeam
            + " "
            + s.runs
            + "/"
            + s.wickets
            + " ("
            + s.overs(match.rules)
            + ")\n"
            + match.result()
            + "\nhttps://boundary-cricket-tournament.vercel.app/?match="
            + match.id);
    startActivity(Intent.createChooser(i, "Share match"));
  }

  private void cups() {
    heading("Competition desk", "Tournaments", "League, group stage and knockout fixtures.");
    action(body, "Create tournament", this::newCup);
    gap();
    for (Tournament cup : repo.tournaments()) {
      LinearLayout c =
          card(
              cup.format.name(),
              cup.name,
              cup.teams.size() + " registered teams · " + cup.fixtures.size() + " fixtures");
      action(c, "Registrations & schedule", () -> cupDetail(cup));
    }
  }

  private void newCup() {
    if (!writable()) return;
    LinearLayout box = form();
    EditText name = field(box, "Tournament name", "", false);
    Spinner format = spinner(box, "Format", new String[] {"LEAGUE", "KNOCKOUT", "GROUPS"});
    dialog(
        "New tournament",
        box,
        "Create",
        () -> {
          if (val(name).isEmpty()) throw new IllegalArgumentException("Enter a tournament name.");
          repo.save(new Tournament(val(name), Format.values()[format.getSelectedItemPosition()]));
          draw();
        });
  }

  private void cupDetail(Tournament cup) {
    String[] options = {
      "Register teams", "Generate fixtures", "Fixtures / start match", "Points table",
      "Batting leaderboard", "Bowling leaderboard", "NRR scenario planner",
      "Fielding leaderboard", "All-round leaderboard"
    };
    new AlertDialog.Builder(this)
        .setTitle(cup.name)
        .setItems(
            options,
            (d, w) ->
                guard(
                    () -> {
                      if (w == 0) {
                        if (!writable()) return;
                        List<Team> ts = repo.teams();
                        String[] ns = ts.stream().map(t -> t.name).toArray(String[]::new);
                        boolean[] selected = new boolean[ns.length];
                        for (int i = 0; i < ns.length; i++) selected[i] = cup.teams.contains(ns[i]);
                        new AlertDialog.Builder(this)
                            .setTitle("Registered teams")
                            .setMultiChoiceItems(
                                ns, selected, (x, index, on) -> selected[index] = on)
                            .setPositiveButton(
                                "Save",
                                (x, y) -> {
                                  if (!cup.fixtures.isEmpty()) {
                                    message(
                                        "Schedule already created",
                                        "Team registrations are locked after generating fixtures.");
                                    return;
                                  }
                                  cup.teams.clear();
                                  for (int i = 0; i < ns.length; i++)
                                    if (selected[i]) cup.teams.add(ns[i]);
                                  repo.save(cup);
                                  draw();
                                })
                            .show();
                      } else if (w == 1) {
                        if (!writable()) return;
                        cup.generate();
                        repo.save(cup);
                        draw();
                      } else if (w == 2) {
                        String[] fixtures =
                            cup.fixtures.stream()
                                .map(f -> f.round + " · " + f.a + " v " + f.b)
                                .toArray(String[]::new);
                        new AlertDialog.Builder(this)
                            .setTitle("Fixtures")
                            .setItems(
                                fixtures,
                                (x, i) -> {
                                  Fixture f = cup.fixtures.get(i);
                                  for (Match m : repo.matches())
                                    if (m.id.equals(f.matchId)) {
                                      open(m);
                                      return;
                                    }
                                  if (!writable()) return;
                                  if (f.a.equals("BYE") || f.b.equals("BYE")) {
                                    message("Bye", "This team advances without a match.");
                                    return;
                                  }
                                  matchSetup(cup, f);
                                })
                            .show();
                      } else if (w == 4 || w == 5) {
                        showLeaderboard(cup, w == 5);
                      } else if (w == 6) {
                        startActivity(new Intent(this, NrrPlannerActivity.class)
                            .putExtra("tournament", cup.id));
                      } else if (w == 7 || w == 8) {
                        startActivity(new Intent(this, PlayerDirectoryActivity.class)
                            .putExtra("tournament", cup.id).putExtra("category", w == 7 ? 2 : 3));
                      } else {
                        StringBuilder table = new StringBuilder("Team · P · W · L · Pts · NRR\n\n");
                        for (Cricket.Row r : Cricket.standings(cup, repo.matches()))
                          table
                              .append(r.team)
                              .append(" · ")
                              .append(r.played)
                              .append(" · ")
                              .append(r.won)
                              .append(" · ")
                              .append(r.lost)
                              .append(" · ")
                              .append(r.points)
                              .append(" · ")
                              .append(fmt(r.nrr(6)))
                              .append('\n');
                        message("Points table", table.toString());
                      }
                    }))
        .show();
  }

  private void showLeaderboard(Tournament cup, boolean bowling) {
    List<Leaderboards.Player> rows = bowling
        ? Leaderboards.bowling(cup.id, repo.matches())
        : Leaderboards.batting(cup.id, repo.matches());
    String title = cup.name + (bowling ? " · Bowling" : " · Batting");
    StringBuilder report = new StringBuilder(title)
        .append("\nIncludes live matches; corrections update totals.\n")
        .append(bowling
            ? "Rank · Player / team · Wickets · Runs · Legal balls · Economy\n\n"
            : "Rank · Player / team · Runs · Balls · 4s · 6s · Strike rate\n\n");
    for (int index = 0; index < rows.size(); index++) {
      Leaderboards.Player p = rows.get(index);
      report.append(index + 1).append(". ").append(p.name).append(" / ")
          .append(p.team).append("\n");
      if (bowling) {
        report.append(p.wickets).append(" wkts · ").append(p.conceded)
            .append(" runs · ").append(p.deliveries).append(" balls · Econ ")
            .append(fmt(p.economy()));
      } else {
        report.append(p.runs).append(" runs · ").append(p.balls)
            .append(" balls · ").append(p.fours).append(" fours · ")
            .append(p.sixes).append(" sixes · SR ").append(fmt(p.strikeRate()));
      }
      report.append("\n\n");
    }
    if (rows.isEmpty()) report.append("No recorded performances yet. Score a tournament match to populate this table.");
    ScrollView scroll = new ScrollView(this);
    TextView content = text(report.toString(), 14, INK, false);
    content.setPadding(dp(20), dp(12), dp(20), dp(12));
    content.setTextIsSelectable(true);
    scroll.addView(content);
    new AlertDialog.Builder(this).setTitle(title).setView(scroll)
        .setPositiveButton("Close", null)
        .setNeutralButton("Share", (dialog, which) -> guard(() -> {
          Intent share = new Intent(Intent.ACTION_SEND);
          share.setType("text/plain");
          share.putExtra(Intent.EXTRA_TEXT, report.toString());
          startActivity(Intent.createChooser(share, "Share leaderboard"));
        })).show();
  }

  private void teams() {
    heading("Squads & player careers", "Teams", "Your teams, players and recorded career figures.");
    action(
        body,
        "Register a team",
        () -> {
          if (!writable()) return;
          LinearLayout box = form();
          EditText name = field(box, "Team name", "", false);
          box.addView(text("Save your team, then add players using their Google emails or player codes.", 14, MUTED, false));
          dialog(
              "Register team",
              box,
              "Save team",
              () -> {
                if (val(name).isEmpty()) throw new IllegalArgumentException("Enter a team name.");
                for (Team t : repo.teams())
                  if (t.name.equalsIgnoreCase(val(name)))
                    throw new IllegalArgumentException("That team already exists.");
                Team t = new Team(val(name));
                repo.save(t);
                draw();
              });
        });
    gap();
    for (Team t : repo.teams()) {
      LinearLayout c =
          card("SQUAD · " + t.players.size() + " players", t.name, String.join(" · ", t.players));
      action(c, "Add registered player", () -> {
        if (!writable()) return;
        getPreferences(0).edit().putString("addingPlayerTeam", t.id).apply();
        startActivityForResult(new Intent(this, TeamInviteActivity.class).putExtra("teamId", t.id).putExtra("teamName", t.name), 88);
      });
      action(c, "Players & career statistics", () -> players(t));
    }
  }

  private void players(Team t) {
    List<String> opts = new ArrayList<>(t.players);
    opts.add("+ Add player");
    new AlertDialog.Builder(this)
        .setTitle(t.name)
        .setItems(
            opts.toArray(new String[0]),
            (d, w) -> {
              if (w == t.players.size()) {
                if (!writable()) return;
                getPreferences(0).edit().putString("addingPlayerTeam", t.id).apply();
                startActivityForResult(new Intent(this, TeamInviteActivity.class).putExtra("teamId", t.id).putExtra("teamName", t.name), 88);
              } else playerOptions(t.name, t.players.get(w));
            })
        .show();
  }

  private void playerOptions(String team, String player) {
    ProfileStore me = new ProfileStore(this);
    boolean mine = me.claims().contains(com.example.madfinalproject.domain.Career.key(team, player));
    String[] options = {"Career profile", "Batting hand & bowling style", mine ? "This is not me" : "This is me"};
    new AlertDialog.Builder(this).setTitle(player + " · " + team).setItems(options, (d, w) -> guard(() -> {
      if (w == 0) career(player, team);
      else if (w == 1) playerStyles(team, player);
      else { me.claim(team, player, !mine); draw(); }
    })).show();
  }

  /** Batting hand and bowling style power the right/left-hand and pace/spin splits. */
  private void playerStyles(String team, String player) {
    PlayerProfile profile = ProfileStore.find(repo, team, player);
    LinearLayout box = form();
    String[] hands = {"Not recorded", "Right-hand bat (RHB)", "Left-hand bat (LHB)"};
    Spinner hand = spinner(box, "Batting hand", hands);
    hand.setSelection(profile.battingHand.equals("RHB") ? 1 : profile.battingHand.equals("LHB") ? 2 : 0);
    String[] styles = new String[PlayerProfile.BOWLING_STYLES.length + 1];
    styles[0] = "Does not bowl / not recorded";
    System.arraycopy(PlayerProfile.BOWLING_STYLES, 0, styles, 1, PlayerProfile.BOWLING_STYLES.length);
    Spinner style = spinner(box, "Bowling style", styles);
    style.setSelection(Math.max(0, Arrays.asList(styles).indexOf(profile.bowlingStyle)));
    dialog(player, box, "Save styles", () -> {
      profile.battingHand = hand.getSelectedItemPosition() == 1 ? "RHB" : hand.getSelectedItemPosition() == 2 ? "LHB" : "";
      profile.bowlingStyle = style.getSelectedItemPosition() == 0 ? "" : styles[style.getSelectedItemPosition()];
      ProfileStore.save(repo, profile);
    });
  }

  private void career(String player, String team) {
    startActivity(new Intent(this, PlayerProfileActivity.class)
        .putExtra("player", player).putExtra("team", team));
  }

  private void openPolicy(String path) {
    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PlayerAccount.ORIGIN + path)));
  }

  private void store() {
    storeView = new StoreView(this, pal);
    body.addView(storeView, new LinearLayout.LayoutParams(-1, -2));
  }

  private void refreshPage(Runnable done) {
    updates.refresh();
    retryDeviceRevocations();
    if (page.equals("Store") && storeView != null) storeView.refresh(done);
    else { draw(); done.run(); }
  }

  /** Umpire tools: hands-free voice commands and the square-leg stump cam. */
  private void tools() {
    heading(
        "Umpire workspace",
        "Umpire tools",
        "Hands-free scoring commands and a square-leg review camera.");
    LinearLayout v =
        card(
            "Hands-free scorer",
            "Voice commands",
            "Speak scoring and correction commands. Each recognized command is confirmed before"
                + " execution.");
    action(v, "Speak a command", this::voice);
    LinearLayout cam =
        card(
            "Square-leg review",
            "Stump cam",
            "Record short rolling segments and retain the latest action for slow playback.");
    action(cam, "Open stump cam", () -> startActivity(new Intent(this, StumpCamActivity.class)));
  }

  private void recoverCloud() {
    if (!writable()) return;
    LinearLayout box = form();
    EditText code = field(box, "Private recovery code (match ID:authority)", "", false);
    if (match != null) {
      String key = getSharedPreferences("publication", 0).getString("key-" + match.id, "");
      if (!key.isEmpty()) code.setText(match.id + ":" + key);
    }
    dialog(
        "Recover complete match",
        box,
        "Fetch encrypted journal",
        () -> {
          String[] parts = val(code).split(":", 2);
          if (parts.length != 2 || !parts[0].matches("[a-fA-F0-9-]{36}") || parts[1].length() < 50)
            throw new IllegalArgumentException("Enter a complete private recovery code.");
          String id = parts[0], authority = parts[1];
          new Thread(
                  () -> {
                    java.net.HttpURLConnection connection = null;
                    try {
                      connection =
                          (java.net.HttpURLConnection)
                              new java.net.URL(
                                      "https://boundary-cricket-tournament.vercel.app/api/matches?id="
                                          + id
                                          + "&journal=1")
                                  .openConnection();
                      connection.setRequestProperty("Authorization", "Bearer " + authority);
                      connection.setConnectTimeout(12000);
                      connection.setReadTimeout(12000);
                      if (connection.getResponseCode() != 200)
                        throw new java.io.IOException(
                            "The server could not authorize this recovery code.");
                      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                      try (java.io.InputStream in = connection.getInputStream()) {
                        byte[] data = new byte[8192];
                        int n;
                        while ((n = in.read(data)) > 0) bytes.write(data, 0, n);
                      }
                      org.json.JSONObject response =
                          new org.json.JSONObject(bytes.toString("UTF-8"));
                      Match restored =
                          com.example.madfinalproject.data.JournalCodec.decode(
                              response.getString("journal"), authority);
                      if (!restored.id.equals(id))
                        throw new java.io.IOException("Recovery journal identity does not match.");
                      runOnUiThread(
                          () -> {
                            if (isFinishing() || isDestroyed()) return;
                            for (Match local : repo.matches())
                              if (local.id.equals(id) && local.revision > restored.revision) {
                                message(
                                    "Newer local edits exist",
                                    "Your device has a newer match revision. Publish pending"
                                        + " changes before recovering.");
                                return;
                              }
                            new AlertDialog.Builder(this)
                                .setTitle("Restore complete match?")
                                .setMessage(
                                    restored.name
                                        + "\nRevision "
                                        + restored.revision
                                        + " · All innings, rules and delivery events will be"
                                        + " restored.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                    "Restore",
                                    (d, w) ->
                                        guard(
                                            () -> {
                                              if (!getSharedPreferences("publication", 0)
                                                  .edit()
                                                  .putString("key-" + id, authority)
                                                  .commit())
                                                throw new IllegalStateException(
                                                    "Could not save recovery authority");
                                              android.database.sqlite.SQLiteDatabase database =
                                                  repo.getWritableDatabase();
                                              database.beginTransaction();
                                              try {
                                                for (String team :
                                                    new String[] {restored.teamA, restored.teamB}) {
                                                  boolean exists = false;
                                                  for (Team local : repo.teams())
                                                    if (local.name.equals(team)) exists = true;
                                                  if (!exists) {
                                                    Team recovered = new Team(team);
                                                    java.util.List<String> players =
                                                        team.equals(restored.teamA)
                                                            ? restored.rosterA
                                                            : restored.rosterB;
                                                    if (players != null)
                                                      recovered.players.addAll(players);
                                                    repo.save(recovered);
                                                  }
                                                }
                                                repo.save(restored);
                                                database.setTransactionSuccessful();
                                              } finally {
                                                database.endTransaction();
                                              }
                                              open(restored);
                                            }))
                                .show();
                          });
                    } catch (Exception e) {
                      runOnUiThread(
                          () -> {
                            if (!isFinishing() && !isDestroyed())
                              message("Recovery unavailable", e.getMessage());
                          });
                    } finally {
                      if (connection != null) connection.disconnect();
                    }
                  },
                  "cloud-recovery")
              .start();
        });
  }

  private String tactic() {
    if (match == null) return "Record batter scoring zones to get a fielding suggestion.";
    Batting b = batterHistory();
    int best = 0;
    for (int i = 1; i < 8; i++) if (b.zones[i] > b.zones[best]) best = i;
    if (Arrays.stream(b.zones).sum() == 0)
      return "No scoring-zone history yet. Start with a balanced field and attack the stumps.";
    String[] zones = {
      "third man",
      "point",
      "cover",
      "straight off",
      "straight on",
      "midwicket",
      "square leg",
      "fine leg"
    };
    return "Protect "
        + zones[best]
        + ", the batter’s strongest recorded zone, and bowl away from that scoring arc.";
  }

  private Batting batterHistory() {
    Batting combined = new Batting();
    if (match == null) return combined;
    String player = match.state().striker;
    for (Match m : repo.matches())
      for (Innings in : m.innings)
        if (in.battingTeam.equals(match.current().battingTeam)) {
          Batting b = in.state(m.rules).batting.get(player);
          if (b != null) {
            for (int i = 0; i < 8; i++) combined.zones[i] += b.zones[i];
            combined.runs += b.runs;
            combined.balls += b.balls;
          }
        }
    return combined;
  }

  private void voice() {
    if (match == null) {
      message("Select a match", "Open a match before using voice scoring.");
      return;
    }
    if (!writable()) return;
    String official = role.equals("Umpire") ? match.umpire : match.scorer;
    startActivityForResult(new Intent(this, VoiceActivity.class)
        .putExtra("profile", match.id + "-" + role + "-" + official), 77);
  }

  @Override
  public void onRequestPermissionsResult(int req, String[] ps, int[] results) {
    super.onRequestPermissionsResult(req, ps, results);
    if (req == 11 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) voice();
  }

  @Override
  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (req == 88 && result == RESULT_OK && data != null) {
      if (!PlayerAccount.linked(this) || !writable()) return;
      String ownerId = data.getStringExtra("ownerId");
      if (ownerId == null || !ownerId.equals(getSharedPreferences("boundary-account", 0).getString("id", ""))) return;
      String teamId = getPreferences(0).getString("addingPlayerTeam", "");
      String name = data.getStringExtra("playerName"), accountId = data.getStringExtra("playerId");
      if (name == null || name.isEmpty() || accountId == null || !accountId.startsWith("user_")) return;
      for (Team team : repo.teams()) if (team.id.equals(teamId)) {
        for (RegisteredPlayer receipt : repo.list("registered-player", RegisteredPlayer.class))
          if (receipt.teamId.equals(teamId) && receipt.accountId.equals(accountId)) {
            message("Already in this team", receipt.name + " is already in this squad."); return;
          }
        for (String savedName : team.players) if (savedName.equalsIgnoreCase(name) && !savedName.equals(name)) {
          message("Name already in team", "Choose a distinct display name before adding this player."); return;
        }
        String existing = registeredId(teamId, name);
        if (!existing.isEmpty() && !existing.equals(accountId)) { message("Name already in team", "A different registered player already uses this name. Ask the player to choose a distinct cricket display name."); return; }
        if (!team.players.contains(name)) team.players.add(name);
        repo.save(team);
        RegisteredPlayer player = new RegisteredPlayer(teamId, name, accountId);
        repo.save(player.storageId(), "registered-player", player);
        draw(); break;
      }
      getPreferences(0).edit().remove("addingPlayerTeam").apply();
      return;
    }
    if (req == 77 && result == RESULT_OK && data != null) {
      if (!writable()) return;
      guard(() -> command(data.getStringExtra("command")));
      return;
    }
    if (req == 12 && result == RESULT_OK && data != null) {
      ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (results == null || results.isEmpty()) return;
      String heard = results.get(0);
      new AlertDialog.Builder(this)
          .setTitle("Confirm official’s command")
          .setMessage(heard + "\n\nCheck the recognized words before applying.")
          .setNegativeButton("Ignore", null)
          .setPositiveButton("Apply", (d, w) -> guard(() -> command(heard)))
          .show();
    }
  }

  private void command(String heard) {
    com.example.madfinalproject.domain.VoiceCommands.Command c =
        com.example.madfinalproject.domain.VoiceCommands.parse(heard);
    switch (c.action) {
      case UNDO:
        confirmReversal(
            "Undo " + c.count + " deliveries",
            () -> {
              match.undo(c.count);
              persist();
              draw();
            });
        return;
      case NOT_OUT:
        List<Event> es = match.current().events;
        for (int i = es.size() - 1; i >= 0; i--) {
          Event e = es.get(i);
          if (!e.reversed && e.dismissal != Dismissal.NONE) {
            confirmReversal(
                "Reverse wicket",
                () -> {
                  e.dismissal = Dismissal.NONE;
                  e.incoming = "";
                  persist();
                  draw();
                });
            return;
          }
        }
        throw new IllegalArgumentException("No wicket found.");
      case REVIEW:
        startActivity(new Intent(this, StumpCamActivity.class));
        return;
      case CORRECT:
        Event selected = null;
        int legal = 0;
        for (Event e : match.current().events) {
          if (e.reversed || e.kind != Kind.BALL) continue;
          boolean valid =
              e.extra != Extra.WIDE && e.extra != Extra.NO_BALL && e.extra != Extra.PENALTY;
          if (valid) legal++;
          if (c.over < 0 || legal == c.over * match.rules.ballsPerOver + c.ball) selected = e;
          if (c.over >= 0 && selected != null) break;
        }
        if (selected == null) throw new IllegalArgumentException("No matching delivery found.");
        Event edit = selected;
        confirmReversal(
            "Apply voice correction",
            () -> {
              if (c.extra != Extra.NONE) {
                edit.extra = c.extra;
                edit.extras = edit.bat + edit.extras;
                edit.bat = 0;
              } else {
                edit.bat = c.runs;
                edit.completed = c.runs % 2;
                edit.boundary = c.runs == 4 || c.runs == 6;
                edit.extra = Extra.NONE;
                edit.extras = 0;
              }
              edit.note = "Voice correction";
              persist();
              draw();
            });
        return;
      case SCORE:
        if (c.normalized.contains("wicket") || c.normalized.matches(".*\\bout\\b.*")) {
          wicket();
          return;
        }
        Event e = new Event();
        e.extra = c.extra;
        if (c.extra == Extra.NONE || c.extra == Extra.NO_BALL) e.bat = c.runs;
        else e.extras = c.runs;
        e.completed = c.runs % 2;
        e.boundary = c.runs == 4 || c.runs == 6;
        record(e);
        return;
      default:
        message(
            "Command not understood",
            "No ball was logged. Try ‘four runs’, ‘wide’, ‘undo last two balls’, or ‘change last"
                + " ball to six’.");
    }
  }
}
