package com.example.madfinalproject;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Career;
import com.example.madfinalproject.domain.Career.Ball;
import com.example.madfinalproject.domain.Career.BatInnings;
import com.example.madfinalproject.domain.Career.BatStats;
import com.example.madfinalproject.domain.Career.BowlInnings;
import com.example.madfinalproject.domain.Career.BowlStats;
import com.example.madfinalproject.domain.Career.MatchRow;
import com.example.madfinalproject.domain.Career.Partnership;
import com.example.madfinalproject.domain.Cricket.Match;
import com.example.madfinalproject.domain.PerformanceInsights;
import com.example.madfinalproject.domain.PlayerProfile;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * My Performance: the owner's batting, bowling and head-to-head analytics, laid out like the reference screens
 * (photo header, Batting / Bowling / Compare tabs, one block per analysis) in Boundary's palette. Sections whose
 * data Boundary does not record are left out, and undefined numbers show as "-". Given a player and team, it shows
 * that squad place instead, unless the place is the owner's own.
 */
public final class PerformanceActivity extends AppCompatActivity {
  public static final String EXTRA_TAB = "tab", EXTRA_SHEET = "sheet", EXTRA_PLAYER = "player", EXTRA_TEAM = "team";
  private static final String[] TABS = {"Batting", "Bowling", "Compare"};
  private static final String[] INNINGS = {"All innings", "1st innings", "2nd innings"};
  private static final String[] RUN_LABELS = {"Dots", "1s", "2s", "3s", "4s", "6s"};
  private static final int WHITE = 0xffffffff, FORM = 5, STYLE_BALLS = 60;

  private Ui.Palette p;
  private Repository repo;
  private ProfileStore me;
  private PlayerProfile profile;
  private Career.Who who;
  private boolean owner;
  private List<Match> matches;
  private Map<String, PlayerProfile> profiles;
  private final Career.Filter filter = new Career.Filter();
  private final Map<String, Career> careers = new HashMap<>();
  private Career base, career;
  private String tab = TABS[0];
  private String[] rival;
  private boolean compareBowling;
  private StringBuilder shareText;
  private final SimpleDateFormat dates = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

  private LinearLayout root, toolbar, heroDetails, tabStrip, content;
  private NestedScrollView scroll;
  private TextView badge;

