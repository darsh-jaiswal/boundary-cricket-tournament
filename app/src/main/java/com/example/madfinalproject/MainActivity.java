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
import com.example.madfinalproject.domain.RegisteredPlayer;
import com.example.madfinalproject.domain.Cricket.*;
import java.util.*;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
  private static final int INK = 0xff17251d,
      GREEN = 0xff147653,
      PAPER = 0xfff3f6ef,
      MUTED = 0xff66756b,
      LIME = 0xffd0ed8c;
  private Repository repo;
  private Match match;
  private String page = "Home", role = "Organizer";
  private LinearLayout root, body;
  private TextToSpeech tts;
  private boolean audioReady, pa;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private AlertDialog pending;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    repo = new Repository(this);
    String id = getPreferences(0).getString("active", "");
    for (Match m : repo.matches()) if (m.id.equals(id)) match = m;
    role = getPreferences(0).getString("role", "Organizer");
    tts = new TextToSpeech(this, status -> audioReady = status == TextToSpeech.SUCCESS);
    draw();
  }

  @Override
  protected void onDestroy() {
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
    b.setTextColor(primary ? 0xff031b1b : INK);
    b.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(primary ? GREEN : 0xff272727));
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
    c.setBackground(bg(Color.WHITE, 18));
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
    LinearLayout head = new LinearLayout(this);
    head.setPadding(dp(18), dp(8), dp(18), dp(8));
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setBackgroundColor(Color.WHITE);
    head.addView(text("Boundary", 22, GREEN, true), new LinearLayout.LayoutParams(0, dp(42), 1));
    Button roleButton = button(role + " ▾", false, this::roles);
    roleButton.setMinWidth(0);
    roleButton.setPadding(dp(8), 0, dp(8), 0);
    head.addView(roleButton);
    Button create = button("+", true, this::quickCreate);
    create.setContentDescription("Create match, tournament, team or broadcast");
    head.addView(create, new LinearLayout.LayoutParams(dp(52), dp(48)));
    root.addView(head);
    if (isMyCricket()) {
      TextView section = text("Your Cricket", 27, GREEN, false);
      section.setPadding(dp(18), dp(12), dp(18), dp(10));
      root.addView(section);
      HorizontalScrollView strip = new HorizontalScrollView(this);
      strip.setHorizontalScrollBarEnabled(false);
      LinearLayout tabs = new LinearLayout(this);
      String[] keys = {"Matches", "Cups", "Teams", "Stats", "Score"};
      String[] names = {"Matches", "Tournaments", "Teams", "Stats", "Scoring"};
      for (int index = 0; index < keys.length; index++) {
        final String key = keys[index];
        Button tab = button(names[index], false, () -> { page = key; draw(); });
        tab.setTextColor(key.equals(page) ? GREEN : MUTED);
        tabs.addView(tab);
      }
      strip.addView(tabs);
      root.addView(strip);
    }
    ScrollView scroll = new ScrollView(this);
    body = new LinearLayout(this);
    body.setOrientation(1);
    body.setPadding(dp(18), dp(10), dp(18), dp(24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout nav = new LinearLayout(this);
    nav.setBackgroundColor(Color.WHITE);
    for (String tab : new String[] {"Home", "Looking", "Matches", "Community", "Store"}) {
      Button b =
          button(
              tab.equals("Matches") ? "My Cricket" : tab,
              tab.equals(page) || tab.equals("Matches") && isMyCricket(),
              () -> {
                page = tab;
                draw();
              });
      b.setTextSize(10);
      b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
      b.setTextColor(tab.equals(page) || tab.equals("Matches") && isMyCricket() ? GREEN : MUTED);
      b.setPadding(0, 0, 0, 0);
      nav.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    }
    root.addView(nav);
    switch (page) {
      case "Matches":
        matchDirectory();
        break;
      case "Stats":
        heading("Player performances", "Stats & rankings", "Career figures from matches saved on this device.");
        action(body, "Open player statistics", () -> startActivity(new Intent(this, PlayerDirectoryActivity.class)));
        break;
      case "Looking":
      case "Community":
        community();
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
      default:
        home();
    }
  }

  private boolean isMyCricket() {
    return Arrays.asList("Matches", "Cups", "Teams", "Stats", "Score").contains(page);
  }

  private void quickCreate() {
    String[] choices = {"Start a match", "Go live", "Add a tournament / series", "Add team", "Looking for", "Ground tools"};
    new AlertDialog.Builder(this).setTitle("Create").setItems(choices, (dialog, index) -> guard(() -> {
      switch (index) {
        case 0: if (writable()) matchSetup(null, null); break;
        case 1: startActivity(new Intent(this, LiveStreamActivity.class)); break;
        case 2: newCup(); break;
        case 3: page = "Teams"; draw(); break;
        case 4: page = "Looking"; draw(); break;
        default: page = "Tools"; draw();
      }
    })).show();
  }

  private void community() {
    heading("Find your cricket circle", page.equals("Looking") ? "Looking for" : "Community",
        "Players, opponents, officials and places to play.");
    String[] labels = {"Players", "Opponents", "Scorers", "Umpires", "Grounds", "Coaches", "Cricket updates"};
    String[] categories = {"players", "opponents", "scorers", "umpires", "grounds", "coaches", "updates"};
    for (int index = 0; index < labels.length; index++) {
      final String category = categories[index];
      action(body, labels[index], () -> startActivity(new Intent(Intent.ACTION_VIEW,
          Uri.parse("https://boundary-cricket-tournament.vercel.app/community.html?category=" + category))));
    }
  }

  private void matchDirectory() {
    EditText search = new EditText(this);
    search.setHint("Quick search matches, teams or ground");
    search.setSingleLine();
    body.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
    Spinner filter = new Spinner(this);
    filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
        new String[] {"All matches", "Live", "Completed"}));
    body.addView(filter);
    action(body, "Start your match", () -> { if (writable()) matchSetup(null, null); });
    LinearLayout results = new LinearLayout(this);
    results.setOrientation(1);
    body.addView(results);
    final List<Match> saved = repo.matches();
    Runnable render = () -> {
      results.removeAllViews();
      String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
      int count = 0;
      for (Match item : saved) {
        boolean live = item.result().equals("Live");
        if (filter.getSelectedItemPosition() == 1 && !live || filter.getSelectedItemPosition() == 2 && live) continue;
        if (!(item.name + " " + item.venue + " " + item.teamA + " " + item.teamB).toLowerCase(Locale.ROOT).contains(query)) continue;
        State score = item.state();
        Button row = button(item.name + "\n" + score.runs + "/" + score.wickets + " (" + score.overs(item.rules) + ") · "
            + item.result(), false, () -> open(item));
        row.setMinHeight(dp(88));
        results.addView(row, new LinearLayout.LayoutParams(-1, -2));
        count++;
      }
      if (count == 0) results.addView(text(saved.isEmpty() ? "No matches yet. Start your first match above." : "No matching scorecards.", 16, MUTED, false));
    };
    search.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
      public void afterTextChanged(android.text.Editable value) {}
    });
    filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { render.run(); }
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    render.run();
  }

  private void roles() {
    String[] rs = {"Organizer", "Manager", "Scorer", "Umpire", "Captain", "Player", "Spectator"};
    new AlertDialog.Builder(this)
        .setTitle("Workspace role")
        .setItems(
            rs,
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
          "Choose the assigned organizer, scorer or umpire workspace to make match changes.");
      return false;
    }
    return true;
  }

  private void home() {
    heading(
        "My cricket",
        "Matches",
        "Your live scores, teams and tournament fixtures.");
    LinearLayout hero =
        card(
            "Match-day desk",
            "Ready for the first ball?",
            "Set up teams and officials, choose your local rules, and score with or without"
                + " internet.");
    action(
        hero,
        "Create a match",
        () -> {
          if (writable()) matchSetup(null, null);
        });
    action(
        hero,
        "Browse public live matches",
        () -> startActivity(new Intent(this, SpectatorActivity.class)));
    action(hero, "My phone account", () -> startActivity(new Intent(this, PlayerAccountActivity.class)));
    action(hero, "Ground tools", () -> { page = "Tools"; draw(); });
    action(hero, "Shop Boundary merchandise", () -> { page = "Store"; draw(); });
    action(hero, "Community · players, opponents & officials", () -> startActivity(
        new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://boundary-cricket-tournament.vercel.app/community.html"))));
    for (Match m : repo.matches()) {
      State s = m.state();
      LinearLayout c =
          card(
              m.result().equals("Live") ? "IN PROGRESS" : m.result(),
              m.name,
              s.runs + "/" + s.wickets + " · " + s.overs(m.rules) + " overs\n" + m.venue);
      action(c, "Open scorecard", () -> open(m));
    }
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
      card("Imported past scorecard", sheet.name, summary.toString());
    }
    if (repo.matches().isEmpty())
      card(
          "Your first match starts here",
          "Start with your teams",
          "Add squads in Teams, then create a match or generate a tournament schedule.");
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
              if (id.isEmpty()) { message("Phone registration required", name + " must be added by their registered phone number first."); return; }
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
              if (!isFinishing() && !isDestroyed()) message("Player registration check", error.getMessage() == null ? "Connect to the internet and check your phone account." : error.getMessage());
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
      CheckBox player = check(box, team.players.get(i) + (registered ? " ? Phone registered" : " ? Registration required"), registered && eligible < size);
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
          box.addView(text("Save your team, then add players using their registered phone numbers.", 14, MUTED, false));
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
      action(c, "Add player by phone", () -> {
        if (!writable()) return;
        getPreferences(0).edit().putString("addingPlayerTeam", t.id).apply();
        startActivityForResult(new Intent(this, PlayerAccountActivity.class).putExtra("pick", true), 88);
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
                startActivityForResult(new Intent(this, PlayerAccountActivity.class).putExtra("pick", true), 88);
              } else career(t.players.get(w), t.name);
            })
        .show();
  }

  private void career(String player, String team) {
    startActivity(new Intent(this, PlayerProfileActivity.class)
        .putExtra("player", player).putExtra("team", team));
  }

  private void openPolicy(String path) {
    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PlayerAccount.ORIGIN + path)));
  }

  private void store() {
    heading("Boundary club shop", "Wear your cricket", "T-shirts, full-sleeve jerseys, caps and bottles.");
    LinearLayout shop = card("TEAMWEAR & ESSENTIALS", "Made for your match day",
        "Choose your size, colour and material. Browse cotton, performance and premium options for teamwear, plus caps and bottles.");
    action(shop, "Open shop & secure Razorpay checkout", () -> startActivity(new Intent(Intent.ACTION_VIEW,
        Uri.parse("https://boundary-cricket-tournament.vercel.app/shop.html"))));
    action(shop, "Terms & Conditions", () -> openPolicy("/terms.html"));
    action(shop, "Privacy Policy", () -> openPolicy("/privacy.html"));
    action(shop, "My orders", () -> startActivity(new Intent(Intent.ACTION_VIEW,
        Uri.parse("https://boundary-cricket-tournament.vercel.app/shop.html#orders"))));
    action(body, "Ground tools", () -> { page = "Tools"; draw(); });
  }

  private void tools() {
    heading(
        "Intelligence & ground tools",
        "Match-day toolkit",
        "Voice, camera, imports, announcements and spectator publishing.");
    LinearLayout v =
        card(
            "Hands-free scorer",
            "Voice commands",
            "Speak scoring and correction commands. Each recognized command is confirmed before"
                + " execution.");
    action(v, "Speak a command", this::voice);
    LinearLayout a =
        card(
            "Stadium PA",
            "Ground announcements",
            pa
                ? "Announcements enabled"
                : "Connect a Bluetooth speaker using Android audio settings, then enable match"
                    + " announcements.");
    action(
        a,
        pa ? "Disable announcer" : "Enable announcer",
        () -> {
          pa = !pa;
          draw();
        });
    LinearLayout f = card("Captain’s co-pilot", "Field placement radar", tactic());
    FieldView field = new FieldView(this);
    field.recommend(
        batterHistory().zones,
        match != null && match.state().legal < match.rules.powerplay * match.rules.ballsPerOver);
    f.addView(field, new LinearLayout.LayoutParams(-1, dp(300)));
    f.addView(text("Drag the nine fielders to adjust your plan.", 12, MUTED, false));
    LinearLayout scan =
        card(
            "Paper scorebook",
            "Import & review",
            "Choose a scoresheet image, extract text, and review the result before adding a past"
                + " match.");
    action(scan, "Scan scorebook", () -> startActivity(new Intent(this, ScannerActivity.class)));
    LinearLayout cam =
        card(
            "Square-leg review",
            "Stump cam",
            "Record short rolling segments and retain the latest action for slow playback.");
    action(cam, "Open stump cam", () -> startActivity(new Intent(this, StumpCamActivity.class)));
    LinearLayout stream = card("Camera & microphone", "Live broadcast",
        "Stream to your RTMP service from this phone.");
    action(stream, "Open live broadcast", () -> startActivity(new Intent(this, LiveStreamActivity.class)));
    LinearLayout sync =
        card("Public spectator scoreboard", "Publish match", Publication.status(this, repo));
    action(
        sync,
        "Publish / sync saved matches",
        () -> {
          Publication.sync(this, repo);
          Toast.makeText(this, "Sync requested", Toast.LENGTH_SHORT).show();
        });
    action(sync, "Recover full cloud match", this::recoverCloud);
    action(sync, "Share broadcast overlay", () -> {
      if (match == null) {
        message("Select a match", "Open a match before sharing its broadcast scoreboard.");
        return;
      }
      Intent share = new Intent(Intent.ACTION_SEND);
      share.setType("text/plain");
      share.putExtra(Intent.EXTRA_TEXT,
          "Boundary broadcast scoreboard: " + match.name
              + "\nhttps://boundary-cricket-tournament.vercel.app/overlay.html?match="
              + Uri.encode(match.id)
              + "\n\nAdd this URL as an OBS browser source (1280 x 720)."
              + " Publish the match from Boundary to update the score."
              + " This is a scoreboard overlay; video comes from your streaming setup.");
      startActivity(Intent.createChooser(share, "Share broadcast overlay"));
    });
    action(
        sync,
        "Show match recovery code",
        () -> {
          if (!writable() || match == null) return;
          String key = getSharedPreferences("publication", 0).getString("key-" + match.id, "");
          if (key.isEmpty()) {
            message(
                "Publish first", "Score or publish this match before creating a recovery code.");
            return;
          }
          message(
              "Keep this code private",
              match.id
                  + ":"
                  + key
                  + "\n\n"
                  + "This code gives full scoring access to this match. Keep it with the assigned"
                  + " official.");
        });
    action(
        sync,
        "Switch to spectator",
        () -> {
          role = "Spectator";
          getPreferences(0).edit().putString("role", role).apply();
          draw();
        });
    action(
        sync,
        "Open spectator website",
        () ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://boundary-cricket-tournament.vercel.app/"))));
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
      String teamId = getPreferences(0).getString("addingPlayerTeam", "");
      String name = data.getStringExtra("playerName"), accountId = data.getStringExtra("playerId");
      if (name == null || name.isEmpty() || accountId == null || !accountId.startsWith("user_")) return;
      for (Team team : repo.teams()) if (team.id.equals(teamId)) {
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
