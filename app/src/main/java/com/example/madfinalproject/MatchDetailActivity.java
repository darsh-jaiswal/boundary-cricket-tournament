package com.example.madfinalproject;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.Career;
import com.example.madfinalproject.domain.Cricket.Match;
import com.example.madfinalproject.domain.Cricket.Tournament;
import com.example.madfinalproject.domain.MatchReport;
import com.example.madfinalproject.domain.PlayerProfile;
import com.example.madfinalproject.domain.PlayerStats;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Match detail: Info, Summary, Scorecard, Overs and Squads for one saved match. */
public final class MatchDetailActivity extends AppCompatActivity {
  public static final String EXTRA_MATCH_ID = "match", EXTRA_TAB = "tab";
  private static final String[] TABS = {"Info", "Summary", "Scorecard", "Overs", "Squads"};
  private static final int[] BAT_COLUMNS = {32, 32, 28, 28, 46}, BOWL_COLUMNS = {32, 28, 32, 28, 46};

  private Repository repo;
  private Ui.Palette p;
  private Match match;
  private String tournament = "", title = "Individual match", tab;
  private HorizontalScrollView tabScroll;
  private LinearLayout tabRow, content;
  private ScrollView scroll;
  private final Set<Integer> expanded = new HashSet<>();
  private int oversInnings = -1;
  private boolean alphabetical = true;
  private Set<String> ownerKeys = Collections.emptySet();
  private File ownerPhoto;
  private Map<String, PlayerProfile> profiles = Collections.emptyMap();

  @Override
  protected void onCreate(Bundle saved) {
    Ui.applyAppearance(this);
    super.onCreate(saved);
    p = Ui.palette(this);
    repo = new Repository(this);
    LinearLayout root = Ui.column(this);
    root.setBackgroundColor(p.bg);
    setContentView(root);
    UiInsets.apply(root);
    String id = getIntent().getStringExtra(EXTRA_MATCH_ID);
    for (Match m : repo.matches()) if (m.id.equals(id)) match = m;
    if (match == null) {
      root.addView(topBar(false));
      TextView missing = Ui.text(this, "This match is no longer saved on this device.", 15, p.muted, false);
      missing.setPadding(dp(20), dp(24), dp(20), 0);
      root.addView(missing);
      return;
    }
    for (Tournament t : repo.tournaments()) if (t.id.equals(match.tournament)) tournament = t.name;
    if (!tournament.isEmpty()) title = tournament;
    ProfileStore me = new ProfileStore(this);
    ownerKeys = me.who(repo).keys;
    ownerPhoto = me.hasPhoto() ? me.photo() : null;
    profiles = ProfileStore.all(repo, me);

    tab = pick(saved != null ? saved.getString(EXTRA_TAB) : getIntent().getStringExtra(EXTRA_TAB));
    if (saved != null) {
      oversInnings = saved.getInt("overs", -1);
      alphabetical = saved.getBoolean("alphabetical", true);
      int[] open = saved.getIntArray("expanded");
      if (open != null) for (int i : open) expanded.add(i);
    } else expanded.add(0);

    LinearLayout header = Ui.column(this);
    header.setBackgroundColor(p.surface);
    header.addView(topBar(true));
    tabScroll = new HorizontalScrollView(this);
    tabScroll.setHorizontalScrollBarEnabled(false);
    tabRow = Ui.row(this);
    tabRow.setPadding(dp(4), 0, dp(4), 0);
    tabScroll.addView(tabRow);
    header.addView(tabScroll);
    header.addView(Ui.divider(this, p));
    root.addView(header);

    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = Ui.column(this);
    content.setPadding(dp(16), dp(14), dp(16), dp(28));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if ("Live".equals(match.result()) && canEdit()) root.addView(scoreBar());
    drawTabs();
    render();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    if (match == null) return;
    out.putString(EXTRA_TAB, tab);
    out.putInt("overs", oversInnings);
    out.putBoolean("alphabetical", alphabetical);
    int[] open = new int[expanded.size()];
    int k = 0;
    for (int i : expanded) open[k++] = i;
    out.putIntArray("expanded", open);
  }

  @Override
  protected void onDestroy() {
    if (repo != null) repo.close();
    super.onDestroy();
  }

  private static String pick(String requested) {
    if (requested != null) for (String t : TABS) if (t.equalsIgnoreCase(requested)) return t;
    return "Summary";
  }

  private boolean canEdit() {
    String role = getSharedPreferences("MainActivity", 0).getString("role", "Organizer");
    return !role.equals("Spectator") && !role.equals("Player");
  }

  // ---------------------------------------------------------------- chrome

  private View topBar(boolean share) {
    LinearLayout bar = Ui.row(this);
    bar.setPadding(dp(6), dp(8), dp(12), dp(6));
    bar.setBackgroundColor(p.surface);
    FrameLayout back = Ui.iconButton(this, p, "back", 44, p.surface, p.ink, this::finish);
    back.setContentDescription("Back");
    pressable(back);
    bar.addView(back);
    TextView name = Ui.text(this, title, 21, p.ink, true);
    name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    name.setPadding(dp(8), 0, dp(8), 0);
    bar.addView(name, Ui.weight(1));
    if (share) {
      FrameLayout send = Ui.iconButton(this, p, "share", 44, p.surface2, p.ink, this::share);
      send.setContentDescription("Share match");
      pressable(send);
      bar.addView(send);
    }
    return bar;
  }

