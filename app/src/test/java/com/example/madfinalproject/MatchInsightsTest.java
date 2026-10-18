package com.example.madfinalproject;

import com.example.madfinalproject.domain.Cricket.*;
import com.example.madfinalproject.domain.MatchInsights;
import com.example.madfinalproject.domain.PlayerStats;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class MatchInsightsTest {
  private Match match() {
    Match m = new Match("Green", "Gold");
    m.rules.overs = 20; m.rules.bowlerOvers = 20;
    m.innings.add(new Innings("Green", "Gold", "A", "B", 0));
    Event bowler = new Event(); bowler.kind = Kind.BOWLER; bowler.player = "C"; m.add(bowler);
    return m;
  }
  @Test public void noBallCountsForBatterButNotOverAndCorrectionsRecompute() {
    Match m = match(); Event ball = new Event(); ball.extra = Extra.NO_BALL; ball.bat = 4; ball.boundary = true; m.add(ball);
    assertEquals(1, m.state().bat("A").balls); assertEquals(0, m.state().legal);
    assertEquals(5, m.state().bowl("C").runs);
    PlayerStats.Player p = PlayerStats.find("A", "Green", Collections.singletonList(m));
    assertEquals(400, p.strikeRate(), .001); assertTrue(Double.isNaN(p.average()));
    MatchInsights.FaceOff row = MatchInsights.faceOffs(m.progressAt(0)).get(0);
    assertEquals(4, row.runs); assertEquals(1, row.balls); assertEquals(5, row.conceded);
    assertTrue(Double.isNaN(row.economy(6)));
    m.undo(1); assertEquals(0, m.state().bat("A").balls); assertEquals(0, m.progressAt(0).size());
  }
  @Test public void byesAreTeamRunsButNotBowlerRunsAndWideIsNotFaced() {
    Match m = match();Event bye = new Event();bye.extra = Extra.BYE;bye.extras = 2;m.add(bye);
    Event wide = new Event();wide.extra = Extra.WIDE;m.add(wide);
    assertEquals(3, m.state().runs);assertEquals(1, m.state().legal);assertEquals(1, m.state().bat("A").balls);
    assertEquals(1, m.state().bowl("C").runs);
    MatchInsights.FaceOff row = MatchInsights.faceOffs(m.progressAt(0)).get(0);
    assertEquals(0, row.runs);assertEquals(1, row.balls);assertEquals(1, row.dots);assertEquals(1, row.conceded);
    assertEquals(6, row.economy(6), .001);
    MatchInsights.Phase opening = MatchInsights.phases(m.progressAt(0), 6, 20, 6).get(0);
    assertEquals(3, opening.runs);assertEquals(18, opening.rate(6), .001);
  }
  @Test public void bowlingAverageAndStrikeRateUseOnlyBowlerWickets() {
    Match m = match();Event ball=new Event();ball.bat=4;ball.boundary=true;m.add(ball);
    Event wicket=new Event();wicket.dismissal=Dismissal.BOWLED;wicket.incoming="D";m.add(wicket);
    PlayerStats.Player bowler=PlayerStats.find("C","Gold",Collections.singletonList(m));
    assertEquals(1,bowler.wickets);assertEquals(4,bowler.bowlingAverage(),.001);assertEquals(2,bowler.bowlingStrikeRate(),.001);
    assertEquals(12,bowler.economy(),.001);assertEquals(50,bowler.bowlingDotPercent(),.001);
  }
}
