package com.example.madfinalproject;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Career;
import com.example.madfinalproject.domain.Cricket.Fixture;
import com.example.madfinalproject.domain.Cricket.Match;
import com.example.madfinalproject.domain.Cricket.Tournament;
import com.example.madfinalproject.domain.PlayerProfile;
import com.example.madfinalproject.domain.ProfileStats;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Your Cricket Profile": cover photo, playing role, and Matches, Stats, Teams and Connections tabs,
 * with the profile QR code and a share card. The header collapses into a bar and the tabs stick under it.
 */
public final class ProfileActivity extends AppCompatActivity {
  public static final String EXTRA_TAB = "tab", EXTRA_SHEET = "sheet";
  private static final String[] TABS = {"Matches", "Stats", "Teams", "Connections"};
  private static final String[] BALL_CODES = {"", "LEATHER", "TENNIS"}, BALL_LABELS = {"All", "Leather", "Tennis"};
  private static final String[] TYPE_CODES = {"", "LIMITED_OVERS", "BOX", "HUNDRED"},
      TYPE_LABELS = {"All", "Limited Overs", "Box Cricket", "The Hundred"};
  private static final int BUTTON_BG = 0x8c101814, BAR_DP = 64;

  private Ui.Palette p;
  private Repository repo;
  private ProfileStore store;
  private Career.Who who;
  private List<Match> matches;
  private Career career, filtered;
  private final Career.Filter filter = new Career.Filter();
  private String tab = "Matches", discipline = ProfileStats.BATTING;
  private boolean showFollowing, connectionsLoading, connectionsLoaded;
  private Social.Connections connections;
  private String connectionsError;
  private Runnable afterStoragePermission;
  private ActivityResultLauncher<String> storagePermission;

  private FrameLayout cover, topBar, barBackground;
  private ScrollView scroll;
  private LinearLayout info, body;
  private Strip inline, pinned;
  private boolean stuck;
  private TextView barTitle;
  private int statusInset, navInset, coverHeight;

  /** Tabs over filter chips. One copy scrolls under the info sheet; the other is pinned under the bar once it gets there. */
  private final class Strip {
    final LinearLayout root = Ui.column(ProfileActivity.this), tabs = Ui.row(ProfileActivity.this), chips = Ui.row(ProfileActivity.this);
    final HorizontalScrollView tabsScroll = new HorizontalScrollView(ProfileActivity.this),
        chipsScroll = new HorizontalScrollView(ProfileActivity.this);

    Strip() {
      root.setBackgroundColor(p.bg);
      tabsScroll.setHorizontalScrollBarEnabled(false);
      tabs.setPadding(dp(4), 0, dp(4), 0);
      tabsScroll.addView(tabs, new FrameLayout.LayoutParams(-2, dp(54)));
      root.addView(tabsScroll, new LinearLayout.LayoutParams(-1, dp(54)));
      root.addView(Ui.divider(ProfileActivity.this, p));
      chipsScroll.setHorizontalScrollBarEnabled(false);
      chips.setPadding(dp(16), dp(12), dp(16), dp(8));
      chipsScroll.addView(chips);
      root.addView(chipsScroll);
    }
  }

