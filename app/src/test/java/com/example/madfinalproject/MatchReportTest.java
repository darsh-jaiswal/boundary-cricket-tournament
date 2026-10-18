package com.example.madfinalproject;

import static org.junit.Assert.*;

import com.example.madfinalproject.domain.Cricket.*;
import com.example.madfinalproject.domain.MatchReport;
import java.util.*;
import org.junit.Test;

public class MatchReportTest {
  private static long clock = 1000;

  private static Event event(Kind kind) {
    Event e = new Event();
    e.kind = kind;
    e.created = clock++;
    return e;
  }

  private static void bowler(Match m, String name) {
    Event e = event(Kind.BOWLER);
    e.player = name;
    m.add(e);
  }

  private static void run(Match m, int runs) {
    Event e = event(Kind.BALL);
    e.bat = runs;
    e.completed = runs == 4 || runs == 6 ? 0 : runs;
    e.boundary = runs == 4 || runs == 6;
    m.add(e);
  }

  private static void extra(Match m, Extra kind, int bat, int extras, int completed) {
    Event e = event(Kind.BALL);
    e.extra = kind;
    e.bat = bat;
    e.extras = extras;
    e.completed = completed;
    m.add(e);
  }

  private static void out(Match m, Dismissal how, String fielder, String incoming) {
    Event e = event(Kind.BALL);
    e.dismissal = how;
    e.fielder = fielder;
    e.incoming = incoming;
    m.add(e);
  }

  private static List<String> names(List<MatchReport.BatterLine> lines) {
    List<String> names = new ArrayList<>();
    for (MatchReport.BatterLine b : lines) names.add(b.name);
    return names;
  }

  private static List<String> labels(MatchReport.Over over) {
    List<String> labels = new ArrayList<>();
    for (MatchReport.Ball b : over.balls) labels.add(b.label);
    return labels;
  }

