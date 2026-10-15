package com.example.madfinalproject;
import org.junit.Test;
import static org.junit.Assert.*;
import com.example.madfinalproject.domain.Cricket.*;

public class CricketEngineTest {
    private Match match(){Match m=new Match("A","B");m.rules.overs=2;m.rules.bowlerOvers=2;m.innings.add(new Innings("A","B","Rohan","Dev",0));bowler(m,"Aman");return m;}
    private void bowler(Match m,String name){Event b=new Event();b.kind=Kind.BOWLER;b.player=name;m.add(b);}
    private Event run(Match m,int runs){Event e=new Event();e.bat=runs;e.completed=runs%2;e.boundary=runs==4||runs==6;m.add(e);return e;}
    @Test public void oddRunUndoRestoresEndsAndStats(){Match m=match();run(m,1);assertEquals("Dev",m.state().striker);m.undo(1);assertEquals("Rohan",m.state().striker);assertEquals(0,m.state().runs);assertEquals(0,m.state().bat("Rohan").balls);}
    @Test public void undoOverBoundaryRemovesNewBowler(){Match m=match();for(int i=0;i<6;i++)run(m,0);bowler(m,"Kabir");m.undo(1);assertEquals(5,m.state().legal);assertEquals("Aman",m.state().bowler);assertEquals("Rohan",m.state().striker);}
    @Test public void noBallAndFreeHitUndo(){Match m=match();Event nb=new Event();nb.extra=Extra.NO_BALL;m.add(nb);assertTrue(m.state().freeHit);run(m,0);assertFalse(m.state().freeHit);m.undo(1);assertTrue(m.state().freeHit);m.undo(1);assertFalse(m.state().freeHit);assertEquals(0,m.state().legal);}
    @Test public void wicketUndoRestoresDismissedAndIncoming(){Match m=match();Event e=new Event();e.dismissal=Dismissal.CAUGHT;e.incoming="Sam";e.fielder="Fielder";m.add(e);assertEquals("Sam",m.state().striker);assertEquals(1,m.state().bowl("Aman").wickets);m.undo(1);assertEquals("Rohan",m.state().striker);assertFalse(m.state().batting.containsKey("Sam"));assertEquals(0,m.state().wickets);assertTrue(m.state().falls.isEmpty());assertTrue(m.state().catches.isEmpty());}
    @Test public void runOutCrossingUndo(){Match m=match();Event e=new Event();e.dismissal=Dismissal.RUN_OUT;e.bat=2;e.completed=2;e.crossed=true;e.nonStrikerOut=true;e.incoming="Sam";m.add(e);assertEquals("Sam",m.state().striker);m.undo(1);assertEquals("Rohan",m.state().striker);assertEquals("Dev",m.state().nonStriker);assertEquals(0,m.state().runs);}
    @Test public void historyEditReattributesLaterStats(){Match m=match();Event first=run(m,1);run(m,4);first.bat=2;first.completed=2;assertEquals(6,m.state().runs);assertEquals(6,m.state().bat("Rohan").runs);assertEquals(0,m.state().bat("Dev").runs);assertEquals(3,m.current().events.size());}
    @Test public void winningRunUndoReopensMatch(){Match m=match();m.current().target=4;run(m,4);assertTrue(m.state().closed);m.undo(1);assertFalse(m.state().closed);}
    @Test public void correctionRetainsButExcludesAfterWinningBall(){Match m=match();m.current().target=10;Event first=run(m,1);run(m,1);first.bat=10;assertEquals(10,m.state().runs);assertEquals(1,m.state().legal);assertEquals(3,m.current().events.size());first.bat=1;assertEquals(2,m.state().runs);assertEquals(2,m.state().legal);}
    @Test public void soloBatterStaysAtCrease(){Match m=match();m.rules.players=2;m.rules.lastMan=true;Event out=new Event();out.dismissal=Dismissal.BOWLED;m.add(out);assertEquals("Dev",m.state().striker);run(m,1);assertEquals("Dev",m.state().striker);run(m,2);assertEquals(3,m.state().runs);}
    @Test public void penaltyDoesNotUseDelivery(){Match m=match();Event p=new Event();p.extra=Extra.PENALTY;p.extras=5;m.add(p);assertEquals(0,m.state().legal);assertEquals(0,m.state().bat("Rohan").balls);}
    @Test public void finalOverLegalExtraCustomRules(){Match m=match();m.rules.overs=1;m.rules.ballsPerOver=4;m.rules.finalOverExtrasLegal=true;Event e=new Event();e.extra=Extra.WIDE;m.add(e);assertEquals(1,m.state().legal);assertEquals(1,m.state().runs);}
    @Test public void retiredHurtUndoRestoresOriginalPlayer(){Match m=match();Event e=new Event();e.kind=Kind.RETIRE;e.player="Rohan";e.incoming="Sam";m.add(e);m.undo(1);assertEquals("Rohan",m.state().striker);assertFalse(m.state().bat("Rohan").retired);}
    @Test public void dismissedPlayerCannotBeSelectedAgain(){Match m=match();Event e=new Event();e.dismissal=Dismissal.BOWLED;e.incoming="Sam";m.add(e);Event select=new Event();select.kind=Kind.BATTERS;select.player="Rohan";select.partner="Dev";try{m.add(select);fail("dismissed player accepted");}catch(IllegalArgumentException expected){assertEquals(1,m.state().wickets);}}
    @Test public void revisedBallsPersistThroughReplay(){Match m=match();Event e=new Event();e.kind=Kind.INTERRUPTION;e.revisedBalls=8;e.revisedTarget=0;m.add(e);run(m,0);assertEquals(8,m.state().maxBalls);}
}