  @Override protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    p = Ui.palette(this);
    repo = new Repository(this);
    store = new ProfileStore(this);
    storagePermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
      Runnable next = afterStoragePermission;
      afterStoragePermission = null;
      if (Boolean.TRUE.equals(granted) && next != null) next.run();
      else toast("Allow storage access to save images to your gallery");
    });
    String requested = getIntent().getStringExtra(EXTRA_TAB);
    if (requested != null && Arrays.asList(TABS).contains(requested)) tab = requested;
    if (saved != null) {
      tab = saved.getString("tab", tab);
      discipline = saved.getString("discipline", discipline);
      filter.overs = saved.getInt("overs");
      filter.ballType = saved.getString("ballType", "");
      filter.matchType = saved.getString("matchType", "");
      showFollowing = saved.getBoolean("following");
    }
    loadCareer();
    build();
    drawTabs();
    drawChips();
    drawBody();
    if (saved == null && "qr".equals(getIntent().getStringExtra(EXTRA_SHEET))) scroll.post(this::qrSheet);
  }

  @Override protected void onResume() {
    super.onResume();
    Career.Who now = store.who(repo);
    if (!now.keys.equals(who.keys)) {
      loadCareer();
      drawChips();
      drawBody();
    }
    refreshHeader();
  }

  @Override protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("tab", tab);
    out.putString("discipline", discipline);
    out.putInt("overs", filter.overs);
    out.putString("ballType", filter.ballType);
    out.putString("matchType", filter.matchType);
    out.putBoolean("following", showFollowing);
  }

  private void loadCareer() {
    who = store.who(repo);
    matches = repo.matches();
    career = Career.of(matches, who, null);
    filtered = Career.of(matches, who, filter);
  }

  /** Header and bar title. */
  private String name() {
    String n = store.name();
    return n.isEmpty() ? "Your Cricket Profile" : n;
  }

  /** The owner as named on sheets and the share card. */
  private String person() {
    String n = store.name();
    return n.isEmpty() ? "You" : n;
  }

  // ---------------------------------------------------------------- frame

  /** Scrolling cover, info sheet, tabs and body, with the pinned tabs and the collapsing bar layered on top. */
  private void build() {
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(p.bg);
    setContentView(root);
    new WindowInsetsControllerCompat(getWindow(), root).setAppearanceLightStatusBars(false);
    coverHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.42f);

    scroll = new ScrollView(this);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setFillViewport(true);
    LinearLayout content = Ui.column(this);
    scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
    cover = new FrameLayout(this);
    content.addView(cover, new LinearLayout.LayoutParams(-1, coverHeight));
    info = Ui.column(this);
    info.setBackground(corners(p.surface, dp(26), dp(26), 0, 0));
    info.setPadding(dp(20), dp(22), dp(20), dp(22));
    LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-1, -2);
    il.topMargin = -dp(28);
    content.addView(info, il);
    inline = new Strip();
    content.addView(inline.root, new LinearLayout.LayoutParams(-1, -2));
    body = Ui.column(this);
    content.addView(body, new LinearLayout.LayoutParams(-1, -2));
    root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    pinned = new Strip();
    pinned.root.setVisibility(View.INVISIBLE);
    root.addView(pinned.root, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

    topBar = new FrameLayout(this);
    barBackground = new FrameLayout(this);
    barBackground.setAlpha(0);
    topBar.addView(barBackground, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout bar = Ui.row(this);
    bar.setPadding(dp(16), 0, dp(16), 0);
    topBar.addView(bar, new FrameLayout.LayoutParams(-1, dp(BAR_DP), Gravity.BOTTOM));
    bar.addView(barButton("back", this::finish));
    barTitle = Ui.text(this, "", 20, Color.WHITE, true);
    barTitle.setSingleLine(true);
    barTitle.setEllipsize(TextUtils.TruncateAt.END);
    barTitle.setPadding(dp(14), 0, dp(8), 0);
    barTitle.setAlpha(0);
    bar.addView(barTitle, Ui.weight(1));
    bar.addView(barButton("camera", () -> startActivity(new Intent(this, ProfilePhotoActivity.class))));
    bar.addView(Ui.space(this, 10, 1));
    bar.addView(barButton("qr", this::qrSheet));
    bar.addView(Ui.space(this, 10, 1));
    bar.addView(barButton("share", this::shareSheet));
    root.addView(topBar, new FrameLayout.LayoutParams(-1, dp(BAR_DP)));

    ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      statusInset = bars.top;
      navInset = bars.bottom;
      v.setPadding(bars.left, 0, bars.right, 0);
      topBar.getLayoutParams().height = barHeight();
      topBar.requestLayout();
      body.setPadding(dp(16), dp(6), dp(16), dp(28) + navInset);
      return insets;
    });
    scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> onScrolled());
    content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> onScrolled());
    inline.root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> v.post(this::fitBody));
    root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> v.post(this::fitBody));
  }

  private View barButton(String icon, Runnable action) {
    View b = Ui.iconButton(this, p, icon, 44, BUTTON_BG, Color.WHITE, action);
    b.setContentDescription(icon.equals("qr") ? "Your QR code" : icon.equals("camera") ? "Change profile photo" : icon);
    return b;
  }

  private int barHeight() { return statusInset + dp(BAR_DP); }

  /** Lets every tab, however short, scroll far enough for the tabs to pin. */
  private void fitBody() {
    int min = Math.max(0, scroll.getHeight() - barHeight() - inline.root.getHeight());
    if (body.getMinimumHeight() != min) body.setMinimumHeight(min);
  }

  private int pinAt() { return inline.root.getTop() - barHeight(); }

  /** Fades the bar in over the cover's last stretch and swaps to the pinned tabs when the inline ones reach it. */
  private void onScrolled() {
    int y = scroll.getScrollY(), bar = barHeight();
    float fade = (y - (coverHeight - bar - dp(56))) / (float) dp(48);
    fade = Math.max(0, Math.min(1, fade));
    barBackground.setAlpha(fade);
    barTitle.setAlpha(fade);
    pinned.root.setTranslationY(bar);
    boolean now = inline.root.getHeight() > 0 && y >= pinAt();
    if (now == stuck) return;
    stuck = now;
    Strip from = now ? inline : pinned, to = now ? pinned : inline;
    to.tabsScroll.scrollTo(from.tabsScroll.getScrollX(), 0);
    to.chipsScroll.scrollTo(from.chipsScroll.getScrollX(), 0);
    pinned.root.setVisibility(now ? View.VISIBLE : View.INVISIBLE);
  }

  /** Photo or initials, name, city and playing role; rerun when coming back from the photo screen. */
  private void refreshHeader() {
    String name = name();
    PlayerProfile attrs = store.attributes();
    cover.removeAllViews();
    barBackground.removeAllViews();
    cover.setBackground(coverGradient());
    barBackground.setBackground(coverGradient());
    Bitmap photo = store.hasPhoto() ? Ui.decode(store.photo(), getResources().getDisplayMetrics().widthPixels) : null;
    if (photo != null) {
      ImageView image = new ImageView(this);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setImageBitmap(photo);
      cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
      View scrim = new View(this);
      scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x73000000, 0x00000000}));
      cover.addView(scrim, new FrameLayout.LayoutParams(-1, dp(150), Gravity.TOP));
      ImageView blur = new ImageView(this);
      blur.setScaleType(ImageView.ScaleType.CENTER_CROP);
      blur.setImageBitmap(Bitmap.createScaledBitmap(photo, 24, Math.max(1, 24 * photo.getHeight() / photo.getWidth()), true));
      barBackground.addView(blur, new FrameLayout.LayoutParams(-1, -1));
      View dim = new View(this);
      dim.setBackgroundColor(0x59000000);
      barBackground.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    } else {
      TextView initials = Ui.text(this, Ui.initials(store.name()), 96, 0xf2ffffff, true);
      initials.setGravity(Gravity.CENTER);
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
      lp.bottomMargin = dp(10);
      cover.addView(initials, lp);
    }
    barTitle.setText(name);

    info.removeAllViews();
    info.addView(Ui.text(this, name, 26, p.ink, true));
    LinearLayout sub = Ui.row(this);
    sub.setPadding(0, dp(8), 0, 0);
    if (!attrs.city.isEmpty()) {
      sub.addView(Ui.text(this, attrs.city, 15, p.ink, false));
      sub.addView(rule());
    }
    sub.addView(Ui.text(this, ProfileStats.matchesLabel(career.matches.size()), 15, p.ink, false));
    info.addView(sub);
    List<String> batting = ProfileStats.battingRole(attrs);
    if (!batting.isEmpty() || !attrs.bowlingStyle.isEmpty()) {
      LinearLayout roles = Ui.column(this);
      roles.setPadding(0, dp(16), 0, 0);
      if (!batting.isEmpty()) roles.addView(parts(batting, 15, p.ink));
      if (!attrs.bowlingStyle.isEmpty()) {
        TextView bowl = Ui.text(this, attrs.bowlingStyle, 15, p.ink, false);
        bowl.setPadding(0, dp(batting.isEmpty() ? 0 : 8), 0, 0);
        roles.addView(bowl);
      }
      info.addView(roles);
    }
  }

  private GradientDrawable coverGradient() {
    return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {shade(p.accent, 0.1f), shade(p.accent, 0.55f)});
  }

  /** "Middle-order batter | RHB" with thin dividers. */
  private LinearLayout parts(List<String> values, float size, int color) {
    LinearLayout r = Ui.row(this);
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) r.addView(rule());
      r.addView(Ui.text(this, values.get(i), size, color, false));
    }
    return r;
  }

  private View rule() {
    View v = new View(this);
    v.setBackgroundColor(p.line);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(16));
    lp.setMargins(dp(10), 0, dp(10), 0);
    v.setLayoutParams(lp);
    return v;
  }

  // ---------------------------------------------------------------- tabs and filter chips

  private void drawTabs() {
    for (Strip strip : new Strip[] {inline, pinned}) drawTabs(strip);
  }

  private void drawTabs(Strip strip) {
    strip.tabs.removeAllViews();
    View selected = null;
    for (String t : TABS) {
      boolean on = t.equals(tab);
      LinearLayout item = Ui.column(this);
      item.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      item.setPadding(dp(12), 0, dp(12), 0);
      TextView label = Ui.text(this, t, 16, on ? p.accent : p.muted, on);
      label.setPadding(0, 0, 0, dp(12));
      item.addView(label, new LinearLayout.LayoutParams(-2, -2));
      View indicator = new View(this);
      indicator.setBackground(corners(on ? p.accent : Color.TRANSPARENT, dp(3), dp(3), 0, 0));
      item.addView(indicator, new LinearLayout.LayoutParams(-1, dp(3)));
      item.setOnClickListener(v -> select(t));
      strip.tabs.addView(item, new LinearLayout.LayoutParams(-2, -1));
      if (on) selected = item;
    }
    View target = selected;
    if (target != null) strip.tabsScroll.post(() -> strip.tabsScroll.smoothScrollTo(Math.max(0, target.getLeft() - dp(16)), 0));
  }

  private void select(String t) {
    if (t.equals(tab)) return;
    boolean keepPinned = scroll.getScrollY() >= pinAt();
    tab = t;
    drawTabs();
    drawChips();
    drawBody();
    if (keepPinned) scrollToTabs();
  }

  /** After the list under the tabs changes, show its start with the tabs still pinned. */
  private void scrollToTabs() { scroll.post(() -> scroll.scrollTo(0, pinAt())); }

  private void drawChips() {
    for (Strip strip : new Strip[] {inline, pinned}) drawChips(strip.chips);
  }

  private void drawChips(LinearLayout chips) {
    chips.removeAllViews();
    boolean show = !who.keys.isEmpty() && (tab.equals("Matches") || tab.equals("Stats"));
    ((View) chips.getParent()).setVisibility(show ? View.VISIBLE : View.GONE);
    if (!show) return;
    addChip(chips, dropChip("filter", filter.overs == 0 ? "Filters" : "Filters (1)", filter.overs != 0, this::oversMenu));
    if (tab.equals("Stats")) addChip(chips, dropChip(null, discipline, true, this::disciplineMenu));
    addChip(chips, dropChip(null, filter.ballType.isEmpty() ? "BALL TYPE" : PlayerProfile.label(filter.ballType),
        !filter.ballType.isEmpty(), this::ballMenu));
    addChip(chips, dropChip(null, filter.matchType.isEmpty() ? "MATCH TYPE" : PlayerProfile.label(filter.matchType),
        !filter.matchType.isEmpty(), this::matchTypeMenu));
  }

  private void addChip(LinearLayout chips, View chip) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
    lp.rightMargin = dp(8);
    chips.addView(chip, lp);
  }

  private interface Anchored { void open(View anchor); }

  /** Rounded selector with an optional leading icon and a chevron; accent when it narrows the list. */
  private View dropChip(String icon, String label, boolean selected, Anchored menu) {
    int fg = selected ? p.onAccent : p.ink;
    LinearLayout chip = Ui.row(this);
    chip.setPadding(dp(icon == null ? 14 : 10), 0, dp(10), 0);
    chip.setBackground(selected ? Ui.round(p.accent, dp(10)) : Ui.outline(p.surface, p.line, dp(1), dp(10)));
    if (icon != null) {
      chip.addView(new IconView(this, icon, fg), new LinearLayout.LayoutParams(dp(18), dp(18)));
      chip.addView(Ui.space(this, 6, 1));
    }
    TextView text = Ui.text(this, label, 15, fg, false);
    text.setSingleLine(true);
    chip.addView(text);
    chip.addView(Ui.space(this, 6, 1));
    chip.addView(new IconView(this, "chevron-down", fg), new LinearLayout.LayoutParams(dp(18), dp(18)));
    chip.setOnClickListener(menu::open);
    return chip;
  }

  private void oversMenu(View anchor) {
    List<Integer> overs = ProfileStats.oversPlayed(career.matches);
    String[] labels = new String[overs.size() + 1];
    labels[0] = "All overs";
    int selected = 0;
    for (int i = 0; i < overs.size(); i++) {
      labels[i + 1] = overs.get(i) + " Ov.";
      if (overs.get(i) == filter.overs) selected = i + 1;
    }
    menu(anchor, labels, selected, i -> { filter.overs = i == 0 ? 0 : overs.get(i - 1); refilter(); });
  }

  private void disciplineMenu(View anchor) {
    menu(anchor, ProfileStats.DISCIPLINES, Arrays.asList(ProfileStats.DISCIPLINES).indexOf(discipline), i -> {
      discipline = ProfileStats.DISCIPLINES[i];
      boolean keepPinned = scroll.getScrollY() >= pinAt();
      drawChips();
      drawBody();
      if (keepPinned) scrollToTabs();
    });
  }

  private void ballMenu(View anchor) {
    menu(anchor, BALL_LABELS, Math.max(0, Arrays.asList(BALL_CODES).indexOf(filter.ballType)), i -> {
      filter.ballType = BALL_CODES[i];
      refilter();
    });
  }

  private void matchTypeMenu(View anchor) {
    menu(anchor, TYPE_LABELS, Math.max(0, Arrays.asList(TYPE_CODES).indexOf(filter.matchType)), i -> {
      filter.matchType = TYPE_CODES[i];
      refilter();
    });
  }

  private void refilter() {
    boolean keepPinned = scroll.getScrollY() >= pinAt();
    filtered = Career.of(matches, who, filter);
    drawChips();
    drawBody();
    if (keepPinned) scrollToTabs();
  }

  /** Dropdown card under the chip; the current choice is in the accent colour. */
  private void menu(View anchor, String[] labels, int selected, IntConsumer pick) {
    LinearLayout list = Ui.column(this);
    list.setPadding(0, dp(8), 0, dp(8));
    PopupWindow popup = new PopupWindow(list, Math.max(anchor.getWidth(), dp(210)), -2, true);
    popup.setBackgroundDrawable(Ui.outline(p.surface, p.line, dp(1), dp(18)));
    popup.setElevation(dp(10));
    for (int i = 0; i < labels.length; i++) {
      boolean on = i == selected;
      TextView item = Ui.text(this, labels[i], 16, on ? p.accent : p.ink, on);
      item.setPadding(dp(22), dp(14), dp(22), dp(14));
      int index = i;
      item.setOnClickListener(v -> { popup.dismiss(); pick.accept(index); });
      list.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }
    popup.showAsDropDown(anchor, 0, dp(6));
  }

  // ---------------------------------------------------------------- tab bodies

  private void drawBody() {
    body.removeAllViews();
    switch (tab) {
      case "Stats": statsTab(); break;
      case "Teams": teamsTab(); break;
      case "Connections": connectionsTab(); break;
      default: matchesTab();
    }
  }

  private boolean needsIdentity() {
    if (!who.keys.isEmpty()) return false;
    LinearLayout card = Ui.card(this, p);
    card.setPadding(dp(18), dp(18), dp(18), dp(18));
    card.addView(new IconView(this, "user", p.accent), new LinearLayout.LayoutParams(dp(36), dp(36)));
    TextView title = Ui.text(this, "Which squad places are you?", 18, p.ink, true);
    title.setPadding(0, dp(12), 0, dp(6));
    card.addView(title);
    card.addView(Ui.text(this, "Your matches, stats and teams appear here once Boundary knows which players are you. "
        + "Link your phone account, or mark your squad places with \"This is me\" in My Cricket > Teams.", 14, p.muted, false));
    if (!PlayerAccount.linked(this)) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.topMargin = dp(14);
      card.addView(Ui.pill(this, p, "Link phone account", true,
          () -> startActivity(new Intent(this, PlayerAccountActivity.class))), lp);
    }
    addToBody(card, 10);
    return true;
  }

  private void addToBody(View v, int topMarginDp) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(topMarginDp);
    body.addView(v, lp);
  }

  private void note(String title, String text) {
    LinearLayout card = Ui.card(this, p);
    card.addView(Ui.text(this, title, 17, p.ink, true));
    TextView t = Ui.text(this, text, 14, p.muted, false);
    t.setPadding(0, dp(6), 0, 0);
    card.addView(t);
    addToBody(card, 10);
  }

  private void matchesTab() {
    if (needsIdentity()) return;
    List<Career.MatchRow> rows = ProfileStats.newestFirst(filtered.matches);
    if (rows.isEmpty()) {
      if (career.matches.isEmpty()) note("No matches yet", "Matches you play in will be listed here.");
      else note("No matches for these filters", "Change Filters, ball type or match type to see more.");
      return;
    }
    List<Tournament> tournaments = repo.tournaments();
    for (Career.MatchRow r : rows) addToBody(matchCard(r.match, tournaments), 10);
  }

  /** Title and result badge, date | overs | ground, both scores with the winner bold, result, actions. */
  private View matchCard(Match m, List<Tournament> tournaments) {
    LinearLayout card = Ui.column(this);
    card.setBackground(Ui.outline(p.surface, p.line, dp(1), dp(18)));
    card.setPadding(dp(18), dp(16), dp(18), dp(14));
    String result = m.result();
    boolean live = result.equals("Live"), started = !m.innings.isEmpty();
    String title = "Individual Match", subtitle = "";
    for (Tournament t : tournaments) {
      if (!t.id.equals(m.tournament)) continue;
      subtitle = t.name == null ? "" : t.name;
      title = "Tournament Match";
      if (t.fixtures != null) for (Fixture f : t.fixtures) if (m.id.equals(f.matchId) && f.round != null) title = f.round;
    }
    LinearLayout top = Ui.row(this);
    TextView heading = Ui.text(this, title, 17, p.ink, false);
    heading.setSingleLine(true);
    heading.setEllipsize(TextUtils.TruncateAt.END);
    top.addView(heading, Ui.weight(1));
    TextView badge = Ui.text(this, !started ? "Upcoming" : live ? "Live" : "Result", 13, live && started ? p.onAccent : p.ink, true);
    badge.setBackground(Ui.round(live && started ? p.accent : p.surface2, dp(14)));
    badge.setPadding(dp(12), dp(5), dp(12), dp(5));
    top.addView(badge);
    card.addView(top);
    if (!subtitle.isEmpty()) card.addView(meta(subtitle, 4));
    StringBuilder line = new StringBuilder(ProfileStats.matchDate(m.played(), System.currentTimeMillis(), TimeZone.getDefault()));
    line.append("  |  ").append(m.rules.overs).append(" Ov.");
    if (m.venue != null && !m.venue.isEmpty()) line.append("  |  ").append(m.venue);
    card.addView(meta(line.toString(), subtitle.isEmpty() ? 6 : 2));
    View d1 = Ui.divider(this, p);
    card.addView(d1);
    ((LinearLayout.LayoutParams) d1.getLayoutParams()).topMargin = dp(12);
    String winner = m.winner();
    for (String team : new String[] {m.teamA, m.teamB}) {
      boolean won = team.equals(winner);
      LinearLayout row = Ui.row(this);
      row.setPadding(0, dp(12), 0, 0);
      TextView name = Ui.text(this, team, 16, won || winner == null ? p.ink : p.muted, won);
      name.setSingleLine(true);
      name.setEllipsize(TextUtils.TruncateAt.END);
      row.addView(name, Ui.weight(1));
      String[] score = ProfileStats.score(m, team);
      TextView s = Ui.text(this, "", 16, won || winner == null ? p.ink : p.muted, won);
      if (score == null) s.setText("Yet to bat");
      else {
        SpannableStringBuilder b = new SpannableStringBuilder(score[0]);
        int start = b.length();
        b.append("  (").append(score[1]).append(")");
        b.setSpan(new RelativeSizeSpan(0.85f), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new TypefaceSpan("sans-serif"), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setText(b);
      }
      row.addView(s);
      card.addView(row);
    }
    View d2 = Ui.divider(this, p);
    card.addView(d2);
    ((LinearLayout.LayoutParams) d2.getLayoutParams()).topMargin = dp(12);
    String outcome = result;
    if (live) outcome = started ? m.current().battingTeam + " batting" : "Yet to start";
    TextView resultLine = Ui.text(this, outcome, 15, p.ink, false);
    resultLine.setPadding(0, dp(12), 0, dp(10));
    card.addView(resultLine);
    LinearLayout actions = Ui.row(this);
    actions.setGravity(Gravity.END);
    actions.addView(Ui.chip(this, p, "Insights", false, () -> matchDetail(m, "Summary")));
    actions.addView(Ui.space(this, 8, 1));
    actions.addView(Ui.chip(this, p, "Squads", false, () -> matchDetail(m, "Squads")));
    card.addView(actions);
    card.setOnClickListener(v -> matchDetail(m, "Summary"));
    return card;
  }

  private TextView meta(String text, int topDp) {
    TextView t = Ui.text(this, text, 14, p.muted, false);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setPadding(0, dp(topDp), 0, 0);
    return t;
  }

  private void matchDetail(Match m, String detailTab) {
    startActivity(new Intent(this, MatchDetailActivity.class)
        .putExtra(MatchDetailActivity.EXTRA_MATCH_ID, m.id).putExtra(MatchDetailActivity.EXTRA_TAB, detailTab));
  }

  private void statsTab() {
    if (needsIdentity()) return;
    if (filtered.matches.isEmpty()) {
      note("No matches for these filters", "Change Filters, ball type or match type to see your numbers.");
      return;
    }
    body.addView(sectionHead(null, "Overall", Ui.chip(this, p, "Compare", false, () ->
        startActivity(new Intent(this, PerformanceActivity.class).putExtra(PerformanceActivity.EXTRA_TAB, "Compare")))));
    addTiles(ProfileStats.stats(discipline, filtered));
    for (ProfileStats.Section s : ProfileStats.sections(filtered.matches)) {
      Career section = Career.of(matches, who, s.filter(filter));
      List<View> pills = new ArrayList<>();
      if (s.byBallType() && !discipline.equals(ProfileStats.FIELDING))
        pills.add(Ui.chip(this, p, "Wagon Wheel", false, () -> wagonSheet(s, section)));
      pills.add(Ui.chip(this, p, "Table View", false, () -> tableSheet(s.title, section)));
      body.addView(sectionHead(s.byBallType() ? s.ballType : null, s.title, pills.toArray(new View[0])));
      addTiles(ProfileStats.stats(discipline, section));
    }
  }

  private LinearLayout sectionHead(String ball, String title, View... pills) {
    LinearLayout head = Ui.row(this);
    head.setPadding(dp(2), dp(26), 0, dp(12));
    if (ball != null) {
      head.addView(ballGlyph(ball), new LinearLayout.LayoutParams(dp(26), dp(26)));
      head.addView(Ui.space(this, 10, 1));
    }
    TextView t = Ui.text(this, title, 21, p.ink, true);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    head.addView(t, Ui.weight(1));
    for (View pill : pills) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.leftMargin = dp(8);
      head.addView(pill, lp);
    }
    return head;
  }

  private View ballGlyph(String ballType) {
    return new ProfileShare.Glyph(this, ballType.equals("TENNIS") ? "tennis" : "leather", p.ink);
  }

  /** Three-column tiles; the grid's own 4dp cell margins hang into the body padding so edges line up. */
  private void addTiles(List<ProfileStats.Stat> stats) {
    View[] items = new View[stats.size()];
    for (int i = 0; i < items.length; i++) items[i] = Ui.tile(this, p, stats.get(i).value, stats.get(i).label);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(-dp(4), 0, -dp(4), 0);
    body.addView(Ui.grid(this, items, 3), lp);
  }

  private void teamsTab() {
    if (needsIdentity()) return;
    List<Career.TeamSummary> teams = career.teams();
    if (teams.isEmpty()) {
      note("No teams yet", "Teams you have played for will be listed here.");
      return;
    }
    for (Career.TeamSummary t : teams) {
      LinearLayout card = Ui.row(this);
      card.setBackground(Ui.outline(p.surface, p.line, dp(1), dp(18)));
      card.setPadding(dp(16), dp(16), dp(16), dp(16));
      TextView logo = Ui.text(this, Ui.initials(t.team), 22, p.onAccent, true);
      logo.setGravity(Gravity.CENTER);
      logo.setBackground(Ui.round(p.accent, dp(14)));
      card.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));
      LinearLayout text = Ui.column(this);
      text.setPadding(dp(16), 0, 0, 0);
      TextView name = Ui.text(this, t.team, 17, p.ink, true);
      name.setSingleLine(true);
      name.setEllipsize(TextUtils.TruncateAt.END);
      text.addView(name);
      text.addView(meta(ProfileStats.since(t.since, TimeZone.getDefault()), 6));
      SpannableStringBuilder line = new SpannableStringBuilder();
      record(line, "Played: ", t.played);
      record(line, "Won: ", t.won);
      record(line, "Lost: ", t.lost);
      TextView counts = Ui.text(this, "", 14, p.muted, false);
      counts.setText(line);
      counts.setPadding(0, dp(10), 0, 0);
      text.addView(counts);
      card.addView(text, Ui.weight(1));
      addToBody(card, 10);
    }
  }

  private void record(SpannableStringBuilder line, String label, int value) {
    if (line.length() > 0) line.append("     ");
    line.append(label);
    int start = line.length();
    line.append(String.valueOf(value));
    line.setSpan(new ForegroundColorSpan(p.ink), start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    line.setSpan(new StyleSpan(Typeface.BOLD), start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  private void connectionsTab() {
    LinearLayout segments = Ui.row(this);
    segments.setBackground(Ui.round(p.surface2, dp(24)));
    segments.setPadding(dp(4), dp(4), dp(4), dp(4));
    for (String label : new String[] {"Followers", "Following"}) {
      boolean on = label.equals("Following") == showFollowing;
      TextView s = Ui.text(this, label, 15, on ? p.onAccent : p.ink, true);
      s.setGravity(Gravity.CENTER);
      s.setPadding(0, dp(10), 0, dp(10));
      if (on) s.setBackground(Ui.round(p.accent, dp(20)));
      s.setOnClickListener(v -> { showFollowing = label.equals("Following"); drawBody(); });
      segments.addView(s, Ui.weight(1));
    }
    addToBody(segments, 10);
    LinearLayout actions = Ui.row(this);
    TextView scan = Ui.pill(this, p, "Scan QR to follow", true, () -> startActivity(new Intent(this, QrScanActivity.class)));
    TextView mine = Ui.pill(this, p, "My QR code", false, this::qrSheet);
    for (TextView pill : new TextView[] {scan, mine}) {
      pill.setPadding(dp(8), dp(12), dp(8), dp(12));
      pill.setSingleLine(true);
    }
    actions.addView(scan, Ui.weight(1));
    actions.addView(Ui.space(this, 10, 1));
    actions.addView(mine, Ui.weight(1));
    addToBody(actions, 14);

    if (!connectionsLoaded) {
      TextView loading = Ui.text(this, "Loading connections…", 14, p.muted, false);
      loading.setPadding(dp(4), dp(18), 0, 0);
      body.addView(loading);
      loadConnections();
      return;
    }
    if (connections == null) {
      LinearLayout card = Ui.card(this, p);
      card.setPadding(dp(18), dp(18), dp(18), dp(18));
      card.addView(new IconView(this, "team", p.accent), new LinearLayout.LayoutParams(dp(36), dp(36)));
      TextView title = Ui.text(this, "Connections", 18, p.ink, true);
      title.setPadding(0, dp(12), 0, dp(6));
      card.addView(title);
      boolean notLive = Social.NOT_DEPLOYED.equals(connectionsError);
      card.addView(Ui.text(this, notLive ? "Connections will appear here once the Boundary server update is live."
          : connectionsError == null ? "Connections could not be loaded." : connectionsError, 14, p.muted, false));
      if (!notLive) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(12);
        card.addView(Ui.chip(this, p, "Try again", false, () -> { connectionsLoaded = false; drawBody(); }), lp);
      }
      addToBody(card, 16);
      return;
    }
    List<Social.Person> people = showFollowing ? connections.following : connections.followers;
    if (people.isEmpty()) {
      note(showFollowing ? "Not following anyone yet" : "No followers yet", showFollowing
          ? "Scan a teammate's Boundary QR code to follow them."
          : "Share your QR code so teammates can follow you.");
      return;
    }
    LinearLayout list = Ui.card(this, p);
    list.setPadding(dp(16), dp(4), dp(16), dp(4));
    for (int i = 0; i < people.size(); i++) {
      if (i > 0) list.addView(Ui.divider(this, p));
      LinearLayout row = Ui.row(this);
      row.setPadding(0, dp(12), 0, dp(12));
      row.addView(Ui.avatar(this, p, null, people.get(i).name, 44));
      TextView name = Ui.text(this, people.get(i).name, 16, p.ink, true);
      name.setPadding(dp(14), 0, 0, 0);
      row.addView(name, Ui.weight(1));
      list.addView(row);
    }
    addToBody(list, 16);
  }

  private void loadConnections() {
    if (connectionsLoading) return;
    connectionsLoading = true;
    Social.connections(this, (result, error) -> {
      connectionsLoading = false;
      connectionsLoaded = true;
      connections = result;
      connectionsError = error;
      if (!isFinishing() && !isDestroyed() && tab.equals("Connections")) drawBody();
    });
  }

  // ---------------------------------------------------------------- sheets

  /** Bottom sheet in the palette's surface colour, opened fully. */
  private BottomSheetDialog sheet(LinearLayout content) {
    BottomSheetDialog dialog = new BottomSheetDialog(this);
    NestedScrollView scrollView = new NestedScrollView(this);
    LinearLayout box = Ui.column(this);
    View handle = new View(this);
    handle.setBackground(Ui.round(p.line, dp(2)));
    LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(40), dp(4));
    hl.gravity = Gravity.CENTER_HORIZONTAL;
    hl.setMargins(0, dp(12), 0, dp(6));
    box.addView(handle, hl);
    box.addView(content, new LinearLayout.LayoutParams(-1, -2));
    scrollView.addView(box);
    dialog.setContentView(scrollView);
    View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
    if (container != null) Ui.tint(container, p.surface);
    dialog.getBehavior().setSkipCollapsed(true);
    dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    dialog.show();
    return dialog;
  }

  private void wagonSheet(ProfileStats.Section section, Career c) {
    boolean bowling = discipline.equals(ProfileStats.BOWLING);
    List<WagonWheelView.Shot> shots = new ArrayList<>();
    for (Career.Ball b : ProfileStats.withZone(bowling ? c.bowled : c.faced))
      shots.add(new WagonWheelView.Shot(b.zone, b.batRuns, b.batterOut));
    boolean leftHanded = !bowling && "LHB".equals(store.attributes().battingHand);

    LinearLayout box = Ui.column(this);
    box.setPadding(dp(20), dp(10), dp(20), dp(28));
    box.addView(Ui.text(this, person() + " - " + (bowling ? "Bowling" : "Batting"), 24, p.ink, true));
    LinearLayout ball = Ui.row(this);
    ball.setPadding(0, dp(22), 0, dp(14));
    ball.addView(Ui.text(this, section.title + " ball", 18, p.ink, true));
    ball.addView(Ui.space(this, 10, 1));
    ball.addView(ballGlyph(section.ballType), new LinearLayout.LayoutParams(dp(24), dp(24)));
    box.addView(ball);
    WagonWheelView wheel = new WagonWheelView(this);
    wheel.setShots(shots, leftHanded);
    box.addView(wheel, new LinearLayout.LayoutParams(-1, -2));

    TextView insight = Ui.text(this, "", 15, p.ink, false);
    insight.setBackground(Ui.round(p.surface2, dp(12)));
    insight.setPadding(dp(16), dp(14), dp(16), dp(14));
    Set<String> visible = new HashSet<>(Arrays.asList(WagonWheelView.CATEGORIES));
    Runnable summarise = () -> {
      List<WagonWheelView.Shot> shown = new ArrayList<>();
      for (WagonWheelView.Shot s : shots) if (visible.contains(s.category())) shown.add(s);
      String summary = WagonWheelView.summary(shown, Career.ZONES);
      insight.setText(summary.isEmpty()
          ? shots.isEmpty() ? "No shot directions were recorded for these balls." : "No runs in the selected shot types."
          : insightText(summary));
    };
    LinearLayout legend = Ui.row(this);
    legend.setPadding(0, dp(18), 0, 0);
    for (int i = 0; i < WagonWheelView.CATEGORIES.length; i++) {
      String category = WagonWheelView.CATEGORIES[i];
      LinearLayout item = Ui.column(this);
      item.setGravity(Gravity.CENTER);
      item.setBackground(Ui.round(p.surface2, dp(12)));
      item.setPadding(0, dp(10), 0, dp(10));
      View dot = new View(this);
      GradientDrawable d = Ui.circle(WagonWheelView.CATEGORY_COLORS[i]);
      d.setStroke(dp(1), p.line);
      dot.setBackground(d);
      item.addView(dot, new LinearLayout.LayoutParams(dp(18), dp(18)));
      TextView label = Ui.text(this, category, 14, p.ink, false);
      label.setPadding(0, dp(8), 0, 0);
      item.addView(label);
      item.setOnClickListener(v -> {
        if (!visible.remove(category)) visible.add(category);
        item.setAlpha(visible.contains(category) ? 1f : 0.35f);
        wheel.setVisible(visible);
        summarise.run();
      });
      LinearLayout.LayoutParams lp = Ui.weight(1);
      lp.setMargins(dp(3), 0, dp(3), 0);
      legend.addView(item, lp);
    }
    box.addView(legend);
    TextView caution = Ui.text(this, "*Stats may vary if Wagon Wheel was not used correctly.", 13, p.muted, false);
    caution.setGravity(Gravity.CENTER);
    caution.setPadding(0, dp(18), 0, dp(14));
    box.addView(caution, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout split = Ui.row(this);
    split.setPadding(dp(12), 0, dp(12), dp(14));
    split.addView(Ui.divider(this, p), new LinearLayout.LayoutParams(0, Math.max(1, dp(0.7f)), 1));
    LinearLayout.LayoutParams chart = new LinearLayout.LayoutParams(dp(22), dp(22));
    chart.setMargins(dp(10), 0, dp(10), 0);
    split.addView(new IconView(this, "performance", p.muted), chart);
    split.addView(Ui.divider(this, p), new LinearLayout.LayoutParams(0, Math.max(1, dp(0.7f)), 1));
    box.addView(split);
    summarise.run();
    box.addView(insight, new LinearLayout.LayoutParams(-1, -2));
    sheet(box);
  }

  /** "•  57% runs scored in Long on from total 7" with the numbers and the region bold. */
  private CharSequence insightText(String summary) {
    SpannableStringBuilder s = new SpannableStringBuilder("•  " + summary);
    Matcher m = Pattern.compile("^(\\d+%) runs scored in (.+) from total (\\d+)$").matcher(summary);
    if (m.matches())
      for (int g = 1; g <= 3; g++)
        s.setSpan(new StyleSpan(Typeface.BOLD), m.start(g) + 3, m.end(g) + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return s;
  }

  private void tableSheet(String title, Career c) {
    LinearLayout box = Ui.column(this);
    box.setPadding(dp(20), dp(10), dp(20), dp(28));
    box.addView(Ui.text(this, title + " - " + discipline, 24, p.ink, true));
    LinearLayout table = Ui.column(this);
    table.setBackground(Ui.round(p.surface2, dp(14)));
    table.setPadding(dp(16), dp(4), dp(16), dp(4));
    List<ProfileStats.Stat> stats = ProfileStats.stats(discipline, c);
    for (int i = 0; i < stats.size(); i++) {
      if (i > 0) table.addView(Ui.divider(this, p));
      LinearLayout row = Ui.row(this);
      row.setPadding(0, dp(13), 0, dp(13));
      row.addView(Ui.text(this, stats.get(i).label, 15, p.muted, false), Ui.weight(1));
      row.addView(Ui.text(this, stats.get(i).value, 15, p.ink, true));
      table.addView(row);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(18);
    box.addView(table, lp);
    sheet(box);
  }

  /** "Your QR code": avatar, name, role, the code itself and a short account id; links the account when missing. */
  private void qrSheet() {
    String account = store.accountId();
    LinearLayout box = Ui.column(this);
    box.setPadding(0, dp(4), 0, dp(24));
    LinearLayout head = Ui.row(this);
    head.setPadding(dp(20), dp(8), dp(20), dp(14));
    head.addView(Ui.text(this, "Your QR code", 24, p.ink, true), Ui.weight(1));
    LinearLayout card = Ui.column(this);
    card.setGravity(Gravity.CENTER_HORIZONTAL);
    card.setBackgroundColor(p.surface);
    card.setPadding(dp(24), dp(24), dp(24), dp(16));
    LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(112), dp(112));
    card.addView(roundPhoto(112), al);
    TextView owner = Ui.text(this, person(), 19, p.ink, true);
    owner.setGravity(Gravity.CENTER);
    owner.setPadding(0, dp(14), 0, 0);
    card.addView(owner);
    String role = ProfileStats.roleSentence(store.attributes());
    if (!role.isEmpty()) {
      TextView r = Ui.text(this, role, 14, p.muted, false);
      r.setGravity(Gravity.CENTER);
      r.setPadding(0, dp(6), 0, 0);
      card.addView(r);
    }
    BottomSheetDialog[] dialog = new BottomSheetDialog[1];
    if (account.isEmpty()) {
      TextView why = Ui.text(this, "Link your phone account to get a QR code that teammates can scan to follow you.", 15, p.ink, false);
      why.setGravity(Gravity.CENTER);
      why.setPadding(0, dp(24), 0, dp(16));
      card.addView(why);
      card.addView(Ui.pill(this, p, "Link phone account", true, () -> {
        if (dialog[0] != null) dialog[0].dismiss();
        startActivity(new Intent(this, PlayerAccountActivity.class));
      }));
    } else {
      FrameLayout frame = new FrameLayout(this);
      frame.setBackground(Ui.outline(Color.WHITE, p.dark ? Color.WHITE : p.line, dp(1), dp(20)));
      frame.setPadding(dp(18), dp(18), dp(18), dp(18));
      ImageView code = new ImageView(this);
      try {
        code.setImageBitmap(ProfileShare.qr(Social.qrPayload(account), dp(220), new Ui.Palette(false).accent));
      } catch (Exception e) {
        toast("Could not draw the QR code");
      }
      frame.addView(code, new FrameLayout.LayoutParams(dp(220), dp(220)));
      LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
      fl.topMargin = dp(22);
      card.addView(frame, fl);
      TextView hint = Ui.text(this, "Let cricketers find you easily with above QR code.", 16, p.ink, false);
      hint.setGravity(Gravity.CENTER);
      hint.setPadding(dp(16), dp(20), dp(16), 0);
      card.addView(hint);
      TextView id = Ui.text(this, "ID: " + ProfileStats.shortId(account), 14, p.muted, false);
      id.setPadding(0, dp(12), 0, 0);
      card.addView(id);
      head.addView(Ui.iconButton(this, p, "download", 44, p.surface2, p.ink, () ->
          saveImage(ProfileShare.snapshot(card, 1080, p.surface), "boundary-qr")));
      head.addView(Ui.space(this, 10, 1));
      head.addView(Ui.iconButton(this, p, "share", 44, p.surface2, p.ink, () ->
          shareImage(ProfileShare.snapshot(card, 1080, p.surface), "boundary-qr", followText(account), null)));
    }
    box.addView(head);
    box.addView(Ui.divider(this, p));
    box.addView(card, new LinearLayout.LayoutParams(-1, -2));
    dialog[0] = sheet(box);
  }

  /** Profile card preview, then Download, Copy Link and app targets for the rendered card. */
  private void shareSheet() {
    String account = store.accountId();
    LinearLayout box = Ui.column(this);
    box.setPadding(dp(20), dp(8), dp(20), dp(24));
    LinearLayout card = shareCard();
    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(270), -2);
    cl.gravity = Gravity.CENTER_HORIZONTAL;
    box.addView(card, cl);
    TextView title = Ui.text(this, "Share to", 21, p.ink, true);
    title.setPadding(0, dp(24), 0, dp(14));
    box.addView(title);
    String text = followText(account);
    Object[][] targets = {
        {"download", "Download", (Runnable) () -> saveImage(cardImage(card), "boundary-profile")},
        {"link", "Copy Link", (Runnable) () -> copyLink(account)},
        {"instagram", "Instagram", (Runnable) () -> shareImage(cardImage(card), "boundary-profile", text, ProfileShare.INSTAGRAM)},
        {"facebook", "Facebook", (Runnable) () -> shareImage(cardImage(card), "boundary-profile", text, ProfileShare.FACEBOOK)},
        {"whatsapp", "WhatsApp", (Runnable) () -> shareImage(cardImage(card), "boundary-profile", text, ProfileShare.WHATSAPP)},
        {"share", "Share", (Runnable) () -> shareImage(cardImage(card), "boundary-profile", text, null)}};
    View[] items = new View[targets.length];
    for (int i = 0; i < targets.length; i++) {
      String icon = (String) targets[i][0];
      LinearLayout item = Ui.column(this);
      item.setGravity(Gravity.CENTER_HORIZONTAL);
      FrameLayout circle = new FrameLayout(this);
      circle.setBackground(Ui.circle(p.surface2));
      View glyph = icon.equals("instagram") || icon.equals("facebook") || icon.equals("whatsapp")
          ? new ProfileShare.Glyph(this, icon, p.ink) : new IconView(this, icon, p.ink);
      circle.addView(glyph, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
      item.addView(circle, new LinearLayout.LayoutParams(dp(56), dp(56)));
      TextView label = Ui.text(this, (String) targets[i][1], 13, p.ink, false);
      label.setPadding(0, dp(8), 0, dp(12));
      item.addView(label);
      Runnable action = (Runnable) targets[i][2];
      item.setOnClickListener(v -> action.run());
      item.setContentDescription((String) targets[i][1]);
      if (icon.equals("link") && account.isEmpty()) item.setAlpha(0.4f);
      items[i] = item;
    }
    box.addView(Ui.grid(this, items, 4));
    sheet(box);
  }

  /** Wordmark strip, photo, name, city and role, and the "Your cricket matters" footer; corners come from drawables. */
  private LinearLayout shareCard() {
    PlayerProfile attrs = store.attributes();
    LinearLayout card = Ui.column(this);
    LinearLayout brand = Ui.row(this);
    brand.setGravity(Gravity.CENTER);
    brand.setBackground(corners(p.accent, dp(18), dp(18), 0, 0));
    brand.setPadding(0, dp(10), 0, dp(10));
    TextView mark = Ui.text(this, "B", 13, p.accent, true);
    mark.setGravity(Gravity.CENTER);
    mark.setBackground(Ui.circle(p.onAccent));
    brand.addView(mark, new LinearLayout.LayoutParams(dp(22), dp(22)));
    TextView word = Ui.text(this, "Boundary", 16, p.onAccent, true);
    word.setPadding(dp(8), 0, 0, 0);
    brand.addView(word);
    card.addView(brand, new LinearLayout.LayoutParams(-1, -2));

    FrameLayout photo = new FrameLayout(this);
    photo.setBackground(coverGradient());
    Bitmap bitmap = store.hasPhoto() ? Ui.decode(store.photo(), dp(270)) : null;
    if (bitmap != null) {
      ImageView image = new ImageView(this);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setImageBitmap(bitmap);
      photo.addView(image, new FrameLayout.LayoutParams(-1, -1));
    } else {
      TextView initials = Ui.text(this, Ui.initials(store.name()), 72, 0xf2ffffff, true);
      photo.addView(initials, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }
    card.addView(photo, new LinearLayout.LayoutParams(-1, dp(260)));

    LinearLayout details = Ui.column(this);
    details.setBackgroundColor(p.surface2);
    details.setPadding(dp(16), dp(14), dp(16), dp(14));
    details.addView(Ui.text(this, person(), 18, p.ink, true));
    if (!attrs.city.isEmpty()) {
      TextView city = Ui.text(this, attrs.city, 13, p.muted, false);
      city.setPadding(0, dp(4), 0, 0);
      details.addView(city);
    }
    List<String> batting = ProfileStats.battingRole(attrs);
    if (!batting.isEmpty()) {
      LinearLayout line = parts(batting, 13, p.ink);
      line.setPadding(0, dp(10), 0, 0);
      details.addView(line);
    }
    if (!attrs.bowlingStyle.isEmpty()) {
      TextView bowl = Ui.text(this, attrs.bowlingStyle, 13, p.ink, false);
      bowl.setPadding(0, dp(batting.isEmpty() ? 10 : 4), 0, 0);
      details.addView(bowl);
    }
    card.addView(details, new LinearLayout.LayoutParams(-1, -2));

    TextView footer = Ui.text(this, "Your cricket matters", 13, p.accent, false);
    footer.setGravity(Gravity.CENTER);
    footer.setBackground(corners(p.accentSoft, 0, 0, dp(18), dp(18)));
    footer.setPadding(0, dp(10), 0, dp(10));
    card.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    return card;
  }

  private Bitmap cardImage(View card) { return ProfileShare.snapshot(card, 1080, p.surface); }

  /** Circular photo drawn with a shader (not outline clipping), so it survives rendering to an image. */
  private View roundPhoto(int sizeDp) {
    Bitmap b = store.hasPhoto() ? Ui.decode(store.photo(), dp(sizeDp)) : null;
    if (b == null) return Ui.avatar(this, p, null, store.name(), sizeDp);
    int side = Math.min(b.getWidth(), b.getHeight());
    Bitmap square = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
    RoundedBitmapDrawable round = RoundedBitmapDrawableFactory.create(getResources(), square);
    round.setCircular(true);
    ImageView v = new ImageView(this);
    v.setImageDrawable(round);
    return v;
  }

  private String followText(String account) {
    return account.isEmpty() ? "My cricket profile on Boundary" : "Follow me on Boundary: " + Social.qrPayload(account);
  }

  private void copyLink(String account) {
    if (account.isEmpty()) {
      toast("Link your phone account to get a profile link");
      return;
    }
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Boundary profile", Social.qrPayload(account)));
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast("Profile link copied");
  }

  private void shareImage(Bitmap image, String name, String text, String targetPackage) {
    try {
      ProfileShare.share(this, image, name, text, targetPackage);
    } catch (Exception e) {
      toast("Could not share: " + e.getMessage());
    }
  }

  private void saveImage(Bitmap image, String name) {
    Runnable save = () -> {
      try {
        ProfileShare.saveToGallery(this, image, name + "-" + System.currentTimeMillis());
        toast("Saved to Pictures/Boundary");
      } catch (Exception e) {
        toast("Could not save: " + e.getMessage());
      }
    };
    if (ProfileShare.needsStoragePermission()
        && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      afterStoragePermission = save;
      storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
      return;
    }
    save.run();
  }

  // ---------------------------------------------------------------- helpers

  private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

  private int dp(float v) { return Ui.dp(this, v); }

  private static GradientDrawable corners(int color, float topLeft, float topRight, float bottomRight, float bottomLeft) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadii(new float[] {topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft});
    return d;
  }

  /** The colour mixed toward black by {@code amount} (0 to 1). */
  private static int shade(int color, float amount) {
    float keep = 1 - amount;
    return Color.argb(Color.alpha(color), Math.round(Color.red(color) * keep), Math.round(Color.green(color) * keep),
        Math.round(Color.blue(color) * keep));
  }
}
