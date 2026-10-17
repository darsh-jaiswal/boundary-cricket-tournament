package com.example.madfinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.madfinalproject.data.Repository;
import com.example.madfinalproject.domain.PlayerStats;
import java.util.*;

/** Rankings within the scoring device's saved journals, without subscription gates. */
public final class PlayerDirectoryActivity extends AppCompatActivity {
  private Repository repository;
  private LinearLayout results;
  private EditText search;
  private Spinner category;
  private List<PlayerStats.Player> players;

  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    repository = new Repository(this);
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(1);
    body.setPadding(24, 20, 24, 24);
    scroll.addView(body);
    setContentView(scroll);
    UiInsets.apply(scroll);
    Button back = new Button(this); back.setText("Back to My Cricket");
    back.setOnClickListener(v -> finish()); body.addView(back);
    TextView title = new TextView(this); title.setText("Stats & player rankings"); title.setTextSize(25); body.addView(title);
    TextView scope = new TextView(this);
    String tournamentId = getIntent().getStringExtra("tournament");
    scope.setText(tournamentId == null
        ? "All statistics are free. Rankings include live and completed matches saved on this device. These are not worldwide rankings."
        : "Tournament rankings from this device's saved live and completed scorecards. All statistics are free.");
    body.addView(scope);
    search = new EditText(this); search.setHint("Search player or team");search.setSingleLine();body.addView(search);
    category = new Spinner(this);
    category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
        new String[] {"Batting · runs", "Bowling · wickets", "Fielding · involvements", "All-round · Boundary points"}));
    body.addView(category);
    category.setSelection(Math.max(0, Math.min(3, getIntent().getIntExtra("category", 0))));
    results = new LinearLayout(this);results.setOrientation(1);body.addView(results);
    try {
      List<com.example.madfinalproject.domain.Cricket.Match> matches = repository.matches();
      if (tournamentId != null) matches.removeIf(match -> !tournamentId.equals(match.tournament));
      players = PlayerStats.players(matches);
    }
    catch (RuntimeException error) {
      TextView failure = new TextView(this);failure.setText("Statistics could not load: " + error.getMessage());results.addView(failure);return;
    }
    search.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
      public void onTextChanged(CharSequence s,int start,int before,int count) { render(); }
      public void afterTextChanged(Editable value) {}
    });
    category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { render(); }
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    render();
  }

  private void render() {
    results.removeAllViews();
    int selected = category.getSelectedItemPosition();
    Comparator<PlayerStats.Player> comparator;
    if (selected == 1) comparator = Comparator.comparingLong((PlayerStats.Player p) -> p.wickets).reversed();
    else if (selected == 2) comparator = Comparator.comparingInt(PlayerStats.Player::fieldingInvolvements).reversed();
    else if (selected == 3) comparator = Comparator.comparingLong(PlayerStats.Player::mvpPoints).reversed();
    else comparator = Comparator.comparingLong((PlayerStats.Player p) -> p.runs).reversed();
    List<PlayerStats.Player> sorted = new ArrayList<>(players);
    sorted.sort(comparator.thenComparing(p -> p.name).thenComparing(p -> p.team));
    String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
    int rank = 0, shown = 0;
    for (PlayerStats.Player player : sorted) {
      rank++;
      if (!(player.name + " " + player.team).toLowerCase(Locale.ROOT).contains(query)) continue;
      String figures = selected == 1 ? player.wickets + " wickets · " + player.conceded + " conceded"
          : selected == 2 ? player.catches + " catches · " + player.runOuts + " run-outs · " + player.stumpings + " stumpings"
          : selected == 3 ? player.mvpPoints() + " points (runs + 20 per wicket + 8 per fielding involvement)"
          : player.runs + " runs · " + player.innings + " innings · " + player.fours + " fours · " + player.sixes + " sixes";
      Button row = new Button(this);row.setAllCaps(false);
      row.setText(rank + ". " + player.name + " · " + player.team + "\n" + figures);
      row.setOnClickListener(v -> startActivity(new Intent(this, PlayerProfileActivity.class)
          .putExtra("player", player.name).putExtra("team", player.team)));
      results.addView(row,new LinearLayout.LayoutParams(-1,-2));shown++;
    }
    if (shown == 0) {TextView empty = new TextView(this);empty.setText(players.isEmpty()
        ? "Score a match to build player statistics and rankings." : "No players match your search.");results.addView(empty);}
  }

  @Override protected void onDestroy() {if(repository != null)repository.close();super.onDestroy();}
}