  @Test
  public void scorecardWordsEveryDismissalAndListsFallsInBallNotation() {
    Match m = new Match("Lions", "Tigers");
    m.rules.overs = 2;
    m.rosterA.addAll(Arrays.asList("Ravi", "Dev", "Sam", "Om", "Raj", "Ali", "Ben", "Cal", "Eli", "Fin", "Gus"));
    m.rosterB.addAll(Arrays.asList("Kabir", "Zed", "Arjun", "Jay"));
    m.innings.add(new Innings("Lions", "Tigers", "Ravi", "Dev", 0));
    bowler(m, "Kabir");
    out(m, Dismissal.CAUGHT, "Arjun", "Sam");
    out(m, Dismissal.BOWLED, "", "Om");
    out(m, Dismissal.LBW, "", "Raj");
    out(m, Dismissal.CAUGHT, "Kabir", "Ali");
    out(m, Dismissal.STUMPED, "Jay", "Ben");
    out(m, Dismissal.HIT_WICKET, "", "Cal"); // sixth ball: Dev now faces, Cal at the other end
    bowler(m, "Zed");
    Event runOut = event(Kind.BALL);
    runOut.bat = 1;
    runOut.completed = 1;
    runOut.dismissal = Dismissal.RUN_OUT;
    runOut.fielder = "Arjun";
    runOut.nonStrikerOut = true; // Cal, backing up, is run out; Eli comes in
    runOut.incoming = "Eli";
    m.add(runOut);
    Event retire = event(Kind.RETIRE);
    retire.player = "Eli";
    retire.incoming = "Fin";
    m.add(retire);
    run(m, 0);
    extra(m, Extra.WIDE, 0, 0, 0);

    MatchReport.Scorecard card = MatchReport.scorecard(m, 0);
    assertEquals(Arrays.asList("Ravi", "Dev", "Sam", "Om", "Raj", "Ali", "Ben", "Cal", "Eli", "Fin"),
        names(card.batters));
    Map<String, String> how = new HashMap<>();
    for (MatchReport.BatterLine b : card.batters) how.put(b.name, b.dismissal);
    assertEquals("c Arjun b Kabir", how.get("Ravi"));
    assertEquals("b Kabir", how.get("Sam"));
    assertEquals("lbw b Kabir", how.get("Om"));
    assertEquals("c & b Kabir", how.get("Raj"));
    assertEquals("st Jay b Kabir", how.get("Ali"));
    assertEquals("hit wicket b Kabir", how.get("Ben"));
    assertEquals("run out (Arjun)", how.get("Cal"));
    assertEquals("retired", how.get("Eli"));
    assertEquals("not out", how.get("Dev"));
    assertEquals("not out", how.get("Fin"));
    assertEquals(1, card.batters.get(1).runs); // Dev ran one before Cal was run out
    assertEquals(Collections.singletonList("Gus"), card.yetToBat);

    assertEquals(7, card.falls.size());
    MatchReport.Fall first = card.falls.get(0), sixth = card.falls.get(5), seventh = card.falls.get(6);
    assertEquals(1, first.wicket);
    assertEquals("Ravi", first.batter);
    assertEquals("0.1", first.over);
    assertEquals("Ben", sixth.batter);
    assertEquals("0.6", sixth.over);
    assertEquals(7, seventh.wicket);
    assertEquals("Cal", seventh.batter);
    assertEquals(1, seventh.runs);
    assertEquals("1.1", seventh.over);

    assertEquals(2, card.bowlers.size());
    MatchReport.BowlerLine kabir = card.bowlers.get(0), zed = card.bowlers.get(1);
    assertEquals(6, kabir.wickets);
    assertEquals(1, kabir.maidens);
    assertEquals("1", kabir.overs());
    assertEquals(0, zed.wickets); // run outs are not the bowler's
    assertEquals(0, zed.maidens);
    assertEquals("0.2", zed.overs());

    List<MatchReport.Over> overs = MatchReport.overs(m, 0);
    assertEquals(Arrays.asList("W", "W", "W", "W", "W", "W"), labels(overs.get(0)));
    assertEquals(Arrays.asList("1W", "•", "WD"), labels(overs.get(1)));
    assertEquals(MatchReport.BallKind.WICKET, overs.get(1).balls.get(0).kind);
    assertEquals(MatchReport.BallKind.DOT, overs.get(1).balls.get(1).kind);
    assertEquals(MatchReport.BallKind.EXTRA, overs.get(1).balls.get(2).kind);
  }