  @Override protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    p = Ui.palette(this);
    repo = new Repository(this);
    me = new ProfileStore(this);
    who = me.who(repo);
    String player = getIntent().getStringExtra(EXTRA_PLAYER), team = getIntent().getStringExtra(EXTRA_TEAM);
    owner = TextUtils.isEmpty(player) || TextUtils.isEmpty(team) || who.keys.contains(Career.key(team, player));
    if (owner) profile = me.attributes();
    else {
      who = new Career.Who(player).add(team, player);
      profile = ProfileStore.find(repo, team, player);
    }
    matches = repo.matches();
    profiles = ProfileStore.all(repo, me);
    String requested = getIntent().getStringExtra(EXTRA_TAB);
    if (Arrays.asList(TABS).contains(requested)) tab = requested;
    if (saved != null) {
      tab = saved.getString("tab", tab);
      filter.ballType = saved.getString("ballType", "");
      filter.matchType = saved.getString("matchType", "");
      filter.overs = saved.getInt("overs");
      rival = saved.getStringArray("rival");
      compareBowling = saved.getBoolean("compareBowling");
    }
    base = careerFor(who, new Career.Filter());
    career = careerFor(who, filter);
    edgeToEdge();
    build();
    if (saved == null && "filter".equals(getIntent().getStringExtra(EXTRA_SHEET)) && !who.keys.isEmpty())
      root.post(this::filterSheet);
  }

  /** Opens the stats of one squad place; the owner's own places open the owner's view. */
  public static void open(Context context, String team, String name) {
    context.startActivity(new Intent(context, PerformanceActivity.class).putExtra(EXTRA_TEAM, team).putExtra(EXTRA_PLAYER, name));
  }

  @Override protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("tab", tab);
    out.putString("ballType", filter.ballType);
    out.putString("matchType", filter.matchType);
    out.putInt("overs", filter.overs);
    if (rival != null) out.putStringArray("rival", rival);
    out.putBoolean("compareBowling", compareBowling);
  }

  @Override protected void onRestart() {
    super.onRestart();
    if (who.keys.isEmpty() && !me.who(repo).keys.isEmpty()) recreate(); // linked or claimed a squad place meanwhile
  }

  @Override protected void onDestroy() {
    repo.close();
    super.onDestroy();
  }

  // ================================================================ frame

  private int dp(float v) { return Ui.dp(this, v); }

  private void edgeToEdge() {
    Window window = getWindow();
    WindowCompat.setDecorFitsSystemWindows(window, false);
    window.setStatusBarColor(Color.TRANSPARENT);
    window.setNavigationBarColor(p.bg);
    WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, window.getDecorView());
    bars.setAppearanceLightStatusBars(false);
    bars.setAppearanceLightNavigationBars(!p.dark);
  }

  private GradientDrawable headerGradient() {
    return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        p.dark ? new int[] {0xff0a2a1f, 0xff156446} : new int[] {0xff0d4f37, 0xff147653});
  }

  private void build() {
    root = Ui.column(this);
    root.setBackgroundColor(p.bg);
    setContentView(root);
    toolbar = Ui.column(this);
    toolbar.setBackground(headerGradient());
    LinearLayout bar = Ui.row(this);
    bar.setPadding(dp(4), 0, dp(6), 0);
    bar.addView(Ui.iconButton(this, p, "back", 44, Color.TRANSPARENT, WHITE, this::finish));
    TextView name = Ui.text(this, who.display, 21, WHITE, true);
    name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    name.setPadding(dp(10), 0, dp(10), 0);
    bar.addView(name, Ui.weight(1));
    if (!who.keys.isEmpty()) bar.addView(filterButton());
    toolbar.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));
    root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

    content = Ui.column(this);
    scroll = new NestedScrollView(this);
    scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));
    if (who.keys.isEmpty()) {
      root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
      emptyIdentity();
    } else {
      CoordinatorLayout coordinator = new CoordinatorLayout(this);
      root.addView(coordinator, new LinearLayout.LayoutParams(-1, 0, 1));
      AppBarLayout appBar = new AppBarLayout(this);
      appBar.setBackground(headerGradient());
      appBar.setLiftOnScroll(false);
      appBar.setStateListAnimator(null);
      appBar.setElevation(0);
      AppBarLayout.LayoutParams heroParams = new AppBarLayout.LayoutParams(-1, -2);
      heroParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL); // photo scrolls away, tabs stay
      appBar.addView(hero(), heroParams);
      tabStrip = Ui.row(this);
      tabStrip.setBackgroundColor(p.surface);
      appBar.addView(tabStrip, new AppBarLayout.LayoutParams(-1, -2));
      coordinator.addView(appBar, new CoordinatorLayout.LayoutParams(-1, -2));
      CoordinatorLayout.LayoutParams scrollParams = new CoordinatorLayout.LayoutParams(-1, -1);
      scrollParams.setBehavior(new AppBarLayout.ScrollingViewBehavior());
      coordinator.addView(scroll, scrollParams);
      showTab(tab);
    }
    ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      root.setPadding(bars.left, 0, bars.right, 0);
      toolbar.setPadding(0, bars.top, 0, 0);
      content.setPadding(0, 0, 0, dp(28) + bars.bottom);
      return WindowInsetsCompat.CONSUMED;
    });
    ViewCompat.requestApplyInsets(root);
  }

  private View filterButton() {
    FrameLayout box = new FrameLayout(this);
    box.addView(Ui.iconButton(this, p, "filter", 44, Color.TRANSPARENT, WHITE, this::filterSheet),
        new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
    badge = Ui.text(this, "", 10, 0xff0d4f37, true);
    badge.setGravity(Gravity.CENTER);
    badge.setBackground(Ui.circle(WHITE));
    box.addView(badge, new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.TOP | Gravity.END));
    box.setLayoutParams(new LinearLayout.LayoutParams(dp(50), dp(48)));
    box.setContentDescription("Filters");
    updateBadge();
    return box;
  }

  private int activeFilters() {
    return (filter.ballType.isEmpty() ? 0 : 1) + (filter.matchType.isEmpty() ? 0 : 1) + (filter.overs == 0 ? 0 : 1);
  }

  private void updateBadge() {
    if (badge == null) return;
    int n = activeFilters();
    badge.setText(String.valueOf(n));
    badge.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
  }

  /** Square photo and the recorded playing style, on the gradient that continues from the toolbar. */
  private View hero() {
    FrameLayout frame = new FrameLayout(this);
    frame.addView(new Glow(this), new FrameLayout.LayoutParams(-1, -1));
    LinearLayout row = Ui.row(this);
    row.setGravity(Gravity.TOP);
    row.setPadding(dp(16), dp(2), dp(16), dp(22));
    int size = dp(116);
    File photo = me.photo();
    Bitmap bitmap = owner && photo.exists() ? Ui.decode(photo, size) : null;
    View picture;
    if (bitmap != null) {
      ImageView image = new ImageView(this);
      image.setImageBitmap(bitmap);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setBackground(Ui.round(0x33ffffff, dp(8)));
      image.setClipToOutline(true);
      picture = image;
    } else {
      TextView initials = Ui.text(this, Ui.initials(who.display), 36, WHITE, true);
      initials.setGravity(Gravity.CENTER);
      initials.setBackground(Ui.round(0x29ffffff, dp(8)));
      picture = initials;
    }
    row.addView(picture, new LinearLayout.LayoutParams(size, size));
    heroDetails = Ui.column(this);
    heroDetails.setPadding(dp(18), dp(4), 0, 0);
    row.addView(heroDetails, Ui.weight(1));
    frame.addView(row, new FrameLayout.LayoutParams(-1, -2));
    return frame;
  }

  private void heroLines() {
    heroDetails.removeAllViews();
    boolean batting = !tab.equals("Bowling"), bowling = !tab.equals("Batting");
    List<String[]> lines = new ArrayList<>();
    if (batting && !TextUtils.isEmpty(profile.battingHand)) lines.add(new String[] {"Batting style", profile.battingHand});
    if (batting && !TextUtils.isEmpty(profile.battingOrder)) lines.add(new String[] {"Batting order", profile.battingOrder});
    if (bowling && !TextUtils.isEmpty(profile.bowlingStyle)) lines.add(new String[] {"Bowling style", profile.bowlingStyle});
    for (String[] line : lines) {
      TextView label = Ui.text(this, line[0], 14, 0xd9ffffff, false);
      label.setPadding(0, heroDetails.getChildCount() == 0 ? 0 : dp(14), 0, dp(4));
      heroDetails.addView(label);
      heroDetails.addView(Ui.text(this, line[1], 16, WHITE, true));
    }
    if (lines.isEmpty() && owner)
      heroDetails.addView(Ui.text(this, "Add your batting and bowling style in your cricket profile.", 14, 0xd9ffffff, false));
  }

  private void showTab(String name) {
    tab = name;
    tabStrip.removeAllViews();
    for (String t : TABS) {
      boolean on = t.equals(tab);
      FrameLayout cell = new FrameLayout(this);
      TextView label = Ui.text(this, t, 16, on ? p.ink : p.muted, on);
      label.setGravity(Gravity.CENTER);
      label.setPadding(0, dp(15), 0, dp(15));
      cell.addView(label, new FrameLayout.LayoutParams(-1, -2));
      if (on) {
        View underline = new View(this);
        underline.setBackground(Ui.round(p.accent, dp(2)));
        cell.addView(underline, new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM));
      }
      cell.setContentDescription(t);
      cell.setOnClickListener(v -> { if (!t.equals(tab)) showTab(t); });
      tabStrip.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }
    heroLines();
    renderTab();
    scroll.scrollTo(0, 0);
  }

  private void renderTab() {
    content.removeAllViews();
    if (tab.equals("Bowling")) bowling();
    else if (tab.equals("Compare")) compare();
    else batting();
  }

  /** Re-renders the current tab where the reader was, e.g. after a toggle inside it. */
  private void rerender() {
    int y = scroll.getScrollY();
    renderTab();
    scroll.post(() -> scroll.scrollTo(0, y));
  }

  // ================================================================ careers and filters

  private Career careerFor(Career.Who person, Career.Filter f) {
    String key = TextUtils.join(",", person.keys) + "|" + f.ballType + "|" + f.matchType + "|" + f.pitchType + "|" + f.overs;
    Career c = careers.get(key);
    if (c == null) {
      c = Career.of(matches, person, f);
      careers.put(key, c);
    }
    return c;
  }

  /** One section's own selections. They start from the header filter and replace its values in that section. */
  private final class Picks {
    String ballType = filter.ballType, matchType = filter.matchType, style = "", batter = "";
    int overs = filter.overs, innings, mode, metric;

    Career.Filter filter() {
      Career.Filter f = new Career.Filter();
      f.ballType = ballType;
      f.matchType = matchType;
      f.pitchType = PerformanceActivity.this.filter.pitchType;
      f.overs = overs;
      return f;
    }

    Career career() { return careerFor(who, filter()); }
  }

  private List<String> recorded(String[] known, Function<Match, String> field) {
    Set<String> seen = new HashSet<>();
    for (MatchRow r : base.matches) seen.add(field.apply(r.match));
    List<String> codes = new ArrayList<>();
    for (String code : known) if (seen.contains(code)) codes.add(code);
    return codes;
  }

  private List<String> ballTypes() { return recorded(PlayerProfile.BALL_TYPES, Match::ballType); }

  private List<String> matchTypes() { return recorded(PlayerProfile.MATCH_TYPES, Match::matchType); }

  private List<Integer> oversOptions() {
    TreeSet<Integer> overs = new TreeSet<>();
    for (MatchRow r : base.matches) overs.add(r.match.rules.overs);
    return new ArrayList<>(overs);
  }

  private static List<String> withAll(List<String> codes) {
    List<String> all = new ArrayList<>();
    all.add("");
    all.addAll(codes);
    return all;
  }

  private static String[] labels(List<String> codes, String all) {
    String[] labels = new String[codes.size()];
    for (int n = 0; n < labels.length; n++) labels[n] = codes.get(n).isEmpty() ? all : PlayerProfile.label(codes.get(n));
    return labels;
  }

  private String describeFilter() {
    List<String> parts = new ArrayList<>();
    if (!filter.ballType.isEmpty()) parts.add(PlayerProfile.label(filter.ballType));
    if (!filter.matchType.isEmpty()) parts.add(PlayerProfile.label(filter.matchType));
    if (filter.overs != 0) parts.add(filter.overs + " overs");
    return parts.isEmpty() ? "" : " (" + TextUtils.join(", ", parts) + ")";
  }

  private void filterSheet() {
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    Career.Filter draft = new Career.Filter();
    draft.ballType = filter.ballType;
    draft.matchType = filter.matchType;
    draft.overs = filter.overs;
    LinearLayout box = Ui.column(this);
    box.setBackground(topRounded(p.surface));
    box.setPadding(dp(20), dp(14), dp(20), dp(24));
    LinearLayout head = Ui.row(this);
    head.addView(Ui.text(this, "Filters", 21, p.ink, true), Ui.weight(1));
    head.addView(Ui.iconButton(this, p, "close", 40, p.surface2, p.ink, sheet::dismiss));
    box.addView(head);
    TextView hint = Ui.text(this, "Applies to every section on Batting, Bowling and Compare.", 14, p.muted, false);
    hint.setPadding(0, dp(6), 0, 0);
    box.addView(hint);
    LinearLayout groups = Ui.column(this);
    box.addView(groups);
    Runnable[] render = new Runnable[1];
    render[0] = () -> {
      groups.removeAllViews();
      chipGroup(groups, "Ball type", withAll(ballTypes()), "All ball types", draft.ballType, code -> { draft.ballType = code; render[0].run(); });
      chipGroup(groups, "Match type", withAll(matchTypes()), "All match types", draft.matchType, code -> { draft.matchType = code; render[0].run(); });
      List<String> overs = new ArrayList<>();
      overs.add("");
      for (int o : oversOptions()) overs.add(String.valueOf(o));
      chipGroup(groups, "Overs", overs, "All overs", draft.overs == 0 ? "" : String.valueOf(draft.overs),
          code -> { draft.overs = code.isEmpty() ? 0 : Integer.parseInt(code); render[0].run(); });
    };
    render[0].run();
    LinearLayout actions = Ui.row(this);
    actions.setPadding(0, dp(24), 0, 0);
    actions.addView(Ui.pill(this, p, "Clear all", false, () -> {
      draft.ballType = "";
      draft.matchType = "";
      draft.overs = 0;
      render[0].run();
    }), Ui.weight(1));
    actions.addView(Ui.space(this, 12, 1));
    actions.addView(Ui.pill(this, p, "Apply", true, () -> {
      filter.ballType = draft.ballType;
      filter.matchType = draft.matchType;
      filter.overs = draft.overs;
      career = careerFor(who, filter);
      updateBadge();
      sheet.dismiss();
      showTab(tab);
    }), Ui.weight(1));
    box.addView(actions);
    sheet.setContentView(box);
    clearSheetBackground(sheet, false);
    sheet.show();
  }

  private void chipGroup(LinearLayout into, String title, List<String> codes, String all, String selected, Consumer<String> pick) {
    TextView t = Ui.text(this, title, 15, p.ink, true);
    t.setPadding(0, dp(20), 0, dp(10));
    into.addView(t);
    if (codes.size() <= 1) {
      into.addView(Ui.text(this, "Not recorded in " + (owner ? "your" : first() + "'s") + " matches yet.", 14, p.muted, false));
      return;
    }
    HorizontalScrollView strip = new HorizontalScrollView(this);
    strip.setHorizontalScrollBarEnabled(false);
    LinearLayout row = Ui.row(this);
    for (String code : codes) {
      String label = code.isEmpty() ? all : title.equals("Overs") ? code + " overs" : PlayerProfile.label(code);
      row.addView(Ui.chip(this, p, label, code.equals(selected), () -> pick.accept(code)));
      row.addView(Ui.space(this, 8, 1));
    }
    strip.addView(row);
    into.addView(strip);
  }

  private GradientDrawable topRounded(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    float r = dp(22);
    d.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    return d;
  }

  private void clearSheetBackground(BottomSheetDialog sheet, boolean expand) {
    if (expand) sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    sheet.setOnShowListener(d -> {
      View frame = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
      if (frame != null) frame.setBackgroundColor(Color.TRANSPARENT);
    });
  }

  // ================================================================ section kit

  private interface Fill { void into(LinearLayout body, Runnable redraw); }

  private LinearLayout block() {
    LinearLayout box = Ui.column(this);
    box.setBackgroundColor(p.surface);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(10);
    content.addView(box, lp);
    return box;
  }

  /** Full-width block: title, explanation, share; the body redraws itself when one of its selectors changes. */
  private void section(String title, String info, Fill fill) {
    LinearLayout box = block();
    LinearLayout head = Ui.row(this);
    head.setPadding(dp(16), dp(8), dp(6), dp(8));
    TextView heading = Ui.text(this, title, 19, p.ink, true);
    heading.setMaxWidth(getResources().getDisplayMetrics().widthPixels - dp(132));
    head.addView(heading);
    head.addView(Ui.iconButton(this, p, "info", 36, Color.TRANSPARENT, p.muted, () -> explain(title, info)));
    head.addView(new View(this), Ui.weight(1));
    StringBuilder text = new StringBuilder();
    head.addView(Ui.iconButton(this, p, "share", 42, Color.TRANSPARENT, p.ink, () -> share(title, text.toString())));
    box.addView(head);
    box.addView(Ui.divider(this, p));
    LinearLayout body = Ui.column(this);
    body.setPadding(0, 0, 0, dp(18));
    box.addView(body);
    Runnable[] redraw = new Runnable[1];
    redraw[0] = () -> {
      body.removeAllViews();
      text.setLength(0);
      StringBuilder outer = shareText;
      shareText = text;
      try { fill.into(body, redraw[0]); } finally { shareText = outer; }
    };
    redraw[0].run();
  }

  private void explain(String title, String info) {
    new AlertDialog.Builder(this).setTitle(title).setMessage(info).setPositiveButton("Got it", null).show();
  }

  private void share(String title, String body) {
    String subject = who.display + (tab.equals("Compare") && rival != null ? " vs " + rival[1] : "") + " · " + title;
    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, subject + describeFilter() + "\n\n" + body.trim() + "\n\nShared from Boundary");
    startActivity(Intent.createChooser(send, "Share " + title));
  }

  /** Adds a line to the text the current section shares. */
  private void line(String s) {
    if (shareText != null && !s.isEmpty()) shareText.append(s).append('\n');
  }

  /** Underlined selector that opens a rounded dropdown list; the selected item is in the accent colour. */
  private View dropdown(String[] options, int selected, IntConsumer pick) {
    if (selected > 0) line("Showing: " + options[selected]);
    LinearLayout box = Ui.column(this);
    LinearLayout row = Ui.row(this);
    row.setPadding(0, dp(12), 0, dp(10));
    TextView label = Ui.text(this, options[selected], 15, p.ink, false);
    label.setSingleLine(true);
    label.setEllipsize(TextUtils.TruncateAt.END);
    row.addView(label, Ui.weight(1));
    IconView arrow = new IconView(this, "chevron-down", p.ink);
    row.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
    box.addView(row);
    box.addView(Ui.divider(this, p));
    box.setContentDescription(options[selected]);
    box.setOnClickListener(v -> popup(box, arrow, options, selected, pick));
    return box;
  }

  private void popup(View anchor, View arrow, String[] options, int selected, IntConsumer pick) {
    LinearLayout list = Ui.column(this);
    list.setPadding(0, dp(6), 0, dp(6));
    ScrollView frame = new ScrollView(this);
    frame.addView(list);
    int height = options.length > 7 ? dp(330) : ViewGroup.LayoutParams.WRAP_CONTENT;
    PopupWindow window = new PopupWindow(frame, Math.max(anchor.getWidth(), dp(180)), height, true);
    window.setBackgroundDrawable(Ui.outline(p.surface, p.line, Math.max(1, dp(1)), dp(14)));
    window.setElevation(dp(10));
    for (int n = 0; n < options.length; n++) {
      boolean on = n == selected;
      TextView item = Ui.text(this, options[n], 15, on ? p.accent : p.ink, on);
      item.setPadding(dp(16), dp(12), dp(16), dp(12));
      int index = n;
      item.setOnClickListener(v -> {
        window.dismiss();
        if (index != selected) pick.accept(index);
      });
      list.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }
    arrow.setRotation(180);
    window.setOnDismissListener(() -> arrow.setRotation(0));
    window.showAsDropDown(anchor, 0, dp(2));
  }

  /** Selectors two to a row, skipping the ones a section cannot offer. */
  private void selectors(LinearLayout body, View... views) {
    List<View> list = new ArrayList<>();
    for (View v : views) if (v != null) list.add(v);
    for (int n = 0; n < list.size(); n += 2) {
      LinearLayout row = Ui.row(this);
      row.setPadding(dp(16), dp(4), dp(16), 0);
      row.addView(list.get(n), Ui.weight(1));
      if (n + 1 < list.size()) {
        row.addView(Ui.space(this, 18, 1));
        row.addView(list.get(n + 1), Ui.weight(1));
      }
      body.addView(row);
    }
  }

  private View ballTypeDropdown(Picks k, Runnable redraw) {
    List<String> codes = withAll(ballTypes());
    if (codes.size() <= 1) return null;
    return dropdown(labels(codes, "All ball types"), Math.max(0, codes.indexOf(k.ballType)),
        n -> { k.ballType = codes.get(n); redraw.run(); });
  }

  private View matchTypeDropdown(Picks k, Runnable redraw) {
    List<String> codes = withAll(matchTypes());
    if (codes.size() <= 1) return null;
    return dropdown(labels(codes, "All match types"), Math.max(0, codes.indexOf(k.matchType)),
        n -> { k.matchType = codes.get(n); redraw.run(); });
  }

  private View oversDropdown(Picks k, Runnable redraw) {
    List<Integer> overs = oversOptions();
    if (overs.size() <= 1) return null;
    String[] labels = new String[overs.size() + 1];
    labels[0] = "All overs";
    for (int n = 0; n < overs.size(); n++) labels[n + 1] = overs.get(n) + " Ov.";
    return dropdown(labels, k.overs == 0 ? 0 : Math.max(0, overs.indexOf(k.overs) + 1),
        n -> { k.overs = n == 0 ? 0 : overs.get(n - 1); redraw.run(); });
  }

  private View inningsDropdown(Picks k, Runnable redraw) {
    return dropdown(INNINGS, k.innings, n -> { k.innings = n; redraw.run(); });
  }

  /** Bowling styles of the bowlers faced, only those recorded in player profiles. */
  private View styleDropdown(Picks k, Runnable redraw) {
    List<String> styles = withAll(new ArrayList<>(career.againstStyles(profiles, false).keySet()));
    if (styles.size() <= 1) return null;
    String[] labels = styles.toArray(new String[0]);
    labels[0] = "All bowling types";
    return dropdown(labels, Math.max(0, styles.indexOf(k.style)), n -> { k.style = styles.get(n); redraw.run(); });
  }

  private View batterDropdown(Picks k, Runnable redraw) {
    List<String> batters = PerformanceInsights.battersFaced(career.bowled);
    if (batters.isEmpty()) return null;
    List<String> names = withAll(batters.subList(0, Math.min(60, batters.size())));
    String[] labels = names.toArray(new String[0]);
    labels[0] = "All batters";
    return dropdown(labels, Math.max(0, names.indexOf(k.batter)), n -> { k.batter = names.get(n); redraw.run(); });
  }

  private void radios(LinearLayout body, String[] options, int selected, IntConsumer pick) {
    LinearLayout row = Ui.row(this);
    row.setPadding(dp(16), dp(14), dp(16), dp(2));
    for (int n = 0; n < options.length; n++) {
      boolean on = n == selected;
      LinearLayout option = Ui.row(this);
      FrameLayout ring = new FrameLayout(this);
      ring.setBackground(Ui.outline(Color.TRANSPARENT, on ? p.accent : p.muted, dp(2), dp(11)));
      if (on) {
        View dot = new View(this);
        dot.setBackground(Ui.circle(p.accent));
        ring.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));
      }
      option.addView(ring, new LinearLayout.LayoutParams(dp(22), dp(22)));
      TextView label = Ui.text(this, options[n], 15, p.ink, false);
      label.setPadding(dp(10), 0, 0, 0);
      option.addView(label);
      int index = n;
      option.setOnClickListener(v -> { if (index != selected) pick.accept(index); });
      option.setContentDescription(options[n] + (on ? ", selected" : ""));
      row.addView(option, Ui.weight(1));
    }
    body.addView(row);
    line("View: " + options[selected]);
  }

  private void add(LinearLayout body, View v) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(16), dp(10), dp(16), 0);
    body.addView(v, lp);
  }

  /** The reference's insight divider: a rule with the chart glyph in the middle. */
  private void insights(LinearLayout body) {
    LinearLayout row = Ui.row(this);
    row.setPadding(dp(56), dp(20), dp(56), dp(4));
    View left = new View(this), right = new View(this);
    left.setBackgroundColor(p.line);
    right.setBackgroundColor(p.line);
    row.addView(left, new LinearLayout.LayoutParams(0, Math.max(1, dp(1)), 1));
    LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(22), dp(22));
    il.setMargins(dp(10), 0, dp(10), 0);
    row.addView(new IconView(this, "performance", p.accent), il);
    row.addView(right, new LinearLayout.LayoutParams(0, Math.max(1, dp(1)), 1));
    body.addView(row);
  }

  private void insight(LinearLayout body, String value, String label) {
    add(body, Ui.insight(this, p, value, label));
    line(value + "  " + label);
  }

  /** Insight sentence; every second part is highlighted. */
  private void say(LinearLayout body, String... parts) {
    TextView t;
    if (parts.length == 3) t = Ui.highlight(this, p, parts[0], parts[1], parts[2]);
    else {
      SpannableStringBuilder s = new SpannableStringBuilder();
      for (int n = 0; n < parts.length; n++) {
        int start = s.length();
        s.append(parts[n]);
        if (n % 2 == 1 && s.length() > start) {
          s.setSpan(new ForegroundColorSpan(p.accent), start, s.length(), 0);
          s.setSpan(new StyleSpan(Typeface.BOLD), start, s.length(), 0);
        }
      }
      t = Ui.text(this, "", 15, p.ink, false);
      t.setText(s);
      t.setBackground(Ui.round(p.surface2, dp(12)));
      t.setPadding(dp(16), dp(14), dp(16), dp(14));
    }
    add(body, t);
    line(TextUtils.join("", parts));
  }

  private void note(LinearLayout body, String text) {
    TextView t = Ui.text(this, text, 13, p.muted, false);
    t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
    t.setPadding(dp(16), dp(14), dp(16), 0);
    body.addView(t);
    line(text);
  }

  private void link(LinearLayout body, String label, Runnable action) {
    TextView t = Ui.text(this, label, 15, p.accent, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(0, dp(14), 0, dp(6));
    t.setOnClickListener(v -> action.run());
    body.addView(t, new LinearLayout.LayoutParams(-1, -2));
  }

  /** Stat tiles from value, label pairs. */
  private void tiles(LinearLayout body, int columns, String... pairs) {
    View[] items = new View[pairs.length / 2];
    for (int n = 0; n < items.length; n++) {
      items[n] = Ui.tile(this, p, pairs[2 * n], pairs[2 * n + 1]);
      line(pairs[2 * n + 1].replace('\n', ' ') + ": " + pairs[2 * n]);
    }
    LinearLayout grid = Ui.grid(this, items, columns);
    grid.setPadding(dp(12), dp(10), dp(12), 0);
    body.addView(grid);
  }

  /** "Total runs given:  584" with an optional selector beside it. */
  private void totalRow(LinearLayout body, String label, String value, View selector) {
    LinearLayout row = Ui.row(this);
    row.setPadding(dp(16), selector == null ? dp(16) : dp(4), dp(16), 0);
    SpannableStringBuilder s = new SpannableStringBuilder(label + "  ");
    s.setSpan(new StyleSpan(Typeface.ITALIC), 0, label.length(), 0);
    int start = s.length();
    s.append(value);
    s.setSpan(new StyleSpan(Typeface.BOLD), start, s.length(), 0);
    TextView t = Ui.text(this, "", 15, p.ink, false);
    t.setText(s);
    row.addView(t, Ui.weight(1));
    if (selector != null) {
      row.addView(Ui.space(this, 12, 1));
      row.addView(selector, Ui.weight(1));
    }
    body.addView(row);
    line(label + " " + value);
  }

  private View table(String[] head, List<CharSequence[]> rows, float[] weights, boolean tintedHead, IntConsumer onHead, IntConsumer onRow) {
    LinearLayout t = Ui.column(this);
    LinearLayout h = Ui.row(this);
    if (tintedHead) h.setBackgroundColor(p.surface2);
    h.setPadding(dp(12), dp(12), dp(12), dp(12));
    for (int n = 0; n < head.length; n++) {
      TextView cell = Ui.text(this, head[n], 13, p.muted, false);
      cell.setGravity(n == 0 ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
      if (onHead != null) {
        int column = n;
        cell.setOnClickListener(v -> onHead.accept(column));
      }
      h.addView(cell, new LinearLayout.LayoutParams(0, -2, weights == null ? 1 : weights[n]));
    }
    t.addView(h);
    line(TextUtils.join(" | ", head));
    for (int r = 0; r < rows.size(); r++) {
      LinearLayout row = Ui.row(this);
      row.setBackgroundColor(p.bg);
      row.setPadding(dp(12), dp(14), dp(12), dp(14));
      CharSequence[] cells = rows.get(r);
      StringBuilder text = new StringBuilder();
      for (int n = 0; n < cells.length; n++) {
        TextView cell = Ui.text(this, "", 14, p.ink, false);
        cell.setText(cells[n]);
        cell.setGravity(n == 0 ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, weights == null ? 1 : weights[n]));
        text.append(n == 0 ? "" : " | ").append(cells[n]);
      }
      if (onRow != null) {
        int index = r;
        row.setOnClickListener(v -> onRow.accept(index));
      }
      t.addView(row);
      if (r < rows.size() - 1) t.addView(Ui.divider(this, p));
      line(text.toString());
    }
    return t;
  }

  private void chart(LinearLayout body, View chart) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(6), dp(14), dp(14), 0);
    body.addView(chart, lp);
  }

  private void pie(LinearLayout body, Map<String, Integer> counts, int[] colors) {
    String[] labels = new String[counts.size()];
    double[] values = new double[counts.size()];
    int total = 0;
    for (int v : counts.values()) total += v;
    StringBuilder text = new StringBuilder();
    int n = 0;
    for (Map.Entry<String, Integer> e : counts.entrySet()) {
      labels[n] = e.getKey() + " · " + e.getValue();
      values[n++] = e.getValue();
      text.append(e.getKey()).append(' ').append(e.getValue()).append(" (")
          .append(Math.round(e.getValue() * 100.0 / Math.max(1, total))).append("%), ");
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(10), dp(14), dp(12), 0);
    body.addView(new Charts.PieChartView(this, p).slices(labels, values, colors), lp);
    if (text.length() > 2) line(text.substring(0, text.length() - 2));
  }

  /** Run-type bars in the wagon wheel's colours (dots stay visible on the light surface). */
  private void runBars(LinearLayout body, double[] pct) {
    int[] w = WagonWheelView.CATEGORY_COLORS; // out, dots, 1s, 2s, 4s, 6s
    int[] colors = {p.dark ? w[1] : 0xffb4beb7, w[2], w[3], w[3], w[4], w[5]};
    chart(body, new Charts.BarChartView(this, p).bars(RUN_LABELS, pct, colors).axes("PERCENTAGE (%)", "RUNS")
        .tooltip((series, x) -> Math.round(pct[x]) + "% " + RUN_LABELS[x]));
    StringBuilder text = new StringBuilder();
    for (int n = 0; n < RUN_LABELS.length; n++) text.append(RUN_LABELS[n]).append(' ').append(Math.round(pct[n])).append("%  ");
    line(text.toString().trim());
  }

  private void empty(String title, String body) {
    LinearLayout box = block();
    box.setPadding(dp(20), dp(24), dp(20), dp(24));
    box.addView(Ui.text(this, title, 18, p.ink, true));
    boolean filtered = activeFilters() > 0;
    TextView t = Ui.text(this, body + (filtered ? " Some matches are hidden by your filters." : ""), 14, p.muted, false);
    t.setPadding(0, dp(8), 0, filtered ? dp(14) : 0);
    box.addView(t);
    if (filtered) box.addView(Ui.pill(this, p, "Clear filters", true, () -> {
      filter.ballType = "";
      filter.matchType = "";
      filter.overs = 0;
      career = careerFor(who, filter);
      updateBadge();
      showTab(tab);
    }), new LinearLayout.LayoutParams(-2, -2));
  }

  private void emptyIdentity() {
    LinearLayout box = block();
    box.setPadding(dp(20), dp(24), dp(20), dp(24));
    box.addView(new IconView(this, "performance", p.accent), new LinearLayout.LayoutParams(dp(40), dp(40)));
    TextView title = Ui.text(this, "Your performance starts with your matches", 19, p.ink, true);
    title.setPadding(0, dp(14), 0, dp(8));
    box.addView(title);
    box.addView(Ui.text(this, "Boundary needs to know which squad places are you. In Teams, open a team, tap your name "
        + "and choose \"This is me\". Or link your Google player account, and every squad place registered with your "
        + "account counts as you.", 14, p.muted, false));
    if (!PlayerAccount.linked(this)) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.topMargin = dp(16);
      box.addView(Ui.pill(this, p, "Link Google account", true,
          () -> startActivity(new Intent(this, PlayerAccountActivity.class))), lp);
    }
  }

  // ================================================================ shared helpers

  private String first() { return firstOf(who.display); }

  private static String firstOf(String name) {
    String n = name.trim();
    int space = n.indexOf(' ');
    return space > 0 ? n.substring(0, space) : n;
  }

  private static String score(BatInnings b) { return b.runs + "(" + b.balls + ")" + (b.out ? "" : "*"); }

  private String date(Match m) { return dates.format(new Date(m.played())); }

  /** "TeamA vs TeamB" with the opponent in the accent colour. */
  private CharSequence fixture(MatchRow r) {
    String a = r.match.teamA, b = r.match.teamB;
    SpannableStringBuilder s = new SpannableStringBuilder(a + " vs " + b);
    int start = r.opponent.equals(a) ? 0 : a.length() + 4;
    s.setSpan(new ForegroundColorSpan(p.accent), start, start + r.opponent.length(), 0);
    return s;
  }

  private void openMatch(Match m) {
    startActivity(new Intent(this, MatchDetailActivity.class).putExtra(MatchDetailActivity.EXTRA_MATCH_ID, m.id));
  }

  /** Whole numbers without decimals, anything else to one decimal place, "-" when undefined. */
  private static String num(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return "-";
    return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf(Math.round(v)) : String.format(Locale.US, "%.1f", v);
  }

  private static String one(double v) {
    return Double.isNaN(v) || Double.isInfinite(v) ? "-" : String.format(Locale.US, "%.1f", v);
  }

  private static List<Ball> struck(List<Ball> balls) {
    List<Ball> own = new ArrayList<>();
    for (Ball b : balls) if (PerformanceInsights.struck(b)) own.add(b);
    return own;
  }

  /** Deliveries faced in this section's selection, against one recorded bowling style if picked. */
  private List<Ball> faced(Picks k) {
    List<Ball> balls = new ArrayList<>();
    for (Ball b : k.career().faced) {
      if (!k.style.isEmpty()) {
        PlayerProfile bowler = profiles.get(Career.key(b.row.opponent, b.bowler));
        if (bowler == null || !k.style.equals(bowler.bowlingStyle)) continue;
      }
      balls.add(b);
    }
    return balls;
  }

  /** Deliveries bowled in this section's selection, to one batter if picked. */
  private List<Ball> bowled(Picks k) {
    List<Ball> balls = new ArrayList<>();
    for (Ball b : k.career().bowled) if (k.batter.isEmpty() || k.batter.equals(b.batter)) balls.add(b);
    return balls;
  }

  private static String categoryName(String category) {
    switch (category) {
      case WagonWheelView.OUT: return "Outs";
      case WagonWheelView.DOTS: return "Dot balls";
      case WagonWheelView.ONES: return "Singles";
      case WagonWheelView.TWOS: return "Twos and threes";
      case WagonWheelView.FOURS: return "Fours";
      default: return "Sixes";
    }
  }

  /** Wagon wheel with its colour legend; tapping a colour shows only those shots. */
  private void wheel(LinearLayout body, List<WagonWheelView.Shot> shots, boolean leftHanded, String[] only, Runnable redraw) {
    WagonWheelView view = new WagonWheelView(this);
    view.setShots(shots, leftHanded);
    if (!only[0].isEmpty()) {
      view.setVisible(Collections.singleton(only[0]));
      int count = 0;
      for (WagonWheelView.Shot s : shots) if (s.category().equals(only[0])) count++;
      LinearLayout row = Ui.row(this);
      row.setPadding(dp(16), dp(14), dp(16), 0);
      row.addView(Ui.text(this, categoryName(only[0]) + " : " + count, 15, p.ink, false), Ui.weight(1));
      TextView reset = Ui.text(this, "Reset", 15, p.accent, true);
      reset.setPaintFlags(reset.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
      reset.setOnClickListener(v -> { only[0] = ""; redraw.run(); });
      row.addView(reset);
      body.addView(row);
      line(categoryName(only[0]) + ": " + count);
    }
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(16), dp(14), dp(16), 0);
    body.addView(view, lp);
    LinearLayout legend = Ui.row(this);
    legend.setPadding(dp(12), dp(14), dp(12), 0);
    for (int n = 0; n < WagonWheelView.CATEGORIES.length; n++) {
      String category = WagonWheelView.CATEGORIES[n];
      int color = WagonWheelView.CATEGORY_COLORS[n];
      TextView pill = Ui.text(this, category, 13, Charts.onColor(color), true);
      pill.setGravity(Gravity.CENTER);
      pill.setPadding(0, dp(13), 0, dp(13));
      pill.setBackground(color == WHITE && !p.dark ? Ui.outline(color, p.line, Math.max(1, dp(1)), dp(22)) : Ui.round(color, dp(22)));
      pill.setAlpha(only[0].isEmpty() || only[0].equals(category) ? 1f : 0.35f);
      pill.setContentDescription("Show only " + categoryName(category));
      pill.setOnClickListener(v -> { only[0] = only[0].equals(category) ? "" : category; redraw.run(); });
      LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, -2, 1);
      pl.setMargins(dp(3), 0, dp(3), 0);
      legend.addView(pill, pl);
    }
    body.addView(legend);
    int[] runs = new int[8];
    for (WagonWheelView.Shot s : shots)
      if (s.zone >= 0 && s.zone < 8 && view.visible().contains(s.category())) runs[s.zone] += s.runs;
    List<String> zones = new ArrayList<>();
    for (int z = 0; z < 8; z++) if (runs[z] > 0) zones.add(Career.ZONES[z] + " " + runs[z]);
    line(zones.isEmpty() ? "No scoring regions recorded" : "Runs by region: " + TextUtils.join(", ", zones));
  }

  /** Bar chart rows for each label: one chart per measure, the last one carrying the x-axis title. */
  private void measureCharts(LinearLayout body, String[] labels, double[][] measures, String[] titles, int[] colors,
      boolean[] whole, String xTitle) {
    for (int m = 0; m < measures.length; m++) {
      double[] values = measures[m];
      String title = titles[m];
      boolean integers = whole[m];
      Charts.BarChartView chart = new Charts.BarChartView(this, p).bars(labels, values, colors[m])
          .axes(title, m == measures.length - 1 ? xTitle : "").height(m == measures.length - 1 ? 190 : 165)
          .tooltip((series, x) -> labels[x] + ": " + (integers ? num(values[x]) : Ui.decimal(values[x])) + " " + title.toLowerCase(Locale.ROOT));
      if (integers) chart.integers();
      chart(body, chart);
    }
    for (int n = 0; n < labels.length; n++) {
      StringBuilder text = new StringBuilder(labels[n]).append(": ");
      for (int m = 0; m < measures.length; m++)
        text.append(m == 0 ? "" : ", ").append(whole[m] ? num(measures[m][n]) : Ui.decimal(measures[m][n])).append(' ')
            .append(titles[m].toLowerCase(Locale.ROOT));
      line(text.toString());
    }
  }

  /** Bars per year with a metric selector, a table view, and the best year. */
  private void years(String[] metrics, String[] bestYear, Function<String, SortedMap<Integer, Double>> data) {
    Picks k = new Picks();
    boolean[] asTable = {false};
    section("Stats year-on-year", "Your numbers season by season. Pick the measure, or switch to a table.", (body, redraw) -> {
      LinearLayout row = Ui.row(this);
      row.setPadding(dp(16), dp(4), dp(16), 0);
      row.addView(dropdown(metrics, k.metric, n -> { k.metric = n; redraw.run(); }), Ui.weight(1));
      row.addView(Ui.space(this, 14, 1));
      row.addView(Ui.chip(this, p, asTable[0] ? "Chart" : "Table", false, () -> { asTable[0] = !asTable[0]; redraw.run(); }));
      body.addView(row);
      String metric = metrics[k.metric];
      boolean decimals = k.metric >= 2; // average, strike rate, economy
      SortedMap<Integer, Double> years = data.apply(metric.toLowerCase(Locale.ROOT));
      String[] labels = new String[years.size()];
      double[] values = new double[years.size()];
      int n = 0;
      for (Map.Entry<Integer, Double> e : years.entrySet()) { labels[n] = String.valueOf(e.getKey()); values[n++] = e.getValue(); }
      if (asTable[0]) {
        List<CharSequence[]> rows = new ArrayList<>();
        for (int y = 0; y < labels.length; y++) rows.add(new CharSequence[] {labels[y], decimals ? Ui.decimal(values[y]) : num(values[y])});
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        body.addView(table(new String[] {"Year", metric}, rows, null, true, null, null), lp);
      } else {
        chart(body, new Charts.BarChartView(this, p).bars(labels, values, Charts.BLUE).axes(metric, "Year")
            .tooltip((series, x) -> labels[x] + ": " + (decimals ? Ui.decimal(values[x]) : num(values[x]))));
        List<String> text = new ArrayList<>();
        for (int y = 0; y < labels.length; y++) text.add(labels[y] + " " + (decimals ? Ui.decimal(values[y]) : num(values[y])));
        line(metric + " by year: " + TextUtils.join(", ", text));
      }
      int best = PerformanceInsights.bestYear(years);
      if (best > 0) {
        insights(body);
        say(body, bestYear[k.metric], String.valueOf(best), "");
      }
    });
  }

  // ================================================================ batting

  private void batting() {
    if (career.batting.isEmpty()) {
      empty("No batting innings yet", owner ? "Innings you bat in show up here once a scored match includes you."
          : "Innings " + first() + " bats in show up here once a scored match includes them.");
      return;
    }
    battingForm();
    playingStyle();
    battingWheel();
    runTypes();
    battingPosition();
    battingByMatchInnings();
    againstBowlingTypes();
    paceVsSpin();
    battingPitch();
    outTypes();
    partnerships();
    years(new String[] {"Runs", "Innings", "Average", "Strike rate"},
        new String[] {"Scored maximum runs in ", "Played most innings in ", "Best batting average in ", "Best strike rate in "},
        metric -> PerformanceInsights.battingByYear(career, metric));
    battingOverall();
  }

  private void battingForm() {
    List<BatInnings> all = career.battingForm(Integer.MAX_VALUE);
    PerformanceInsights.BatForm f = PerformanceInsights.batForm(career, FORM);
    String last = "last " + f.innings + " innings";
    boolean[] expanded = {false};
    section("Current form", "Your latest batting innings, newest first. Score is runs (balls) and * means not out. "
        + "Tap an innings to open the match. The insights and tiles cover your last " + FORM + " innings.", (body, redraw) -> {
      List<BatInnings> shown = expanded[0] ? all : all.subList(0, Math.min(FORM, all.size()));
      List<CharSequence[]> rows = new ArrayList<>();
      for (BatInnings b : shown)
        rows.add(new CharSequence[] {score(b), fixture(b.row), String.valueOf(b.row.match.rules.overs), date(b.row.match)});
      body.addView(table(new String[] {"Score", "Innings", "Ov.", "Date"}, rows, new float[] {1.1f, 2.8f, 0.7f, 1.3f},
          false, null, n -> openMatch(shown.get(n).row.match)));
      if (all.size() > FORM) link(body, expanded[0] ? "Show less" : "View all", () -> { expanded[0] = !expanded[0]; redraw.run(); });
      insights(body);
      insight(body, String.valueOf(f.runs), "Total runs in " + last);
      insight(body, String.valueOf(f.caught), "Caught out in " + last);
      insight(body, String.valueOf(f.notOuts), "Not out in " + last);
      tiles(body, 2, Ui.decimal(f.strikeRate()), "SR", Ui.decimal(f.average()), "Avg", f.fours + "/" + f.sixes, "4's/6's",
          f.bestZone.isEmpty() ? "-" : f.bestZone,
          f.bestZone.isEmpty() ? "Most productive region" : "Most productive region\n" + f.bestZoneRuns + " runs");
    });
  }

  private void playingStyle() {
    Picks k = new Picks();
    section("Playing style", "How your innings build: the average runs you have after each ball faced, over the innings "
        + "that lasted that long. Tap a point to see runs(balls).", (body, redraw) -> {
      selectors(body, inningsDropdown(k, redraw));
      Career sub = career.inMatchInnings(k.innings);
      double[] curve = sub.playingStyle(STYLE_BALLS);
      if (curve.length == 0) {
        note(body, "No balls faced in this selection.");
        return;
      }
      chart(body, new Charts.LineChartView(this, p).values(curve).axes("Runs", "Balls").height(220)
          .tooltip((series, x) -> num(curve[x]) + "(" + (x + 1) + ")"));
      List<String> points = new ArrayList<>();
      for (int balls : new int[] {5, 10, 20, 30, 45, 60})
        if (balls <= curve.length) points.add(num(curve[balls - 1]) + " after " + balls + " balls");
      if (!points.isEmpty()) line("Average runs: " + TextUtils.join(", ", points));
      insights(body);
      insight(body, Ui.decimal(sub.strikeRateFirst(10)), "SR of first 10 balls");
    });
  }

  private void battingWheel() {
    Picks k = new Picks();
    String[] only = {""};
    section("Wagon wheel", "Where your runs go, from the fielding zone the scorer tapped for each scoring shot. Percentages "
        + "are each region's share of your runs. Tap a colour to see only those shots.", (body, redraw) -> {
      selectors(body, styleDropdown(k, redraw), ballTypeDropdown(k, redraw), matchTypeDropdown(k, redraw), oversDropdown(k, redraw));
      List<Ball> balls = faced(k);
      List<WagonWheelView.Shot> shots = new ArrayList<>();
      for (Ball b : balls) {
        boolean own = PerformanceInsights.struck(b);
        if (b.batterOut) shots.add(new WagonWheelView.Shot(own ? b.zone : -1, own ? b.batRuns : 0, true));
        else if (own && b.faced()) shots.add(new WagonWheelView.Shot(b.zone, b.batRuns, false));
      }
      wheel(body, shots, "LHB".equals(profile.battingHand), only, redraw);
      String best = Career.bestZone(Career.zoneRuns(struck(balls)));
      insights(body);
      if (best.isEmpty()) note(body, "No shot directions were recorded for this selection.");
      else {
        say(body, "Most productive region: ", best, "");
        String summary = WagonWheelView.summary(shots, Career.ZONES);
        if (!summary.isEmpty()) say(body, summary);
      }
    });
  }

  private void runTypes() {
    Picks k = new Picks();
    Map<String, int[]> against = career.againstBowlers();
    section("Types of runs", "The share of balls you faced that went for dots, 1s, 2s, 3s, 4s and 6s. Wides are not balls "
        + "faced and are left out. Tap a bar for its share.", (body, redraw) -> {
      selectors(body, styleDropdown(k, redraw));
      List<Ball> balls = struck(faced(k));
      if (balls.isEmpty()) note(body, "No balls faced in this selection.");
      else runBars(body, Career.runTypes(balls));
      String most = PerformanceInsights.leader(against, 0), dots = PerformanceInsights.leader(against, 3),
          boundaries = PerformanceInsights.leader(against, 4);
      if (most.isEmpty() && dots.isEmpty() && boundaries.isEmpty()) return;
      insights(body);
      if (!most.isEmpty()) say(body, "Most runs scored against ", most, "");
      if (!dots.isEmpty()) say(body, "Most dot balls against ", dots, "");
      if (!boundaries.isEmpty()) say(body, "Hits most boundaries against ", boundaries, "");
    });
  }

  private void battingPosition() {
    Picks k = new Picks();
    section("Batting position", "Innings, runs and strike rate at each place you have batted in the order. Slot-wise "
        + "groups the top (1-3), middle (4-7) and lower (8+) order.", (body, redraw) -> {
      selectors(body, inningsDropdown(k, redraw));
      selectors(body, matchTypeDropdown(k, redraw), oversDropdown(k, redraw));
      radios(body, new String[] {"All", "Slot-wise"}, k.mode, n -> { k.mode = n; redraw.run(); });
      Career sub = k.career().inMatchInnings(k.innings);
      SortedMap<Integer, int[]> positions = sub.battingPositions();
      Map<String, int[]> rows = new LinkedHashMap<>();
      if (k.mode == 1) rows.putAll(PerformanceInsights.orderSlots(positions));
      else if (!positions.isEmpty())
        for (int pos = 1; pos <= positions.lastKey(); pos++) rows.put(String.valueOf(pos), positions.getOrDefault(pos, new int[3]));
      if (positions.isEmpty() || rows.isEmpty()) {
        note(body, "No innings in this selection.");
        return;
      }
      String[] labels = rows.keySet().toArray(new String[0]);
      double[][] measures = new double[3][labels.length];
      int n = 0;
      for (int[] r : rows.values()) {
        measures[0][n] = r[0];
        measures[1][n] = r[1];
        measures[2][n++] = PerformanceInsights.ratio(r[1] * 100.0, r[2]);
      }
      measureCharts(body, labels, measures, new String[] {"Innings", "Runs", "Strike rate"},
          new int[] {p.accent, Charts.BLUE, Charts.AMBER}, new boolean[] {true, true, false}, "BATTING POSITION");
      List<Integer> preferred = sub.preferredPositions();
      if (preferred.isEmpty()) return;
      insights(body);
      insight(body, String.valueOf(preferred.get(0)), "Preferred batting position for " + first());
      if (preferred.size() > 1) insight(body, String.valueOf(preferred.get(1)), "2nd preferred batting position for " + first());
    });
  }

  private void battingByMatchInnings() {
    Picks k = new Picks();
    section("Performance by match innings", "Your batting when your team batted first (innings 1) and when it chased "
        + "(innings 2). The win % insights count only matches with a result.", (body, redraw) -> {
      selectors(body, matchTypeDropdown(k, redraw), oversDropdown(k, redraw));
      Career c = k.career();
      List<CharSequence[]> rows = new ArrayList<>();
      for (int number = 1; number <= 2; number++) {
        BatStats s = c.battingInMatchInnings(number);
        rows.add(new CharSequence[] {String.valueOf(number), String.valueOf(s.innings), String.valueOf(s.runs),
            Ui.decimal(s.average()), Ui.decimal(s.strikeRate()), String.valueOf(s.notOuts)});
      }
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.topMargin = dp(14);
      body.addView(table(new String[] {"Inns No.", "Inns", "Runs", "Avg", "SR", "NO"}, rows,
          new float[] {1.3f, 1, 1, 1, 1.1f, 0.8f}, true, null, null), lp);
      insights(body);
      insight(body, Ui.percent(c.winPercent(b -> b.index == 0 && b.runs >= 30)), "Win % if " + first() + " scores 30+ in the first innings");
      insight(body, Ui.percent(c.winPercent(b -> b.index == 1 && !b.out)), "Win % if " + first() + " is not out in a chase");
    });
  }

  private static double sortValue(double v) { return Double.isNaN(v) ? -1 : v; }

  private void againstBowlingTypes() {
    Map<String, int[]> styles = career.againstStyles(profiles, false);
    if (styles.isEmpty()) return;
    int[] sort = {0};
    section("Performance against bowling types", "Your average, strike rate and share of dismissals against each "
        + "bowling style. Only bowlers whose style is saved in their player profile are counted. Tap Avg. or SR to sort.",
        (body, redraw) -> {
      int outs = 0;
      for (int[] a : styles.values()) outs += a[2];
      List<String> order = new ArrayList<>(styles.keySet());
      if (sort[0] == 1) order.sort((x, y) -> Double.compare(sortValue(avg(styles.get(y))), sortValue(avg(styles.get(x)))));
      if (sort[0] == 2) order.sort((x, y) -> Double.compare(sortValue(sr(styles.get(y))), sortValue(sr(styles.get(x)))));
      List<CharSequence[]> rows = new ArrayList<>();
      for (String style : order) {
        int[] a = styles.get(style);
        rows.add(new CharSequence[] {style, Ui.decimal(avg(a)), Ui.decimal(sr(a)), Ui.percent(PerformanceInsights.ratio(a[2] * 100.0, outs))});
      }
      String[] head = {"Bowling type", "Avg." + (sort[0] == 1 ? " ↓" : ""), "SR" + (sort[0] == 2 ? " ↓" : ""), "Outs"};
      body.addView(table(head, rows, new float[] {2.3f, 1, 1, 1}, true,
          column -> { if (column == 1 || column == 2) { sort[0] = sort[0] == column ? 0 : column; redraw.run(); } }, null));
      note(body, "*Count may not tally: bowlers without a saved bowling style are left out.");
    });
  }

  /** {runs, balls, dismissals, ...} -> average and strike rate. */
  private static double avg(int[] a) { return PerformanceInsights.ratio(a[0], a[2]); }

  private static double sr(int[] a) { return PerformanceInsights.ratio(a[0] * 100.0, a[1]); }

  private void paceVsSpin() {
    Map<String, int[]> types = career.againstStyles(profiles, true);
    if (types.isEmpty()) return;
    section("Pace vs Spin", "Your batting against pace (fast and medium) and spin bowling. Only bowlers whose style is "
        + "saved in their player profile are counted.", (body, redraw) -> {
      List<CharSequence[]> rows = new ArrayList<>();
      for (String type : new String[] {"PACE", "SPIN"}) {
        int[] a = types.get(type);
        if (a == null) continue;
        rows.add(new CharSequence[] {PlayerProfile.label(type), String.valueOf(a[0]), Ui.decimal(avg(a)), Ui.decimal(sr(a)),
            String.valueOf(a[3]), String.valueOf(a[4]), String.valueOf(a[2])});
      }
      body.addView(table(new String[] {"Type", "Runs", "Avg.", "SR", "4s", "6s", "Outs"}, rows,
          new float[] {1.1f, 1, 1.1f, 1.2f, 0.7f, 0.7f, 0.9f}, true, null, null));
      note(body, "*Count may not tally: bowlers without a saved bowling style are left out.");
    });
  }

  private void battingPitch() {
    if (career.battingByPitch().isEmpty()) return;
    Picks k = new Picks();
    section("Pitch type analysis", "Your batting on each type of pitch. Only matches where the pitch type was recorded "
        + "are counted.", (body, redraw) -> {
      selectors(body, ballTypeDropdown(k, redraw), oversDropdown(k, redraw));
      Map<String, BatStats> by = k.career().battingByPitch();
      if (by.isEmpty()) note(body, "No recorded pitch types in this selection.");
      else {
        List<CharSequence[]> rows = new ArrayList<>();
        for (Map.Entry<String, BatStats> e : by.entrySet()) {
          BatStats s = e.getValue();
          rows.add(new CharSequence[] {PlayerProfile.label(e.getKey()), String.valueOf(s.innings), String.valueOf(s.runs),
              Ui.decimal(s.average()), Ui.decimal(s.strikeRate()), String.valueOf(s.notOuts)});
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        body.addView(table(new String[] {"Pitch type", "Inns", "Runs", "Avg", "SR", "NO"}, rows,
            new float[] {1.6f, 0.9f, 1, 1, 1.2f, 0.7f}, true, null, null), lp);
      }
      note(body, "*Count may not tally: matches without a recorded pitch type are left out.");
    });
  }

  private void outTypes() {
    Picks k = new Picks();
    section("Out type", "How you have been dismissed, as a share of all your dismissals.", (body, redraw) -> {
      Map<String, Integer> types = k.style.isEmpty() ? career.outTypes()
          : PerformanceInsights.outTypesAgainst(career, profiles, k.style);
      int total = 0;
      for (int v : types.values()) total += v;
      totalRow(body, "Total number of outs:", String.valueOf(total), styleDropdown(k, redraw));
      if (total == 0) note(body, "Not dismissed in this selection.");
      else pie(body, types, Charts.series(p));
      insights(body);
      double lose = PerformanceInsights.losePercent(career, b -> b.out && b.runs == 0);
      if (Double.isNaN(lose)) insight(body, "-", "Chances of the team losing when " + first() + " gets out for a duck");
      else say(body, "Chances of team losing are ", Ui.percent(lose), " if ", first(), " gets out for a duck");
    });
  }

  private void partnerships() {
    List<Partnership> top = career.topPartnerships(5);
    if (top.isEmpty()) return;
    section("Top 5 partnerships", "Your five biggest partnerships. Left: your runs (balls). Middle: the partnership, "
        + "extras included (balls). Right: your partner's runs (balls).", (body, redraw) -> {
      int max = 1;
      for (Partnership t : top) max = Math.max(max, Math.max(t.myRuns, t.partnerRuns));
      for (Partnership t : top) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(16), dp(12), dp(16), dp(4));
        row.addView(side(first(), Gravity.START), new LinearLayout.LayoutParams(0, -2, 1.1f));
        row.addView(meter(t.myRuns / (float) max, p.accent, true, t.myRuns + " (" + t.myBalls + ")"), new LinearLayout.LayoutParams(0, -2, 1.6f));
        TextView total = Ui.text(this, t.runs + " (" + t.balls + ")", 13, p.ink, true);
        total.setGravity(Gravity.CENTER);
        total.setPadding(0, dp(4), 0, 0);
        row.addView(total, new LinearLayout.LayoutParams(0, -2, 1.3f));
        row.addView(meter(t.partnerRuns / (float) max, Charts.AMBER, false, t.partnerRuns + " (" + t.partnerBalls + ")"), new LinearLayout.LayoutParams(0, -2, 1.6f));
        row.addView(side(t.partner, Gravity.END), new LinearLayout.LayoutParams(0, -2, 1.1f));
        body.addView(row);
        line(first() + " " + t.myRuns + " (" + t.myBalls + ") + " + t.partner + " " + t.partnerRuns + " (" + t.partnerBalls
            + ") = " + t.runs + " (" + t.balls + ")");
      }
    });
  }

  private TextView side(String name, int gravity) {
    TextView t = Ui.text(this, name, 13, p.muted, false);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setGravity(gravity);
    t.setPadding(gravity == Gravity.END ? dp(6) : 0, dp(4), gravity == Gravity.START ? dp(6) : 0, 0);
    return t;
  }

  private View meter(float fraction, int color, boolean fromEnd, String label) {
    LinearLayout column = Ui.column(this);
    column.addView(new Charts.Meter(this, fraction, color, fromEnd), new LinearLayout.LayoutParams(-1, -2));
    TextView text = Ui.text(this, label, 12, p.ink, false);
    text.setGravity(fromEnd ? Gravity.END : Gravity.START);
    text.setPadding(0, dp(4), 0, 0);
    column.addView(text, new LinearLayout.LayoutParams(-1, -2));
    return column;
  }

  private void battingOverall() {
    BatStats s = career.batStats();
    section("Overall stats", "Your batting totals across the matches shown.", (body, redraw) -> tiles(body, 3,
        String.valueOf(s.innings), "Innings", String.valueOf(s.notOuts), "Not out", String.valueOf(s.runs), "Runs",
        s.highestText(), "Highest Runs", Ui.decimal(s.average()), "Avg", Ui.decimal(s.strikeRate()), "SR",
        String.valueOf(s.thirties), "30s", String.valueOf(s.fifties), "50s", String.valueOf(s.hundreds), "100s",
        String.valueOf(s.fours), "4s", String.valueOf(s.sixes), "6s", String.valueOf(s.dots), "Dot Balls",
        String.valueOf(s.ducks), "Ducks"));
  }

  // ================================================================ bowling

  private void bowling() {
    if (career.bowling.isEmpty()) {
      empty("No bowling innings yet", owner ? "Innings you bowl in show up here once a scored match includes you."
          : "Innings " + first() + " bowls in show up here once a scored match includes them.");
      return;
    }
    bowlingForm();
    bowlingPosition();
    wicketTypes();
    bowlingByMatchInnings();
    bowlingPitch();
    positionWickets();
    wicketsInInnings();
    runsGiven();
    bowlingWheel();
    extras();
    years(new String[] {"Wickets", "Runs", "Economy"},
        new String[] {"Took most wickets in ", "Conceded most runs in ", "Highest economy in "},
        metric -> PerformanceInsights.bowlingByYear(career, metric));
    bowlingOverall();
  }

  private void bowlingForm() {
    List<BowlInnings> all = career.bowlingForm(Integer.MAX_VALUE);
    PerformanceInsights.BowlForm f = PerformanceInsights.bowlForm(career, FORM, profiles);
    String last = "last " + f.innings + " innings";
    String tag = "\n(Last " + f.innings + " innings)";
    boolean[] expanded = {false};
    section("Current form", "Your latest bowling innings, newest first, as overs-maidens-runs-wickets. Tap an innings to "
        + "open the match. The insights and tiles cover your last " + FORM + " innings.", (body, redraw) -> {
      List<BowlInnings> shown = expanded[0] ? all : all.subList(0, Math.min(FORM, all.size()));
      List<CharSequence[]> rows = new ArrayList<>();
      for (BowlInnings b : shown)
        rows.add(new CharSequence[] {b.figures(), fixture(b.row), String.valueOf(b.row.match.rules.overs), date(b.row.match)});
      body.addView(table(new String[] {"O-M-R-W", "Innings", "Ov.", "Date"}, rows, new float[] {1.3f, 2.7f, 0.7f, 1.3f},
          false, null, n -> openMatch(shown.get(n).row.match)));
      if (all.size() > FORM) link(body, expanded[0] ? "Show less" : "View all", () -> { expanded[0] = !expanded[0]; redraw.run(); });
      insights(body);
      insight(body, String.valueOf(f.wickets), "Total wickets in " + last);
      if (f.wickets > 0) insight(body, String.valueOf(f.caught), "Caught out of " + f.wickets + (f.wickets == 1 ? " wicket" : " wickets"));
      insight(body, String.valueOf(f.boundaries()), "Boundaries given in " + last);
      List<String> pairs = new ArrayList<>();
      for (String hand : PlayerProfile.BATTING_HANDS) {
        Integer w = f.wicketsByHand.get(hand);
        if (w != null) { pairs.add(w + "W"); pairs.add(hand + tag); }
      }
      pairs.addAll(Arrays.asList(String.valueOf(f.fours), "4's" + tag, String.valueOf(f.sixes), "6's" + tag));
      tiles(body, 2, pairs.toArray(new String[0]));
    });
  }

  private void bowlingPosition() {
    Picks k = new Picks();
    section("Bowling position", "When you bowl: innings, runs and wickets in each over of the innings. Slot-wise groups "
        + "overs 1-5, 6-10 and so on.", (body, redraw) -> {
      selectors(body, inningsDropdown(k, redraw));
      selectors(body, matchTypeDropdown(k, redraw), oversDropdown(k, redraw));
      radios(body, new String[] {"All", "Slot-wise"}, k.mode, n -> { k.mode = n; redraw.run(); });
      Career sub = k.career().inMatchInnings(k.innings);
      if (sub.bowled.isEmpty()) {
        note(body, "No overs bowled in this selection.");
        return;
      }
      Map<String, int[]> rows = new LinkedHashMap<>();
      if (k.mode == 1) rows.putAll(PerformanceInsights.overSlots(sub.bowled));
      else {
        SortedMap<Integer, int[]> overs = sub.bowlingByOver();
        for (int over = 1; over <= overs.lastKey(); over++) rows.put(String.valueOf(over), overs.getOrDefault(over, new int[4]));
      }
      String[] labels = rows.keySet().toArray(new String[0]);
      double[][] measures = new double[3][labels.length];
      int n = 0;
      for (int[] r : rows.values()) {
        measures[0][n] = r[0];
        measures[1][n] = r[1];
        measures[2][n++] = r[2];
      }
      measureCharts(body, labels, measures, new String[] {"Innings", "Runs", "Wickets"},
          new int[] {Charts.BLUE, Charts.CORAL, Charts.AMBER}, new boolean[] {true, true, true}, k.mode == 1 ? "OVERS" : "OVER");
      String wickets = sub.bestSlot(2), runs = sub.bestSlot(1), dots = sub.bestSlot(3);
      if (wickets.isEmpty() && runs.isEmpty() && dots.isEmpty()) return;
      insights(body);
      if (!wickets.isEmpty()) say(body, "Most wickets taken between ", wickets, " overs");
      if (!runs.isEmpty()) say(body, "Gave most runs between ", runs, " overs");
      if (!dots.isEmpty()) say(body, "Bowled most dot balls between ", dots, " overs");
    });
  }

  private void wicketTypes() {
    Picks k = new Picks();
    LinkedHashMap<String, double[]> hands = PerformanceInsights.handSplit(career.byBatterHand(profiles));
    section("Types of wickets", "How your wickets fell. Run outs are not credited to the bowler, so they are not "
        + "included. The RHB and LHB tiles count only batters whose batting hand is saved.", (body, redraw) -> {
      Map<String, Integer> types = career.wicketTypes(k.batter.isEmpty() ? null : k.batter);
      int total = 0;
      for (int v : types.values()) total += v;
      totalRow(body, "Total wickets:", String.valueOf(total), batterDropdown(k, redraw));
      if (total == 0) note(body, "No wickets in this selection.");
      else pie(body, types, Charts.series(p));
      int setting = career.bowlingInMatchInnings(1).wickets, chasing = career.bowlingInMatchInnings(2).wickets;
      if (setting == chasing && hands.isEmpty()) return;
      insights(body);
      if (setting != chasing) say(body, "Takes more wickets in ", setting > chasing ? "1st" : "2nd", " innings");
      if (hands.isEmpty()) return;
      List<String> pairs = new ArrayList<>();
      String[] names = {"Wickets", "SR", "AVG"};
      for (int m = 0; m < 3; m++)
        for (Map.Entry<String, double[]> h : hands.entrySet()) {
          pairs.add(m == 0 ? Ui.percent(h.getValue()[0]) : Ui.decimal(h.getValue()[m]));
          pairs.add(h.getKey() + " " + names[m]);
        }
      tiles(body, hands.size(), pairs.toArray(new String[0]));
      note(body, "*Only batters whose batting hand is saved in their player profile are counted.");
    });
  }

  private void bowlingByMatchInnings() {
    Picks k = new Picks();
    section("Performance by match innings", "Your bowling when your team bowled first (innings 1) and when it defended "
        + "a total (innings 2).", (body, redraw) -> {
      selectors(body, matchTypeDropdown(k, redraw), oversDropdown(k, redraw), ballTypeDropdown(k, redraw));
      Career c = k.career();
      List<CharSequence[]> rows = new ArrayList<>();
      for (int number = 1; number <= 2; number++) {
        BowlStats s = c.bowlingInMatchInnings(number);
        rows.add(new CharSequence[] {String.valueOf(number), String.valueOf(s.innings), s.innings == 0 ? "0.0" : s.oversText(),
            String.valueOf(s.maidens), String.valueOf(s.wickets), String.valueOf(s.runs)});
      }
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.topMargin = dp(14);
      body.addView(table(new String[] {"Inns No.", "Inns", "O", "M", "W", "R"}, rows, new float[] {1.3f, 1, 1.1f, 0.8f, 0.8f, 1},
          true, null, null), lp);
    });
  }

  private void bowlingPitch() {
    if (career.bowlingByPitch().isEmpty()) return;
    Picks k = new Picks();
    section("Pitch type analysis", "Your bowling on each type of pitch. Only matches where the pitch type was recorded "
        + "are counted.", (body, redraw) -> {
      selectors(body, ballTypeDropdown(k, redraw), oversDropdown(k, redraw));
      Map<String, BowlStats> by = k.career().bowlingByPitch();
      if (by.isEmpty()) note(body, "No recorded pitch types in this selection.");
      else {
        List<CharSequence[]> rows = new ArrayList<>();
        for (Map.Entry<String, BowlStats> e : by.entrySet()) {
          BowlStats s = e.getValue();
          rows.add(new CharSequence[] {PlayerProfile.label(e.getKey()), String.valueOf(s.innings), s.oversText(),
              String.valueOf(s.maidens), String.valueOf(s.wickets), String.valueOf(s.runs)});
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        body.addView(table(new String[] {"Pitch type", "Inns", "Overs", "Mdns", "Wkts", "Runs"}, rows,
            new float[] {1.6f, 0.8f, 1, 0.9f, 0.9f, 0.9f}, true, null, null), lp);
      }
      note(body, "*Count may not tally: matches without a recorded pitch type are left out.");
    });
  }

  private void positionWickets() {
    if (career.wicketsByPosition().isEmpty()) return;
    Picks k = new Picks();
    section("Batting position wise wickets", "Which batters you dismiss, by their place in the batting order, as a "
        + "share of your wickets.", (body, redraw) -> {
      Career sub = career.inMatchInnings(k.innings);
      SortedMap<Integer, Integer> positions = sub.wicketsByPosition();
      int total = 0;
      for (int v : positions.values()) total += v;
      totalRow(body, "Total number of outs:", String.valueOf(total), inningsDropdown(k, redraw));
      if (total == 0) {
        note(body, "No wickets in this selection.");
        return;
      }
      int lastPosition = positions.lastKey();
      String[] labels = new String[lastPosition];
      double[] share = new double[lastPosition];
      int[] counts = new int[lastPosition];
      for (int pos = 1; pos <= lastPosition; pos++) {
        labels[pos - 1] = String.valueOf(pos);
        counts[pos - 1] = positions.getOrDefault(pos, 0);
        share[pos - 1] = counts[pos - 1] * 100.0 / total;
      }
      chart(body, new Charts.BarChartView(this, p).bars(labels, share, Charts.series(p)).axes("WICKETS (%)", "BATTING POSITION")
          .tooltip((series, x) -> Math.round(share[x]) + "% · " + counts[x] + (counts[x] == 1 ? " wicket" : " wickets")));
      List<String> text = new ArrayList<>();
      for (int pos = 0; pos < lastPosition; pos++) if (counts[pos] > 0) text.add("No. " + labels[pos] + ": " + counts[pos]);
      line("Wickets by batting position: " + TextUtils.join(", ", text));
      int[] order = sub.wicketsByOrder();
      double known = order[0] + order[1] + order[2];
      if (known == 0) return;
      insights(body);
      insight(body, Ui.percent(order[0] * 100 / known), "Top order wickets (1-2-3)");
      insight(body, Ui.percent(order[1] * 100 / known), "Middle order wickets (4-5-6-7)");
      insight(body, Ui.percent(order[2] * 100 / known), "Lower order wickets (8-9-10-11)");
    });
  }

  private void wicketsInInnings() {
    Picks k = new Picks();
    section("Wickets in innings", "How many of your bowling innings ended with 0, 1, 2 or more wickets.", (body, redraw) -> {
      selectors(body, inningsDropdown(k, redraw));
      SortedMap<Integer, Integer> counts = career.inMatchInnings(k.innings).wicketsPerInnings();
      if (counts.isEmpty()) note(body, "No bowling innings in this selection.");
      else {
        int most = counts.lastKey();
        String[] labels = new String[most + 1];
        double[] innings = new double[most + 1];
        for (int w = 0; w <= most; w++) {
          labels[w] = String.valueOf(w);
          innings[w] = counts.getOrDefault(w, 0);
        }
        chart(body, new Charts.BarChartView(this, p).bars(labels, innings, Charts.series(p)).axes("INNINGS", "WICKETS").integers()
            .tooltip((series, x) -> num(innings[x]) + " innings with " + labels[x] + (x == 1 ? " wicket" : " wickets")));
        List<String> text = new ArrayList<>();
        for (int w = 0; w <= most; w++) if (innings[w] > 0) text.add(num(innings[w]) + " with " + w);
        line("Innings by wickets taken: " + TextUtils.join(", ", text));
      }
      insights(body);
      double lose = career.losePercentWhenWicketless();
      if (Double.isNaN(lose)) insight(body, "-", "Matches lost when " + first() + " goes wicketless");
      else say(body, "Team loses ", Ui.percent(lose), " of matches when ", first(), " goes wicketless");
    });
  }

  private void runsGiven() {
    Picks k = new Picks();
    section("Types of runs", "The share of your deliveries that went for dots, 1s, 2s, 3s, 4s and 6s off the bat. Wides "
        + "are left out. Tap a bar for its share.", (body, redraw) -> {
      List<Ball> balls = bowled(k);
      int runs = 0;
      for (Ball b : balls) runs += b.bowlerRuns;
      totalRow(body, "Total runs given:", String.valueOf(runs), batterDropdown(k, redraw));
      if (balls.isEmpty()) note(body, "No deliveries in this selection.");
      else runBars(body, Career.runTypes(balls));
      insights(body);
      insight(body, Ui.percent(career.boundaryRunPercent()), "Runs in boundaries out of total");
    });
  }

  private void bowlingWheel() {
    Picks k = new Picks();
    String[] only = {""};
    section("Wagon wheel", "Where batters score off you, from the fielding zone the scorer tapped. Percentages are each "
        + "region's share of the runs. Tap a colour to see only those deliveries.", (body, redraw) -> {
      selectors(body, batterDropdown(k, redraw), ballTypeDropdown(k, redraw), matchTypeDropdown(k, redraw), oversDropdown(k, redraw));
      List<Ball> balls = bowled(k);
      List<WagonWheelView.Shot> shots = new ArrayList<>();
      for (Ball b : balls) {
        if (b.bowlerWicket()) shots.add(new WagonWheelView.Shot(b.zone, b.batRuns, true));
        else if (b.faced()) shots.add(new WagonWheelView.Shot(b.zone, b.batRuns, false));
      }
      wheel(body, shots, false, only, redraw);
      String best = Career.bestZone(Career.zoneRuns(balls));
      insights(body);
      if (best.isEmpty()) note(body, "No shot directions were recorded for this selection.");
      else say(body, "Most runs conceded in ", best, "");
    });
  }

  private void extras() {
    Picks k = new Picks();
    section("Extras", "Wides and no-balls you have bowled, and how often they come.", (body, redraw) -> {
      selectors(body, batterDropdown(k, redraw));
      List<Ball> balls = bowled(k);
      int[] e = PerformanceInsights.extras(balls);
      double overs = PerformanceInsights.overs(balls);
      if (e[0] + e[1] == 0) note(body, "No wides or no-balls in this selection.");
      else {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Wide", e[0]);
        counts.put("No ball", e[1]);
        pie(body, counts, new int[] {Charts.BLUE, Charts.CORAL});
      }
      if (e[0] == 0 && e[1] == 0) return;
      insights(body);
      if (e[0] > 0) say(body, "Bowls ", "1 wide", " in ", one(overs / e[0]), " overs");
      if (e[1] > 0) say(body, "Bowls ", "1 no-ball", " in ", one(overs / e[1]), " overs");
    });
  }

  private void bowlingOverall() {
    BowlStats s = career.bowlStats();
    section("Overall stats", "Your bowling totals across the matches shown. SR is balls per wicket.", (body, redraw) -> tiles(body, 3,
        String.valueOf(s.matches), "Matches", String.valueOf(s.innings), "Innings", s.oversText(), "Overs",
        String.valueOf(s.maidens), "Maidens", String.valueOf(s.wickets), "Wickets", String.valueOf(s.runs), "Runs",
        s.best(), "Best Bowling", Ui.decimal(s.economy()), "Economy", Ui.decimal(s.strikeRate()), "SR",
        Ui.decimal(s.average()), "Avg", String.valueOf(s.wides), "Wides", String.valueOf(s.noBalls), "No Balls",
        String.valueOf(s.dots), "Dot Balls", String.valueOf(s.fours), "4s", String.valueOf(s.sixes), "6s"));
  }

  // ================================================================ compare

  private void compare() {
    LinearLayout box = block();
    box.setPadding(dp(16), dp(18), dp(16), dp(18));
    LinearLayout cards = Ui.row(this);
    cards.addView(card(playerPicture(), first(), null, null), new LinearLayout.LayoutParams(0, -2, 1));
    cards.addView(versus(), new LinearLayout.LayoutParams(dp(60), -2));
    View picture = rival == null ? new Silhouette(this, p) : initialsPicture(rival[1]);
    cards.addView(card(picture, rival == null ? "Select player" : rival[1], rival == null ? null : rival[0], this::pickRival),
        new LinearLayout.LayoutParams(0, -2, 1));
    box.addView(cards);
    if (rival == null) {
      TextView hint = Ui.text(this, owner ? "Pick a player from your saved teams to compare your numbers side by side."
          : "Pick a player from your saved teams to compare with " + first() + " side by side.", 14, p.muted, false);
      hint.setGravity(Gravity.CENTER);
      hint.setPadding(dp(8), dp(16), dp(8), 0);
      box.addView(hint);
      return;
    }
    LinearLayout toggle = block();
    toggle.setPadding(dp(16), dp(12), dp(16), dp(12));
    segmented(toggle, new String[] {"Batting", "Bowling"}, compareBowling ? 1 : 0, n -> { compareBowling = n == 1; rerender(); });
    Career them = careerFor(new Career.Who(rival[1]).add(rival[0], rival[1]), filter);
    if (compareBowling) {
      bowlingComparison(them);
      bowlingStatsComparison(them);
      sharesComparison("Types of wickets", "How each of you takes wickets, as a share of each bowler's own wickets. "
          + "Run outs are not credited to the bowler.", career.wicketTypes(null), them.wicketTypes(null), "WICKETS (%)",
          "Neither player has taken a wicket in these matches.");
    } else {
      battingStatsComparison(them);
      sharesComparison("Out type", "How each of you gets out, as a share of each player's own dismissals.",
          career.outTypes(), them.outTypes(), "OUTS (%)", "Neither player has been dismissed in these matches.");
    }
  }

  private View card(View picture, String name, String team, Runnable tap) {
    LinearLayout card = Ui.column(this);
    card.setBackground(Ui.outline(p.surface2, p.line, Math.max(1, dp(1)), dp(12)));
    card.setClipToOutline(true);
    SquareFrame frame = new SquareFrame(this);
    frame.addView(picture, new FrameLayout.LayoutParams(-1, -1));
    card.addView(frame, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout label = Ui.column(this);
    label.setGravity(Gravity.CENTER);
    label.setBackgroundColor(p.bg);
    label.setPadding(dp(8), dp(12), dp(8), dp(12));
    TextView n = Ui.text(this, name, 15, tap != null && team == null ? p.accent : p.ink, true);
    n.setGravity(Gravity.CENTER);
    n.setSingleLine(true);
    n.setEllipsize(TextUtils.TruncateAt.END);
    label.addView(n, new LinearLayout.LayoutParams(-1, -2));
    if (team != null) {
      TextView t = Ui.text(this, team, 12, p.muted, false);
      t.setGravity(Gravity.CENTER);
      t.setSingleLine(true);
      t.setEllipsize(TextUtils.TruncateAt.END);
      t.setPadding(0, dp(4), 0, 0);
      label.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }
    card.addView(label, new LinearLayout.LayoutParams(-1, -2));
    if (tap != null) {
      card.setOnClickListener(v -> tap.run());
      card.setContentDescription(team == null ? "Select a player to compare" : "Change player, now " + name);
    }
    return card;
  }

  private View playerPicture() {
    File photo = me.photo();
    Bitmap bitmap = owner && photo.exists() ? Ui.decode(photo, dp(160)) : null;
    if (bitmap != null) {
      ImageView image = new ImageView(this);
      image.setImageBitmap(bitmap);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      return image;
    }
    return initialsPicture(who.display);
  }

  private View initialsPicture(String name) {
    FrameLayout f = new FrameLayout(this);
    f.addView(Ui.avatar(this, p, null, name, 84), new FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER));
    return f;
  }

  private View versus() {
    FrameLayout f = new FrameLayout(this);
    View rule = new View(this);
    rule.setBackgroundColor(p.line);
    f.addView(rule, new FrameLayout.LayoutParams(Math.max(1, dp(1.5f)), dp(140), Gravity.CENTER));
    TextView vs = Ui.text(this, "VS", 14, p.ink, true);
    vs.setGravity(Gravity.CENTER);
    vs.setBackground(Ui.outline(p.surface, p.line, Math.max(1, dp(1.5f)), dp(22)));
    f.addView(vs, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
    return f;
  }

  private void segmented(LinearLayout into, String[] options, int selected, IntConsumer pick) {
    LinearLayout bar = Ui.row(this);
    bar.setBackground(Ui.round(p.surface2, dp(12)));
    bar.setPadding(dp(4), dp(4), dp(4), dp(4));
    for (int n = 0; n < options.length; n++) {
      boolean on = n == selected;
      TextView t = Ui.text(this, options[n], 16, on ? p.onAccent : p.ink, true);
      t.setGravity(Gravity.CENTER);
      t.setPadding(0, dp(11), 0, dp(11));
      if (on) t.setBackground(Ui.round(p.accent, dp(10)));
      int index = n;
      t.setOnClickListener(v -> { if (index != selected) pick.accept(index); });
      bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    }
    into.addView(bar, new LinearLayout.LayoutParams(-1, -2));
  }

  private void pickRival() {
    BottomSheetDialog sheet = new BottomSheetDialog(this);
    LinearLayout box = Ui.column(this);
    box.setBackground(topRounded(p.surface));
    box.setPadding(dp(16), dp(14), dp(16), dp(16));
    LinearLayout head = Ui.row(this);
    head.addView(Ui.text(this, "Select player", 21, p.ink, true), Ui.weight(1));
    head.addView(Ui.iconButton(this, p, "close", 40, p.surface2, p.ink, sheet::dismiss));
    box.addView(head);
    EditText search = new EditText(this);
    search.setHint("Search by player or team");
    search.setSingleLine(true);
    search.setTextSize(15);
    search.setTextColor(p.ink);
    search.setHintTextColor(p.muted);
    search.setBackground(Ui.round(p.surface2, dp(14)));
    search.setPadding(dp(14), dp(12), dp(14), dp(12));
    LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
    sl.setMargins(0, dp(12), 0, dp(6));
    box.addView(search, sl);
    LinearLayout list = Ui.column(this);
    ScrollView frame = new ScrollView(this);
    frame.addView(list);
    box.addView(frame, new LinearLayout.LayoutParams(-1, dp(420)));
    List<String[]> places = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String[] place : ProfileStore.squadPlaces(repo)) {
      String key = Career.key(place[0], place[1]);
      if (!who.keys.contains(key) && seen.add(key)) places.add(place);
    }
    places.sort((a, b) -> {
      int byName = a[1].compareToIgnoreCase(b[1]);
      return byName != 0 ? byName : a[0].compareToIgnoreCase(b[0]);
    });
    Runnable render = () -> {
      list.removeAllViews();
      String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
      int shown = 0;
      for (String[] place : places) {
        if (!q.isEmpty() && !place[1].toLowerCase(Locale.ROOT).contains(q) && !place[0].toLowerCase(Locale.ROOT).contains(q)) continue;
        if (shown++ == 200) break;
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.addView(Ui.avatar(this, p, null, place[1], 40));
        LinearLayout text = Ui.column(this);
        text.setPadding(dp(12), 0, 0, 0);
        text.addView(Ui.text(this, place[1], 16, p.ink, true));
        TextView team = Ui.text(this, place[0], 13, p.muted, false);
        team.setPadding(0, dp(4), 0, 0);
        text.addView(team);
        row.addView(text, Ui.weight(1));
        row.setOnClickListener(v -> {
          rival = place;
          sheet.dismiss();
          rerender();
        });
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
      }
      if (shown == 0) {
        TextView none = Ui.text(this, places.isEmpty() ? "No other players in your saved teams yet." : "No players match your search.",
            14, p.muted, false);
        none.setPadding(dp(4), dp(16), dp(4), 0);
        list.addView(none);
      }
    };
    search.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
      @Override public void afterTextChanged(Editable s) { render.run(); }
    });
    render.run();
    sheet.setContentView(box);
    clearSheetBackground(sheet, true);
    sheet.show();
  }

  /** "Parameters | me | them" rows; label cells on the surface, values on the darker table colour. */
  private void compareTable(LinearLayout body, List<String[]> rows) {
    String[] head = {"Parameters", first(), firstOf(rival[1])};
    float[] weights = {1.3f, 1, 1};
    LinearLayout h = Ui.row(this);
    h.setPadding(0, dp(12), 0, dp(12));
    for (int n = 0; n < head.length; n++) {
      TextView cell = Ui.text(this, head[n], 13, p.muted, false);
      cell.setGravity(n == 0 ? Gravity.START : Gravity.CENTER);
      cell.setSingleLine(true);
      cell.setEllipsize(TextUtils.TruncateAt.END);
      cell.setPadding(n == 0 ? dp(16) : dp(4), 0, dp(4), 0);
      h.addView(cell, new LinearLayout.LayoutParams(0, -2, weights[n]));
    }
    body.addView(h);
    line(TextUtils.join(" | ", head));
    for (String[] r : rows) {
      LinearLayout row = Ui.row(this);
      row.setBackgroundColor(p.line);
      for (int n = 0; n < 3; n++) {
        TextView cell = Ui.text(this, r[n], 14, p.ink, false);
        cell.setGravity(n == 0 ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        cell.setBackgroundColor(n == 0 ? p.surface : p.bg);
        cell.setPadding(n == 0 ? dp(16) : dp(4), dp(13), dp(4), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weights[n]);
        if (n > 0) lp.leftMargin = Math.max(1, dp(1));
        lp.bottomMargin = Math.max(1, dp(0.7f));
        row.addView(cell, lp);
      }
      body.addView(row, new LinearLayout.LayoutParams(-1, -2));
      line(r[0] + ": " + r[1] + " | " + r[2]);
    }
  }

  private static String[] stat(String label, Object mine, Object theirs) {
    return new String[] {label, String.valueOf(mine), String.valueOf(theirs)};
  }

  private void battingStatsComparison(Career them) {
    BatStats a = career.batStats(), b = them.batStats();
    List<String[]> rows = Arrays.asList(
        stat("Matches", a.matches, b.matches), stat("Innings", a.innings, b.innings), stat("Not Outs", a.notOuts, b.notOuts),
        stat("Runs", a.runs, b.runs), stat("Highest Runs", a.highestText(), b.highestText()),
        stat("Average", Ui.decimal(a.average()), Ui.decimal(b.average())),
        stat("Strike Rate", Ui.decimal(a.strikeRate()), Ui.decimal(b.strikeRate())),
        stat("30s", a.thirties, b.thirties), stat("50s", a.fifties, b.fifties), stat("100s", a.hundreds, b.hundreds),
        stat("Ducks", a.ducks, b.ducks));
    section("Batting stats", "Your batting next to " + rival[1] + "'s, from the matches saved on this phone.",
        (body, redraw) -> compareTable(body, rows));
  }

  private void bowlingComparison(Career them) {
    Map<String, double[]> mineByHand = PerformanceInsights.handSplit(career.byBatterHand(profiles));
    Map<String, double[]> theirsByHand = PerformanceInsights.handSplit(them.byBatterHand(profiles));
    List<String[]> rows = new ArrayList<>();
    for (String hand : new String[] {"LHB", "RHB"})
      addComparable(rows, "Avg vs " + hand, mineByHand.containsKey(hand) ? mineByHand.get(hand)[2] : Double.NaN,
          theirsByHand.containsKey(hand) ? theirsByHand.get(hand)[2] : Double.NaN);
    addComparable(rows, "Top six dismissals %", PerformanceInsights.topSixPercent(career), PerformanceInsights.topSixPercent(them));
    addComparable(rows, "Boundary %", career.boundaryRunPercent(), them.boundaryRunPercent());
    if (rows.isEmpty()) return;
    section("Bowling comparison", "Average against left- and right-handed batters (only batters whose batting hand is "
        + "saved), the share of wickets that were batters 1 to 6, and the share of runs conceded in boundaries.",
        (body, redraw) -> compareTable(body, rows));
  }

  private static void addComparable(List<String[]> rows, String label, double mine, double theirs) {
    if (!Double.isNaN(mine) || !Double.isNaN(theirs)) rows.add(new String[] {label, Ui.decimal(mine), Ui.decimal(theirs)});
  }

  private void bowlingStatsComparison(Career them) {
    BowlStats a = career.bowlStats(), b = them.bowlStats();
    List<String[]> rows = Arrays.asList(
        stat("Matches", a.matches, b.matches), stat("Innings", a.innings, b.innings),
        stat("Overs", a.oversText(), b.oversText()), stat("Maidens", a.maidens, b.maidens),
        stat("Wickets", a.wickets, b.wickets), stat("Runs", a.runs, b.runs), stat("Best Bowling", a.best(), b.best()),
        stat("Economy", Ui.decimal(a.economy()), Ui.decimal(b.economy())),
        stat("SR", Ui.decimal(a.strikeRate()), Ui.decimal(b.strikeRate())),
        stat("Avg", Ui.decimal(a.average()), Ui.decimal(b.average())),
        stat("Wides", a.wides, b.wides), stat("No Balls", a.noBalls, b.noBalls));
    section("Bowling stats", "Your bowling next to " + rival[1] + "'s, from the matches saved on this phone.",
        (body, redraw) -> compareTable(body, rows));
  }

  /** Grouped bars of each player's share of their own dismissals or wickets by type. */
  private void sharesComparison(String title, String info, Map<String, Integer> mineCounts, Map<String, Integer> theirCounts,
      String yTitle, String none) {
    section(title, info, (body, redraw) -> {
      LinkedHashSet<String> types = new LinkedHashSet<>(mineCounts.keySet());
      types.addAll(theirCounts.keySet());
      if (types.isEmpty()) {
        note(body, none);
        return;
      }
      String[] names = {first(), firstOf(rival[1])};
      int[] colors = {p.accent, Charts.AMBER};
      LinearLayout legend = Ui.row(this);
      legend.setPadding(dp(16), dp(14), dp(16), 0);
      for (int s = 0; s < 2; s++) {
        View swatch = new View(this);
        swatch.setBackground(Ui.round(colors[s], dp(2)));
        legend.addView(swatch, new LinearLayout.LayoutParams(dp(12), dp(12)));
        TextView t = Ui.text(this, names[s], 14, p.ink, false);
        t.setPadding(dp(8), 0, dp(24), 0);
        legend.addView(t);
      }
      body.addView(legend);
      String[] labels = types.toArray(new String[0]);
      List<Map<String, Integer>> counts = Arrays.asList(mineCounts, theirCounts);
      double[][] values = new double[2][labels.length];
      for (int s = 0; s < 2; s++) {
        Map<String, Double> shares = PerformanceInsights.shares(counts.get(s));
        for (int n = 0; n < labels.length; n++)
          values[s][n] = counts.get(s).isEmpty() ? Double.NaN : shares.getOrDefault(labels[n], 0.0);
      }
      chart(body, new Charts.BarChartView(this, p).grouped(labels, values, colors).axes(yTitle, "").height(220)
          .tooltip((series, x) -> names[series] + ": " + Math.round(values[series][x]) + "% ("
              + counts.get(series).getOrDefault(labels[x], 0) + ")"));
      for (int n = 0; n < labels.length; n++)
        line(labels[n] + ": " + names[0] + " " + counts.get(0).getOrDefault(labels[n], 0) + " ("
            + Ui.percent(values[0][n]) + "), " + names[1] + " " + counts.get(1).getOrDefault(labels[n], 0) + " ("
            + Ui.percent(values[1][n]) + ")");
    });
  }

  // ================================================================ small views

  /** Soft circles on the header gradient, echoing the reference's decorative shapes. */
  private static final class Glow extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glow(Context c) { super(c); }

    /** Takes no height of its own; the frame stretches it to the photo row afterwards. */
    @Override protected void onMeasure(int w, int h) {
      setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getMode(h) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(h) : 0);
    }

    @Override protected void onDraw(Canvas canvas) {
      float w = getWidth(), h = getHeight();
      paint.setColor(0x14ffffff);
      canvas.drawCircle(w * 0.86f, h * 0.92f, h * 0.76f, paint);
      paint.setColor(0x0fffffff);
      canvas.drawCircle(w * 0.04f, h * 1.02f, h * 0.48f, paint);
    }
  }

  /** Neutral head-and-shoulders placeholder for the player not picked yet. */
  private static final class Silhouette extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Silhouette(Context c, Ui.Palette p) {
      super(c);
      paint.setColor(p.muted);
      paint.setAlpha(140);
      setBackgroundColor(p.surface2);
    }

    @Override protected void onDraw(Canvas canvas) {
      float w = getWidth(), h = getHeight();
      canvas.drawCircle(w / 2, h * 0.4f, w * 0.17f, paint);
      path.reset();
      path.moveTo(w * 0.17f, h);
      path.cubicTo(w * 0.17f, h * 0.7f, w * 0.31f, h * 0.62f, w / 2, h * 0.62f);
      path.cubicTo(w * 0.69f, h * 0.62f, w * 0.83f, h * 0.7f, w * 0.83f, h);
      path.close();
      canvas.drawPath(path, paint);
    }
  }

  private static final class SquareFrame extends FrameLayout {
    SquareFrame(Context c) { super(c); }

    @Override protected void onMeasure(int w, int h) { super.onMeasure(w, w); }
  }
}