  private void drawTabs() {
    tabRow.removeAllViews();
    View selected = null;
    for (String name : TABS) {
      boolean on = name.equals(tab);
      LinearLayout cell = Ui.column(this);
      cell.setGravity(Gravity.CENTER_HORIZONTAL);
      TextView label = Ui.text(this, name, 15, on ? p.accent : p.muted, on);
      label.setPadding(dp(4), dp(14), dp(4), dp(11));
      cell.addView(label);
      View underline = new View(this);
      GradientDrawable bar = Ui.round(on ? p.accent : 0, 0);
      bar.setCornerRadii(new float[] {dp(3), dp(3), dp(3), dp(3), 0, 0, 0, 0});
      underline.setBackground(bar);
      cell.addView(underline, new LinearLayout.LayoutParams(-1, dp(3)));
      cell.setContentDescription(name + (on ? ", selected" : ""));
      cell.setOnClickListener(v -> select(name));
      pressable(cell);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.setMargins(dp(10), 0, dp(10), 0);
      tabRow.addView(cell, lp);
      if (on) selected = cell;
    }
    View target = selected;
    if (target != null)
      tabScroll.post(() -> tabScroll.smoothScrollTo(
          Math.max(0, target.getLeft() - (tabScroll.getWidth() - target.getWidth()) / 2), 0));
  }

  private void select(String name) {
    if (name.equals(tab)) return;
    tab = name;
    drawTabs();
    render();
    scroll.scrollTo(0, 0);
  }

  private void render() {
    content.removeAllViews();
    switch (tab) {
      case "Info": info(); break;
      case "Scorecard": scorecard(); break;
      case "Overs": overs(); break;
      case "Squads": squads(); break;
      default: summary(); break;
    }
  }

  /** Re-renders the current tab without jumping back to the top. */
  private void rerender() {
    int y = scroll.getScrollY();
    render();
    scroll.post(() -> scroll.scrollTo(0, y));
  }