  @Test
  public void extrasOversAndMaidensFollowWhoIsCharged() {
    Match m = new Match("Lions", "Tigers");
    m.rules.overs = 3;
    m.innings.add(new Innings("Lions", "Tigers", "Ravi", "Dev", 0));
    bowler(m, "Kabir");
    run(m, 0);
    run(m, 0);
    extra(m, Extra.BYE, 0, 2, 2);
    run(m, 0);
    extra(m, Extra.LEG_BYE, 0, 1, 1);
    run(m, 0); // byes and leg byes are not the bowler's: still a maiden
    bowler(m, "Zed");
    extra(m, Extra.WIDE, 0, 2, 0);   // 1 + 2 run wide
    extra(m, Extra.NO_BALL, 1, 0, 1); // 1 off the bat + 1 no-ball
    extra(m, Extra.PENALTY, 0, 5, 0);
    for (int i = 0; i < 6; i++) run(m, 0); // six dots, but the wide and no-ball spoil the maiden
    bowler(m, "Kabir");
    run(m, 4);
    run(m, 6);

    MatchReport.Scorecard card = MatchReport.scorecard(m, 0);
    assertEquals(12, card.extras);
    assertEquals("wd 3, nb 1, b 2, lb 1, pen 5", card.extrasDetail);
    assertEquals(23, card.runs);
    assertEquals("2.2", card.overs);
    assertEquals(23 * 6 / 14.0, card.runRate, 1e-9);
    MatchReport.BowlerLine kabir = card.bowlers.get(0), zed = card.bowlers.get(1);
    assertEquals("Kabir", kabir.name);
    assertEquals(1, kabir.maidens);
    assertEquals(10, kabir.runs);
    assertEquals("1.2", kabir.overs());
    assertEquals(10 * 6 / 8.0, kabir.economy(), 1e-9);
    assertEquals(0, zed.maidens);
    assertEquals(5, zed.runs); // wide and no-ball count against him, the penalty does not

    List<MatchReport.Over> overs = MatchReport.overs(m, 0);
    assertEquals(3, overs.size());
    MatchReport.Over one = overs.get(0), two = overs.get(1), three = overs.get(2);
    assertEquals(Arrays.asList("•", "•", "2B", "•", "1LB", "•"), labels(one));
    assertEquals(3, one.runs);
    assertEquals(Arrays.asList("2WD", "1NB", "5P", "•", "•", "•", "•", "•", "•"), labels(two));
    assertEquals(2, two.number);
    assertEquals(10, two.runs);
    assertEquals(13, two.scoreRuns);
    assertEquals(0, two.scoreWickets);
    assertEquals(Collections.singletonList("Zed"), two.bowlers);
    assertEquals(Arrays.asList("4", "6"), labels(three));
    assertEquals(MatchReport.BallKind.BOUNDARY, three.balls.get(0).kind);
    assertEquals(23, three.scoreRuns);
    assertEquals(Collections.singletonList("Kabir"), three.bowlers);

    Match plain = new Match("A", "B");
    plain.innings.add(new Innings("A", "B", "P", "Q", 0));
    bowler(plain, "K");
    extra(plain, Extra.WIDE, 0, 0, 0);
    extra(plain, Extra.NO_BALL, 0, 0, 0);
    assertEquals(Arrays.asList("WD", "NB"), labels(MatchReport.overs(plain, 0).get(0)));
  }

  @Test
  public void notesMarkMilestonesInterruptionsAndTheResult() {
    Match m = new Match("Lions", "Tigers");
    m.rules.overs = 4;
    m.innings.add(new Innings("Lions", "Tigers", "Ravi", "Dev", 0));
    clock = 100;
    bowler(m, "Kabir");
    extra(m, Extra.WIDE, 0, 0, 0);
    for (int i = 0; i < 6; i++) run(m, 6); // 37, Ravi 36
    bowler(m, "Zed");
    run(m, 6);
    run(m, 6);
    run(m, 6); // 55: crosses 50 in the ninth legal ball
    Event note = event(Kind.NOTE);
    note.note = " Drinks break ";
    m.add(note);
    Event wrong = event(Kind.NOTE);
    wrong.note = "Typo";
    m.add(wrong);
    m.undo(1);
    Event rain = event(Kind.INTERRUPTION);
    rain.revisedBalls = 12;
    m.add(rain);
    run(m, 4);
    run(m, 6);
    run(m, 1); // 66 at 2 overs: closed by the revised limit
    long firstInningsEnd = clock - 1;

    List<String> first = MatchReport.notes(m, 0, t -> "t" + t);
    assertEquals(Arrays.asList(
        "Match started at t100",
        "Lions: 55 runs in 1.3 overs, Extras 1",
        "Drinks break at 55/0 (1.3 Ov)",
        "Interruption at 55/0 (1.3 Ov): innings reduced to 2 overs",
        "Innings Break: Lions - 66/0 in 2 overs (Ravi 36, Dev 29)",
        "Innings ended at t" + firstInningsEnd), first);

    m.startChase("Sam", "Om");
    clock = 300;
    bowler(m, "Ravi");
    run(m, 4);
    m.add(event(Kind.END_INNINGS));

    assertEquals("Lions won by 62 runs", m.result());
    assertEquals(Arrays.asList(
        "Innings started at t300",
        "Innings closed early at 4/0 (0.1 Ov)",
        "End of innings: Tigers - 4/0 in 0.1 overs (Sam 4, Om 0)",
        "Match ended at t302. Lions won by 62 runs"), MatchReport.notes(m, 1, t -> "t" + t));
    assertEquals("Lions v Tigers · Individual match\nLions 66/0 (2.0 Ov)\nTigers 4/0 (0.1 Ov)\nLions won by 62 runs",
        MatchReport.shareText(m, "Individual match"));
  }

  @Test
  public void notesCountEachFiftyOnceEvenWhenTheTotalKeepsClimbing() {
    Match m = new Match("Lions", "Tigers");
    m.rules.overs = 5;
    m.innings.add(new Innings("Lions", "Tigers", "Ravi", "Dev", 0));
    String[] bowlers = {"Kabir", "Zed", "Kabir", "Zed"};
    for (String b : bowlers) {
      bowler(m, b);
      for (int i = 0; i < 6; i++) run(m, 6);
    }
    int milestones = 0;
    for (String n : MatchReport.notes(m, 0, t -> "")) if (n.startsWith("Lions: ")) milestones++;
    assertEquals(2, milestones); // 54 and 102 of 144: 150 never reached
    assertTrue(MatchReport.notes(m, 0, t -> "").contains("Lions: 102 runs in 2.5 overs, Extras 0"));
  }

  @Test
  public void bestPerformersBreakTiesOnBallsAndRuns() {
    Match m = new Match("Lions", "Tigers");
    m.rules.overs = 2;
    m.rules.bowlerOvers = 1;
    m.innings.add(new Innings("Lions", "Tigers", "Ravi", "Dev", 0));
    bowler(m, "Kabir");
    run(m, 6);
    run(m, 4);
    run(m, 0);
    out(m, Dismissal.CAUGHT, "Arjun", "Sam"); // Ravi 10 off 4
    extra(m, Extra.WIDE, 0, 0, 0);           // Kabir 1-11
    run(m, 0);
    run(m, 0);
    bowler(m, "Zed");
    run(m, 6);
    run(m, 4);                               // Dev 10 off 2
    out(m, Dismissal.BOWLED, "", "Om");      // Zed 1-10
    run(m, 0);
    run(m, 0);
    run(m, 0);

    assertEquals("Dev", MatchReport.bestBatter(m).name);
    assertEquals("Lions", MatchReport.bestBatter(m).team);
    assertEquals("10(3)  1 4s  1 6s", MatchReport.bestBatter(m).figures());
    MatchReport.BowlerLine bowler = MatchReport.bestBowler(m);
    assertEquals("Zed", bowler.name);
    assertEquals("Tigers", bowler.team);
    assertEquals("1-10 (1 Ov)", bowler.figures());

    m.startChase("Arjun", "Jay");
    bowler(m, "Ravi");
    run(m, 6);
    run(m, 6);
    out(m, Dismissal.BOWLED, "", "Kabir");   // Arjun 12 off 3
    out(m, Dismissal.LBW, "", "Zed");        // Ravi 2-12

    assertEquals("Arjun", MatchReport.bestBatter(m).name);
    assertEquals("Ravi", MatchReport.bestBowler(m).name);
    assertEquals("2-12 (0.4 Ov)", MatchReport.bestBowler(m).figures());

    // No saved squad: fall back to everyone the innings recorded for the team.
    assertEquals(Arrays.asList("Kabir", "Zed", "Arjun", "Jay"), MatchReport.squad(m, "Tigers"));
    m.rosterB.addAll(Arrays.asList("Zed", "Kabir"));
    assertEquals(Arrays.asList("Zed", "Kabir"), MatchReport.squad(m, "Tigers"));
  }

  @Test
  public void nobodyIsBestBeforeABallIsBowled() {
    Match m = new Match("Lions", "Tigers");
    m.innings.add(new Innings("Lions", "Tigers", "Ravi", "Dev", 0));
    bowler(m, "Kabir");
    assertNull(MatchReport.bestBatter(m));
    assertNull(MatchReport.bestBowler(m));
    assertTrue(MatchReport.notes(m, 0, t -> "").get(0).startsWith("Match started at"));
    assertEquals(Arrays.asList("Ravi", "Dev"), names(MatchReport.scorecard(m, 0).batters));
  }
}