  private View scoreBar() {
    LinearLayout bar = Ui.column(this);
    bar.setBackgroundColor(p.surface);
    bar.addView(Ui.divider(this, p));
    TextView score = Ui.pill(this, p, "Score this match", true, this::scoreMatch);
    pressable(score);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(16), dp(10), dp(16), dp(10));
    bar.addView(score, lp);
    return bar;
  }

  private void scoreMatch() {
    startActivity(new Intent(this, MainActivity.class)
        .putExtra(MainActivity.EXTRA_SCORE_MATCH, match.id)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    finish();
  }

  private void share() { send(MatchReport.shareText(match, title), "Share match"); }

  private void send(String text, String chooser) {
    Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
    startActivity(Intent.createChooser(intent, chooser));
  }

  // ---------------------------------------------------------------- info

  private void info() {
    LinearLayout details = Ui.sectionTitle(this, p, "Match details", null);
    details.setPadding(0, dp(4), 0, dp(10));
    content.addView(details);
    List<View> rows = new ArrayList<>();
    rows.add(detail("Tournament", value(tournament.isEmpty() ? "Individual match" : tournament)));
    rows.add(detail("Match type", value(match.matchType().isEmpty() ? "-" : PlayerProfile.label(match.matchType()))));
    rows.add(detail("Overs", value(String.valueOf(match.rules.overs))));
    rows.add(detail("Date & time", dateValue()));
    rows.add(detail("Venue", venueValue()));
    if (match.tossWinner != null && !match.tossWinner.isEmpty())
      rows.add(detail("Toss", value(match.tossDecision == null || match.tossDecision.isEmpty()
          ? match.tossWinner + " won the toss"
          : match.tossWinner + " opt to " + ("BOWL".equals(match.tossDecision) ? "bowl" : "bat"))));
    rows.add(detail("Ball type", ballValue()));
    if (!match.pitchType().isEmpty()) rows.add(detail("Pitch type", value(PlayerProfile.label(match.pitchType()))));
    rows.add(detail("Match ID", value(match.id.substring(0, Math.min(8, match.id.length())))));
    LinearLayout card = Ui.card(this, p);
    card.setPadding(dp(16), dp(2), dp(16), dp(2));
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) card.addView(Ui.divider(this, p));
      card.addView(rows.get(i));
    }
    content.addView(card);

    SimpleDateFormat stamp = new SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault());
    boolean titled = false;
    for (int i = 0; i < match.innings.size(); i++) {
      List<String> notes = MatchReport.notes(match, i, t -> stamp.format(new Date(t)));
      if (notes.isEmpty()) continue;
      if (!titled) {
        LinearLayout heading = Ui.sectionTitle(this, p, "Match notes", null);
        heading.setPadding(0, dp(26), 0, dp(10));
        content.addView(heading);
        titled = true;
      }
      LinearLayout box = Ui.card(this, p);
      box.setPadding(dp(16), dp(14), dp(16), dp(6));
      box.addView(Ui.text(this, inningsName(i), 16, p.ink, true));
      for (int k = 0; k < notes.size(); k++) {
        if (k > 0) box.addView(Ui.divider(this, p));
        TextView note = Ui.text(this, notes.get(k), 14, p.ink, false);
        note.setPadding(0, dp(10), 0, dp(10));
        box.addView(note);
      }
      content.addView(box, spaced(12));
    }
  }

  private View detail(String label, View value) {
    LinearLayout row = Ui.row(this);
    row.setPadding(0, dp(13), 0, dp(13));
    TextView name = Ui.text(this, label, 14, p.muted, false);
    row.addView(name, new LinearLayout.LayoutParams(dp(112), -2));
    row.addView(value, Ui.weight(1));
    return row;
  }

  private TextView value(String text) { return Ui.text(this, text, 15, p.ink, false); }

  private View dateValue() {
    Date played = new Date(match.played());
    LinearLayout row = Ui.row(this);
    row.addView(value(new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).format(played)));
    View bar = new View(this);
    bar.setBackgroundColor(p.line);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(16));
    lp.setMargins(dp(12), 0, dp(12), 0);
    row.addView(bar, lp);
    row.addView(value(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(played)));
    return row;
  }

  private View venueValue() {
    if (match.venue == null || match.venue.trim().isEmpty()) return value("-");
    LinearLayout row = Ui.row(this);
    row.addView(Ui.text(this, match.venue, 15, p.accent, false), Ui.weight(1));
    row.addView(new IconView(this, "chevron-right", p.accent), new LinearLayout.LayoutParams(dp(18), dp(18)));
    row.setContentDescription("Open " + match.venue + " in maps");
    row.setOnClickListener(v -> {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(match.venue))));
      } catch (ActivityNotFoundException ignored) {
        // No maps app on this device: the venue text is still shown.
      }
    });
    pressable(row);
    return row;
  }

  private View ballValue() {
    String ball = match.ballType();
    if (ball.isEmpty()) return value("-");
    LinearLayout row = Ui.row(this);
    View dot = new View(this);
    dot.setBackground(Ui.circle(ball.equals("LEATHER") ? p.danger : ball.equals("TENNIS") ? p.gold : p.muted));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(12), dp(12));
    lp.setMargins(0, 0, dp(10), 0);
    row.addView(dot, lp);
    row.addView(value(PlayerProfile.label(ball)));
    return row;
  }

  // ---------------------------------------------------------------- summary

  private void summary() {
    List<PlayerStats.Player> mvp = PlayerStats.mvp(match);
    boolean finished = !"Live".equals(match.result());
    int shown = 0;
    if (!mvp.isEmpty() && mvp.get(0).mvpPoints() > 0) {
      PlayerStats.Player top = mvp.get(0);
      String label = finished ? "Player of the match" : "Top performer so far";
      List<String> plain = new ArrayList<>();
      SpannableStringBuilder figures = new SpannableStringBuilder();
      if (top.balls > 0 || top.runs > 0) {
        figures.append(batFigures((int) top.runs, (int) top.balls, (int) top.fours, (int) top.sixes));
        plain.add(MatchReport.battingFigures((int) top.runs, (int) top.balls, (int) top.fours, (int) top.sixes));
      }
      if (top.ballsBowled > 0) {
        if (figures.length() > 0) figures.append('\n');
        figures.append(bowlFigures((int) top.wickets, (int) top.conceded, (int) top.ballsBowled));
        plain.add(MatchReport.bowlingFigures((int) top.wickets, (int) top.conceded, (int) top.ballsBowled,
            match.rules.ballsPerOver));
      }
      if (figures.length() == 0 && top.fieldingInvolvements() > 0) {
        String fielding = top.fieldingInvolvements() == 1 ? " dismissal in the field" : " dismissals in the field";
        figures.append(styled(String.valueOf(top.fieldingInvolvements()), fielding));
        plain.add(top.fieldingInvolvements() + fielding);
      }
      content.addView(starCard(label, top.name, top.team, figures, String.valueOf(top.mvpPoints()),
          p.gold, 0xff17251d, plain), spaced(14));
      shown++;
    }
    MatchReport.BatterLine bat = MatchReport.bestBatter(match);
    if (bat != null) {
      content.addView(starCard("Best batter", bat.name, bat.team,
          batFigures(bat.runs, bat.balls, bat.fours, bat.sixes), points(mvp, bat.name, bat.team),
          p.accent, p.onAccent, Collections.singletonList(bat.figures())), spaced(14));
      shown++;
    }
    MatchReport.BowlerLine bowl = MatchReport.bestBowler(match);
    if (bowl != null) {
      content.addView(starCard("Best bowler", bowl.name, bowl.team,
          bowlFigures(bowl.wickets, bowl.runs, bowl.balls), points(mvp, bowl.name, bowl.team),
          p.accent, p.onAccent, Collections.singletonList(bowl.figures())), spaced(14));
      shown++;
    }
    if (shown == 0) empty("Highlights appear once the first ball is bowled.");
  }

  private static String points(List<PlayerStats.Player> mvp, String name, String team) {
    for (PlayerStats.Player player : mvp)
      if (player.name.equals(name) && player.team.equals(team)) return String.valueOf(player.mvpPoints());
    return "-";
  }

  private CharSequence batFigures(int runs, int balls, int fours, int sixes) {
    return styled(String.valueOf(runs), "(" + balls + ")", "   " + fours, "4s", "   " + sixes, "6s");
  }

  private CharSequence bowlFigures(int wickets, int runs, int balls) {
    return styled(wickets + "-" + runs, " (" + MatchReport.shortOvers(balls, match.rules.ballsPerOver) + " Ov)");
  }

  /** Alternating large and small runs of text: "40" "(27)" "  4" "4s". */
  private CharSequence styled(String... parts) {
    SpannableStringBuilder s = new SpannableStringBuilder();
    for (int i = 0; i < parts.length; i++) {
      int start = s.length();
      s.append(parts[i]);
      if (i % 2 == 1) {
        s.setSpan(new RelativeSizeSpan(0.65f), start, s.length(), 0);
        s.setSpan(new ForegroundColorSpan(p.muted), start, s.length(), 0);
      } else s.setSpan(new StyleSpan(Typeface.BOLD), start, s.length(), 0);
    }
    return s;
  }

  private View starCard(String ribbon, String name, String team, CharSequence figures, String points,
      int ribbonColor, int ribbonInk, List<String> plainFigures) {
    LinearLayout card = Ui.column(this);
    card.setBackground(Ui.round(p.surface, dp(22)));
    card.setElevation(p.dark ? 0 : dp(1));
    card.setPadding(dp(6), dp(6), dp(6), dp(18));

    FrameLayout image = new FrameLayout(this);
    image.setBackground(Ui.round(p.accentSoft, dp(17)));
    image.setClipToOutline(true);
    Bitmap photo = isOwner(team, name) && ownerPhoto != null ? Ui.decode(ownerPhoto, dp(360)) : null;
    if (photo != null) {
      ImageView view = new ImageView(this);
      view.setImageBitmap(photo);
      view.setScaleType(ImageView.ScaleType.CENTER_CROP);
      view.setContentDescription(name);
      image.addView(view, new FrameLayout.LayoutParams(-1, -1));
    } else {
      View avatar = Ui.avatar(this, p, null, name, 132);
      image.addView(avatar, new FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER));
    }
    TextView tag = Ui.text(this, ribbon, 15, ribbonInk, true);
    tag.setPadding(dp(16), dp(10), dp(20), dp(10));
    GradientDrawable shape = Ui.round(ribbonColor, 0);
    shape.setCornerRadii(new float[] {dp(17), dp(17), 0, 0, dp(20), dp(20), 0, 0});
    tag.setBackground(shape);
    image.addView(tag, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
    String shareText = ribbon + " · " + match.teamA + " v " + match.teamB + "\n" + name + " (" + team + ")"
        + (plainFigures.isEmpty() ? "" : "\n" + TextUtils.join("\n", plainFigures))
        + ("-".equals(points) ? "" : "\nMVP points: " + points);
    FrameLayout send = Ui.iconButton(this, p, "share", 42, 0x66000000, 0xffffffff,
        () -> send(shareText, "Share " + ribbon.toLowerCase(Locale.ROOT)));
    send.setContentDescription("Share " + ribbon.toLowerCase(Locale.ROOT));
    pressable(send);
    FrameLayout.LayoutParams sendLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.END);
    sendLp.setMargins(0, dp(12), dp(12), 0);
    image.addView(send, sendLp);
    card.addView(image, new LinearLayout.LayoutParams(-1, dp(250)));

    LinearLayout text = Ui.column(this);
    text.setPadding(dp(14), dp(16), dp(14), 0);
    TextView who = Ui.text(this, name, 24, p.ink, true);
    who.setSingleLine(true);
    who.setEllipsize(TextUtils.TruncateAt.END);
    text.addView(who);
    LinearLayout teamRow = Ui.row(this);
    teamRow.setPadding(0, dp(8), 0, 0);
    teamRow.addView(Ui.text(this, team, 15, p.muted, false), Ui.weight(1));
    teamRow.addView(Ui.text(this, "MVP", 14, p.muted, false));
    text.addView(teamRow);
    LinearLayout figureRow = Ui.row(this);
    figureRow.setPadding(0, dp(10), 0, 0);
    TextView figureText = Ui.text(this, "", 21, p.ink, false);
    figureText.setText(figures);
    figureRow.addView(figureText, Ui.weight(1));
    figureRow.addView(Ui.text(this, points, 26, p.accent, true));
    text.addView(figureRow);
    card.addView(text);
    return card;
  }

  // ---------------------------------------------------------------- scorecard

  private void scorecard() {
    if (match.innings.isEmpty()) { empty("The scorecard appears once the first ball is bowled."); return; }
    for (int i = 0; i < match.innings.size(); i++) content.addView(inningsCard(i), spaced(12));
  }

  private View inningsCard(int index) {
    MatchReport.Scorecard sc = MatchReport.scorecard(match, index);
    boolean open = expanded.contains(index);
    LinearLayout card = Ui.column(this);
    card.setBackground(Ui.round(p.surface, dp(16)));
    card.setClipToOutline(true);
    card.setElevation(p.dark ? 0 : dp(1));

    LinearLayout head = Ui.row(this);
    head.setPadding(dp(16), dp(16), dp(12), dp(16));
    if (open) head.setBackgroundColor(p.accentSoft);
    LinearLayout names = Ui.column(this);
    names.addView(Ui.text(this, sc.battingTeam, 16, p.ink, open));
    if (sc.superOver) {
      TextView so = Ui.text(this, "Super Over", 12, p.accent, true);
      so.setPadding(0, dp(4), 0, 0);
      names.addView(so);
    }
    head.addView(names, Ui.weight(1));
    head.addView(Ui.text(this, sc.runs + "/" + sc.wickets, 20, p.ink, true));
    TextView overs = Ui.text(this, "  (" + sc.overs + " Ov)", 13, p.muted, false);
    head.addView(overs);
    LinearLayout.LayoutParams chevron = new LinearLayout.LayoutParams(dp(22), dp(22));
    chevron.setMargins(dp(10), 0, 0, 0);
    head.addView(new IconView(this, open ? "chevron-up" : "chevron-down", p.ink), chevron);
    head.setContentDescription(sc.battingTeam + " innings, " + (open ? "collapse" : "expand"));
    head.setOnClickListener(v -> {
      if (!expanded.remove(index)) expanded.add(index);
      rerender();
    });
    pressable(head);
    card.addView(head);
    if (!open) return card;

    LinearLayout body = Ui.column(this);
    body.setPadding(dp(14), dp(12), dp(14), dp(14));
    if (sc.batters.isEmpty() && sc.bowlers.isEmpty()) {
      body.addView(Ui.text(this, "No balls bowled in this innings yet.", 14, p.muted, false));
      card.addView(body);
      return card;
    }
    body.addView(tableRow(Ui.text(this, "Batters", 13, p.muted, true),
        new String[] {"R", "B", "4s", "6s", "SR"}, BAT_COLUMNS, true, 0));
    for (MatchReport.BatterLine b : sc.batters) {
      LinearLayout who = Ui.column(this);
      who.addView(Ui.text(this, b.name + (b.notOut() ? "*" : ""), 14, p.accent, true));
      TextView how = Ui.text(this, b.dismissal, 12, p.muted, false);
      how.setPadding(0, dp(3), dp(6), 0);
      who.addView(how);
      body.addView(tableRow(who, new String[] {String.valueOf(b.runs), String.valueOf(b.balls),
          String.valueOf(b.fours), String.valueOf(b.sixes), rate(b.strikeRate(), 0)}, BAT_COLUMNS, false, 0));
    }
    body.addView(Ui.divider(this, p));
    body.addView(summaryRow("Extras", styled(String.valueOf(sc.extras),
        sc.extrasDetail.isEmpty() ? "" : "  (" + sc.extrasDetail + ")")));
    body.addView(Ui.divider(this, p));
    SpannableStringBuilder total = new SpannableStringBuilder(
        styled(sc.runs + "/" + sc.wickets, "  (" + sc.overs + " Ov)"));
    total.append(styled("  CRR " + rate(sc.runRate, 2)));
    body.addView(summaryRow("Total", total));
    if (!sc.yetToBat.isEmpty()) {
      TextView label = Ui.text(this, "Yet to bat:", 13, p.ink, true);
      label.setPadding(0, dp(14), 0, dp(4));
      body.addView(label);
      body.addView(Ui.text(this, TextUtils.join(", ", sc.yetToBat), 13, p.muted, false));
    }

    if (!sc.bowlers.isEmpty()) {
      View gap = Ui.space(this, 1, 22);
      body.addView(gap);
      body.addView(tableRow(Ui.text(this, "Bowlers", 13, p.muted, true),
          new String[] {"O", "M", "R", "W", "Eco"}, BOWL_COLUMNS, true, 3));
      for (MatchReport.BowlerLine b : sc.bowlers)
        body.addView(tableRow(Ui.text(this, b.name, 14, p.accent, true), new String[] {b.overs(),
            String.valueOf(b.maidens), String.valueOf(b.runs), String.valueOf(b.wickets), rate(b.economy(), 2)},
            BOWL_COLUMNS, false, 3));
    }

    if (!sc.falls.isEmpty()) {
      body.addView(Ui.space(this, 1, 22));
      LinearLayout header = Ui.row(this);
      header.setPadding(0, 0, 0, dp(8));
      header.addView(Ui.text(this, "Fall of wickets", 13, p.muted, true), Ui.weight(1));
      header.addView(Ui.text(this, "Score (over)", 13, p.muted, true));
      body.addView(header);
      for (MatchReport.Fall f : sc.falls) {
        body.addView(Ui.divider(this, p));
        LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView who = Ui.text(this, "", 14, p.accent, true);
        SpannableStringBuilder s = new SpannableStringBuilder(f.wicket + ".  ");
        s.setSpan(new ForegroundColorSpan(p.muted), 0, s.length(), 0);
        s.append(f.batter);
        who.setText(s);
        row.addView(who, Ui.weight(1));
        TextView score = Ui.text(this, "", 14, p.ink, false);
        score.setText(styled(String.valueOf(f.runs), "  (" + f.over + " Ov)"));
        row.addView(score);
        body.addView(row);
      }
    }
    card.addView(body);
    return card;
  }

  /** One table line: a stretching first column, then fixed numeric cells with {@code strong} in bold. */
  private LinearLayout tableRow(View first, String[] cells, int[] widths, boolean header, int strong) {
    LinearLayout row = Ui.row(this);
    row.setPadding(0, dp(header ? 4 : 8), 0, dp(header ? 8 : 8));
    row.setGravity(header ? Gravity.CENTER_VERTICAL : Gravity.TOP);
    row.addView(first, Ui.weight(1));
    for (int i = 0; i < cells.length; i++) {
      TextView cell = Ui.text(this, cells[i], 13, header ? p.muted : p.ink, header || i == strong);
      cell.setGravity(Gravity.END);
      row.addView(cell, new LinearLayout.LayoutParams(dp(widths[i]), -2));
    }
    return row;
  }

  private LinearLayout summaryRow(String label, CharSequence value) {
    LinearLayout row = Ui.row(this);
    row.setPadding(0, dp(10), 0, dp(10));
    row.addView(Ui.text(this, label, 14, p.ink, false), Ui.weight(1));
    TextView v = Ui.text(this, "", 14, p.ink, false);
    v.setText(value);
    row.addView(v);
    return row;
  }

  private static String rate(double v, int decimals) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return "-";
    return decimals == 0 ? String.valueOf(Math.round(v)) : String.format(Locale.US, "%." + decimals + "f", v);
  }

  // ---------------------------------------------------------------- overs

  private void overs() {
    if (match.innings.isEmpty()) { empty("Overs appear once the first ball is bowled."); return; }
    if (oversInnings < 0 || oversInnings >= match.innings.size()) {
      oversInnings = 0;
      for (int i = 0; i < match.innings.size(); i++) if (!match.progressAt(i).isEmpty()) oversInnings = i;
    }
    HorizontalScrollView strip = new HorizontalScrollView(this);
    strip.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = Ui.row(this);
    for (int i = 0; i < match.innings.size(); i++) {
      int index = i;
      TextView chip = Ui.chip(this, p, inningsName(i), i == oversInnings, () -> {
        oversInnings = index;
        render();
      });
      pressable(chip);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.setMargins(0, 0, dp(8), 0);
      chips.addView(chip, lp);
    }
    strip.addView(chips);
    content.addView(strip, spaced(14));
    List<MatchReport.Over> list = MatchReport.overs(match, oversInnings);
    if (list.isEmpty()) { empty("No balls bowled in this innings yet."); return; }
    for (int k = list.size() - 1; k >= 0; k--) content.addView(overCard(list.get(k)), spaced(10));
  }

  private View overCard(MatchReport.Over over) {
    LinearLayout card = Ui.row(this);
    card.setGravity(Gravity.NO_GRAVITY);
    card.setBackground(Ui.round(p.surface, dp(16)));
    card.setClipToOutline(true);
    card.setElevation(p.dark ? 0 : dp(1));

    LinearLayout left = Ui.column(this);
    left.setGravity(Gravity.CENTER_VERTICAL);
    left.setPadding(dp(14), dp(12), dp(6), dp(12));
    left.addView(Ui.text(this, "Ov " + over.number, 17, p.ink, true));
    TextView score = Ui.text(this, over.scoreRuns + "-" + over.scoreWickets, 13, p.muted, false);
    score.setPadding(0, dp(6), 0, 0);
    left.addView(score);
    card.addView(left, new LinearLayout.LayoutParams(dp(76), -1));

    View line = new View(this);
    line.setBackgroundColor(p.line);
    LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(dp(1), -1);
    lineLp.setMargins(0, dp(14), 0, dp(14));
    card.addView(line, lineLp);

    LinearLayout middle = Ui.column(this);
    middle.setPadding(dp(12), dp(12), dp(10), dp(14));
    String bowlers = over.bowlers.isEmpty() ? "" : TextUtils.join(" & ", over.bowlers);
    String batters = TextUtils.join(", ", over.batters);
    String who = bowlers.isEmpty() ? batters : batters.isEmpty() ? bowlers : bowlers + " to " + batters;
    if (!who.isEmpty()) {
      TextView names = Ui.text(this, who, 14, p.ink, false);
      names.setPadding(0, 0, 0, dp(10));
      middle.addView(names);
    }
    Flow balls = new Flow(this, dp(6));
    for (MatchReport.Ball ball : over.balls) balls.addView(ballChip(ball), new ViewGroup.LayoutParams(-2, dp(32)));
    middle.addView(balls, new LinearLayout.LayoutParams(-1, -2));
    card.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));

    LinearLayout right = Ui.column(this);
    right.setGravity(Gravity.CENTER);
    right.setBackgroundColor(p.surface2);
    right.addView(Ui.text(this, String.valueOf(over.runs), 20, p.ink, true));
    TextView runs = Ui.text(this, over.runs == 1 ? "Run" : "Runs", 12, p.muted, false);
    runs.setPadding(0, dp(6), 0, 0);
    right.addView(runs);
    card.addView(right, new LinearLayout.LayoutParams(dp(64), -1));
    return card;
  }

  private TextView ballChip(MatchReport.Ball ball) {
    int fill, ink;
    GradientDrawable shape;
    switch (ball.kind) {
      case WICKET:
        fill = p.danger;
        ink = p.onAccent;
        shape = Ui.round(fill, dp(10));
        break;
      case BOUNDARY:
        ink = p.accent;
        shape = Ui.round(p.accentSoft, dp(10));
        break;
      case EXTRA:
        ink = p.danger;
        shape = Ui.outline(p.surface2, p.danger, Math.max(1, dp(1)), dp(10));
        break;
      default:
        ink = p.ink;
        shape = Ui.round(p.surface2, dp(10));
    }
    TextView chip = Ui.text(this, ball.label, 13, ink, true);
    chip.setGravity(Gravity.CENTER);
    chip.setMinWidth(dp(32));
    chip.setPadding(dp(7), 0, dp(7), 0);
    chip.setBackground(shape);
    return chip;
  }

  // ---------------------------------------------------------------- squads

  private void squads() {
    List<String> a = MatchReport.squad(match, match.teamA), b = MatchReport.squad(match, match.teamB);
    if (a.isEmpty() && b.isEmpty()) { empty("No squad was recorded for this match."); return; }
    if (alphabetical) {
      Collections.sort(a, String.CASE_INSENSITIVE_ORDER);
      Collections.sort(b, String.CASE_INSENSITIVE_ORDER);
    }
    LinearLayout sort = Ui.row(this);
    sort.setBackground(Ui.outline(p.surface, p.line, Math.max(1, dp(1)), dp(12)));
    sort.setPadding(dp(14), dp(10), dp(10), dp(10));
    sort.addView(Ui.text(this, alphabetical ? "By alphabetic order" : "By squad order", 15, p.ink, false));
    LinearLayout.LayoutParams chevron = new LinearLayout.LayoutParams(dp(18), dp(18));
    chevron.setMargins(dp(8), 0, 0, 0);
    sort.addView(new IconView(this, "chevron-down", p.ink), chevron);
    sort.setOnClickListener(this::sortMenu);
    pressable(sort);
    LinearLayout.LayoutParams sortLp = new LinearLayout.LayoutParams(-2, -2);
    sortLp.setMargins(0, 0, 0, dp(14));
    content.addView(sort, sortLp);

    LinearLayout card = Ui.column(this);
    card.setBackground(Ui.round(p.surface, dp(16)));
    card.setClipToOutline(true);
    card.setElevation(p.dark ? 0 : dp(1));

    LinearLayout head = Ui.row(this);
    head.addView(teamCell(match.teamA, p.accent, p.onAccent, false), new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(columnRule(), new LinearLayout.LayoutParams(Math.max(1, dp(1)), -1));
    head.addView(teamCell(match.teamB, p.gold, 0xff17251d, true), new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(head);
    TextView playing = Ui.text(this, "Playing Squad", 14, p.ink, true);
    playing.setGravity(Gravity.CENTER);
    playing.setBackgroundColor(p.surface2);
    playing.setPadding(0, dp(11), 0, dp(11));
    card.addView(playing, new LinearLayout.LayoutParams(-1, -2));
    for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
      if (i > 0) card.addView(Ui.divider(this, p));
      LinearLayout row = Ui.row(this);
      row.addView(playerCell(match.teamA, i < a.size() ? a.get(i) : null, false), new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(columnRule(), new LinearLayout.LayoutParams(Math.max(1, dp(1)), -1));
      row.addView(playerCell(match.teamB, i < b.size() ? b.get(i) : null, true), new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(row);
    }
    content.addView(card);
  }

  private void sortMenu(View anchor) {
    LinearLayout menu = Ui.column(this);
    menu.setPadding(0, dp(6), 0, dp(6));
    PopupWindow popup = new PopupWindow(menu, -2, -2, true);
    popup.setBackgroundDrawable(Ui.round(p.surface, dp(14)));
    popup.setElevation(dp(10));
    for (boolean option : new boolean[] {true, false}) {
      boolean on = option == alphabetical;
      TextView item = Ui.text(this, option ? "By alphabetic order" : "By squad order", 15, on ? p.accent : p.ink, on);
      item.setPadding(dp(20), dp(13), dp(28), dp(13));
      item.setOnClickListener(v -> {
        popup.dismiss();
        alphabetical = option;
        render();
      });
      pressable(item);
      menu.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }
    popup.showAsDropDown(anchor, 0, dp(6));
  }

  private View teamCell(String team, int badgeColor, int badgeInk, boolean end) {
    LinearLayout cell = Ui.row(this);
    cell.setGravity(Gravity.CENTER_VERTICAL | (end ? Gravity.END : Gravity.START));
    cell.setPadding(dp(12), dp(14), dp(12), dp(14));
    TextView badge = Ui.text(this, Ui.initials(team), 15, badgeInk, true);
    badge.setGravity(Gravity.CENTER);
    badge.setBackground(Ui.round(badgeColor, dp(10)));
    LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(42), dp(42));
    TextView name = Ui.text(this, team, 15, p.accent, true);
    name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    name.setPadding(dp(10), 0, dp(10), 0);
    name.setGravity(end ? Gravity.END : Gravity.START);
    if (end) {
      cell.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      cell.addView(badge, badgeLp);
    } else {
      cell.addView(badge, badgeLp);
      cell.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    }
    return cell;
  }

  private View playerCell(String team, String name, boolean end) {
    LinearLayout cell = Ui.row(this);
    cell.setGravity(Gravity.CENTER_VERTICAL | (end ? Gravity.END : Gravity.START));
    cell.setPadding(dp(12), dp(12), dp(12), dp(12));
    cell.setMinimumHeight(dp(68));
    if (name == null) return cell;
    View avatar = Ui.avatar(this, p, isOwner(team, name) ? ownerPhoto : null, name, 44);
    LinearLayout text = Ui.column(this);
    text.setGravity(end ? Gravity.END : Gravity.START);
    text.setPadding(dp(10), 0, dp(10), 0);
    TextView who = Ui.text(this, name, 15, p.ink, false);
    who.setSingleLine(true);
    who.setEllipsize(TextUtils.TruncateAt.END);
    who.setGravity(end ? Gravity.END : Gravity.START);
    text.addView(who, new LinearLayout.LayoutParams(-1, -2));
    String sub = squadNote(team, name);
    if (!sub.isEmpty()) {
      TextView detail = Ui.text(this, sub, 12, p.muted, false);
      detail.setSingleLine(true);
      detail.setEllipsize(TextUtils.TruncateAt.END);
      detail.setGravity(end ? Gravity.END : Gravity.START);
      detail.setPadding(0, dp(4), 0, 0);
      text.addView(detail, new LinearLayout.LayoutParams(-1, -2));
    }
    if (end) {
      cell.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      cell.addView(avatar);
    } else {
      cell.addView(avatar);
      cell.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    }
    return cell;
  }

  /** "You · All-rounder · RHB" from the saved profile, or "" when nothing is recorded. */
  private String squadNote(String team, String name) {
    List<String> parts = new ArrayList<>();
    if (isOwner(team, name)) parts.add("You");
    PlayerProfile profile = profiles.get(Career.key(team, name));
    if (profile != null) {
      if (profile.role != null && !profile.role.isEmpty()) parts.add(profile.role);
      if (profile.battingHand != null && !profile.battingHand.isEmpty()) parts.add(profile.battingHand);
    }
    return TextUtils.join(" · ", parts);
  }

  private View columnRule() {
    View v = new View(this);
    v.setBackgroundColor(p.line);
    return v;
  }

  // ---------------------------------------------------------------- shared bits

  private String inningsName(int index) {
    String team = match.innings.get(index).battingTeam;
    return MatchReport.superOver(match, index) ? team + " · Super Over" : team;
  }

  private boolean isOwner(String team, String name) { return ownerKeys.contains(Career.key(team, name)); }

  private void empty(String message) {
    TextView t = Ui.text(this, message, 15, p.muted, false);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(16), dp(48), dp(16), dp(48));
    content.addView(t, new LinearLayout.LayoutParams(-1, -2));
  }

  private LinearLayout.LayoutParams spaced(int bottomDp) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 0, 0, dp(bottomDp));
    return lp;
  }

  /** Ripple feedback on press, clipped to the view's rounded background. */
  private void pressable(View v) {
    TypedValue tv = new TypedValue();
    if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true) && tv.resourceId != 0)
      v.setForeground(ContextCompat.getDrawable(this, tv.resourceId));
    if (v.getBackground() instanceof GradientDrawable) v.setClipToOutline(true);
    v.setClickable(true);
  }

  private int dp(float v) { return Ui.dp(this, v); }

  /** Lays children left to right, wrapping onto new rows: the ball chips of a long over. */
  static final class Flow extends ViewGroup {
    private final int gap;

    Flow(Context c, int gap) {
      super(c);
      this.gap = gap;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      int max = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
      boolean bounded = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED;
      int x = 0, y = 0, rowHeight = 0, widest = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        measureChild(child, widthSpec, heightSpec);
        int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
        if (bounded && x > 0 && x + w > max) {
          x = 0;
          y += rowHeight + gap;
          rowHeight = 0;
        }
        x += w + gap;
        widest = Math.max(widest, x - gap);
        rowHeight = Math.max(rowHeight, h);
      }
      int width = bounded ? MeasureSpec.getSize(widthSpec) : widest + getPaddingLeft() + getPaddingRight();
      setMeasuredDimension(width, y + rowHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
      int max = r - l - getPaddingLeft() - getPaddingRight();
      int x = 0, y = 0, rowHeight = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
        if (x > 0 && x + w > max) {
          x = 0;
          y += rowHeight + gap;
          rowHeight = 0;
        }
        child.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
        x += w + gap;
        rowHeight = Math.max(rowHeight, h);
      }
    }
  }
}
