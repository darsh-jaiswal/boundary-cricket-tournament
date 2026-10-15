package com.example.madfinalproject.domain;

import java.io.Serializable;
import java.util.*;

/** Pure Java event journal. Derived scorecards are rebuilt after every correction. */
public final class Cricket {
    private Cricket() {}
    public static String id() { return UUID.randomUUID().toString(); }
    public enum Kind { BALL, BOWLER, BATTERS, RETIRE, RESUME, INTERRUPTION }
    public enum Extra { NONE, WIDE, NO_BALL, BYE, LEG_BYE, PENALTY }
    public enum Dismissal { NONE, BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED, HIT_WICKET, OBSTRUCTING }
    public static final class Rules implements Serializable {
        public int ballsPerOver=6, overs=10, players=11, wide=1, noBall=1, bowlerOvers=2, powerplay=2;
        public boolean lastMan, finalOverExtrasLegal;
        public void validate() {
            if (!Arrays.asList(4,5,6,8).contains(ballsPerOver)||overs<1||overs>100||players<2||players>15||wide<0||noBall<0||bowlerOvers<1||powerplay<0||powerplay>overs)
                throw new IllegalArgumentException("Check balls per over, overs, squad size and bowling limits.");
        }
    }
    public static final class Team implements Serializable {
        public String id=id(), name; public ArrayList<String> players=new ArrayList<>();
        public Team(String name){this.name=name;}
        public String toString(){return name;}
    }
    public static final class Event implements Serializable {
        public String id=id(), player="", partner="", bowler="", incoming="", fielder="", note="";
        public Kind kind=Kind.BALL; public Extra extra=Extra.NONE; public Dismissal dismissal=Dismissal.NONE;
        public int bat, extras, completed, overthrow, zone=-1, revisedBalls, revisedTarget;
        public boolean nonStrikerOut, crossed, boundary, reversed;
        public long created=System.currentTimeMillis();
        public String label(){ if(reversed)return "Decision Reversed"; if(kind!=Kind.BALL)return kind+" "+player;
            return dismissal!=Dismissal.NONE?"W · "+dismissal.name().replace('_',' '):(extra!=Extra.NONE?extra.name().replace('_',' ')+" "+(bat+extras):String.valueOf(bat+overthrow)); }
    }
    public static final class Batting implements Serializable { public int runs,balls,fours,sixes;public boolean out,retired; public int[] zones=new int[8]; public String dismissal=""; }
    public static final class Bowling implements Serializable { public int runs,balls,wickets,wides,noBalls; }
    public static final class State implements Serializable {
        public int runs,wickets,legal,target,maxBalls;public String striker,nonStriker,bowler="";public boolean freeHit,closed,needsBatter;
        public LinkedHashMap<String,Batting> batting=new LinkedHashMap<>();public LinkedHashMap<String,Bowling> bowling=new LinkedHashMap<>();
        public ArrayList<String> falls=new ArrayList<>(),commentary=new ArrayList<>();public Map<String,Integer> catches=new LinkedHashMap<>();
        public String overs(Rules r){return legal/r.ballsPerOver+"."+legal%r.ballsPerOver;}
        public double rate(Rules r){return legal==0?0:(double)runs*r.ballsPerOver/legal;}
        public Batting bat(String p){return batting.computeIfAbsent(p,k->new Batting());}
        public Bowling bowl(String p){return bowling.computeIfAbsent(p,k->new Bowling());}
        void swap(){String p=striker;striker=nonStriker;nonStriker=p;}
    }
    public static final class Innings implements Serializable {
        public String battingTeam,bowlingTeam,opener,partner;public int target;
        public ArrayList<Event> events=new ArrayList<>();
        public Innings(String a,String b,String p,String q,int target){battingTeam=a;bowlingTeam=b;opener=p;partner=q;this.target=target;}
        public State state(Rules r){
            State s=new State();s.striker=opener;s.nonStriker=partner;s.target=target;s.maxBalls=r.overs*r.ballsPerOver;s.bat(opener);s.bat(partner);
            for(Event e:events){
                if(e.reversed){s.commentary.add("Decision Reversed: "+e.note);continue;}
                if(s.closed&&e.kind==Kind.BALL){s.commentary.add("Retained after innings completion: "+e.label());continue;}
                switch(e.kind){
                    case BOWLER:s.bowler=e.player;s.bowl(e.player);break;
                    case BATTERS:if(!s.bat(e.player).out&&!s.bat(e.partner).out){s.striker=e.player;s.nonStriker=e.partner;s.bat(e.player);s.bat(e.partner);}break;
                    case RETIRE:s.bat(e.player).retired=true;if(e.player.equals(s.striker))s.striker=e.incoming;else if(e.player.equals(s.nonStriker))s.nonStriker=e.incoming;s.bat(e.incoming);break;
                    case RESUME:s.bat(e.player).retired=false;if(e.nonStrikerOut)s.nonStriker=e.player;else s.striker=e.player;break;
                    case INTERRUPTION:s.maxBalls=e.revisedBalls;s.target=e.revisedTarget;break;
                    case BALL: apply(s,r,e);break;
                }
                s.closed=(s.target>0&&s.runs>=s.target)||s.legal>=s.maxBalls||s.wickets>=r.players-(r.lastMan?0:1);
            }
            s.needsBatter=s.striker.isEmpty()||s.nonStriker.isEmpty();return s;
        }
        private void apply(State s,Rules r,Event e){
            String batter=s.striker,bowler=s.bowler;Batting bt=s.bat(batter);Bowling bw=s.bowl(bowler);
            boolean wide=e.extra==Extra.WIDE,nb=e.extra==Extra.NO_BALL,pen=e.extra==Extra.PENALTY;
            boolean legal=(!wide&&!nb&&!pen)||((wide||nb)&&r.finalOverExtrasLegal&&s.legal>=s.maxBalls-r.ballsPerOver);
            int automatic=wide?r.wide:nb?r.noBall:0;
            int total=e.bat+e.extras+e.overthrow+automatic;
            s.runs+=total;
            if(!wide&&!pen){bt.balls++;}
            bt.runs+=e.bat+(e.extra==Extra.NONE||nb?e.overthrow:0);
            if(e.boundary&&e.bat==4)bt.fours++;if(e.boundary&&e.bat==6)bt.sixes++;
            if(e.zone>=0&&e.zone<8)bt.zones[e.zone]+=e.bat+e.overthrow;
            if(legal){s.legal++;bw.balls++;}
            if(e.extra!=Extra.BYE&&e.extra!=Extra.LEG_BYE&&!pen)bw.runs+=total;
            if(wide)bw.wides+=automatic+e.extras;if(nb)bw.noBalls+=automatic;
            boolean dismissalAllowed=e.dismissal!=Dismissal.NONE&&(!(s.freeHit||nb)||e.dismissal==Dismissal.RUN_OUT||e.dismissal==Dismissal.OBSTRUCTING)&&(!wide||e.dismissal==Dismissal.RUN_OUT||e.dismissal==Dismissal.STUMPED||e.dismissal==Dismissal.HIT_WICKET||e.dismissal==Dismissal.OBSTRUCTING);
            String dismissed=e.nonStrikerOut?s.nonStriker:s.striker;
            if((e.completed%2==1)^e.crossed)s.swap();
            if(r.lastMan&&s.striker.isEmpty())s.swap();
            if(dismissalAllowed){
                s.wickets++;Batting out=s.bat(dismissed);out.out=true;out.dismissal=e.dismissal.name();
                if(e.dismissal!=Dismissal.RUN_OUT&&e.dismissal!=Dismissal.OBSTRUCTING)bw.wickets++;
                if(!e.fielder.isEmpty())s.catches.put(e.fielder,s.catches.getOrDefault(e.fielder,0)+1);
                s.falls.add(s.runs+"/"+s.wickets+" "+dismissed+" ("+s.overs(r)+")");
                String next=e.incoming;
                if(r.lastMan&&s.wickets==r.players-1)next="";
                if(dismissed.equals(s.striker))s.striker=next;else if(dismissed.equals(s.nonStriker))s.nonStriker=next;
                if(!next.isEmpty())s.bat(next);
                if(r.lastMan&&s.striker.isEmpty())s.swap();
            }
            if(legal&&s.legal%r.ballsPerOver==0){s.swap();if(r.lastMan&&s.striker.isEmpty())s.swap();s.bowler="";}
            if(nb)s.freeHit=true;else if(!wide&&!pen)s.freeHit=false;
            s.commentary.add(s.overs(r)+" · "+(dismissalAllowed?"Wicket! "+dismissed+" departs, "+e.dismissal.name().replace('_',' ').toLowerCase(Locale.ROOT)+".":e.boundary&&e.bat==6?batter+" sends it all the way for six!":e.boundary&&e.bat==4?batter+" finds the gap for four!":wide?"Wide called. "+total+" added.":nb?"No-ball! Free hit follows. "+total+" added.":batter+": "+total+" run"+(total==1?"":"s")+". ")+" "+s.runs+"/"+s.wickets);
        }
    }
    public static final class Match implements Serializable {
        public String id=id(),name="",teamA,teamB,scorer="",umpire="",venue="",tournament="";public Rules rules=new Rules();
        public ArrayList<Innings> innings=new ArrayList<>(); public long updated=System.currentTimeMillis();public int revision;
        public Match(String a,String b){teamA=a;teamB=b;name=a+" v "+b;}
        public Innings current(){return innings.get(innings.size()-1);}
        public State state(){return current().state(rules);}
        public String result(){if(innings.size()<2||!state().closed)return "Live";State a=innings.get(0).state(rules),b=state();if(b.runs>=b.target)return teamB+" won by "+Math.max(0,rules.players-1-b.wickets)+" wickets";if(b.runs==b.target-1)return "Match tied";return teamA+" won by "+Math.max(0,b.target-1-b.runs)+" runs";}
        public void add(Event e){State s=state();if(e.kind==Kind.BALL){if(s.closed)throw new IllegalArgumentException("Innings is complete. Undo or start the chase.");if(s.bowler.isEmpty())throw new IllegalArgumentException("Select a bowler for this over.");if(s.striker.isEmpty()||s.nonStriker.isEmpty()&&!rules.lastMan)throw new IllegalArgumentException("Select both batters.");if(e.bat<0||e.extras<0||e.completed<0||e.overthrow<0)throw new IllegalArgumentException("Runs cannot be negative.");}if(e.kind==Kind.BATTERS&&(s.bat(e.player).out||s.bat(e.partner).out))throw new IllegalArgumentException("Dismissed batters cannot return to the crease.");current().events.add(e);revision++;updated=System.currentTimeMillis();}
        public void undo(int count){if(count<1)throw new IllegalArgumentException("Undo count must be positive.");List<Event> es=current().events;for(int i=es.size()-1;i>=0&&count>0;i--){Event e=es.get(i);if(!e.reversed){String label=e.label();e.reversed=true;e.note="Undone "+label;if(e.kind==Kind.BALL||e.kind==Kind.RETIRE||e.kind==Kind.RESUME||e.kind==Kind.INTERRUPTION)count--;}}revision++;}
        public void startChase(String p,String q){if(innings.size()!=1||!state().closed)throw new IllegalArgumentException("Finish the first innings before starting the chase.");innings.add(new Innings(teamB,teamA,p,q,state().runs+1));revision++;}
    }
    public enum Format { LEAGUE, KNOCKOUT, GROUPS }
    public static final class Fixture implements Serializable {public String id=id(),a,b,matchId="",round;public Fixture(String a,String b,String r){this.a=a;this.b=b;round=r;}}
    public static final class Tournament implements Serializable {
        public String id=id(),name;public Format format;public ArrayList<String> teams=new ArrayList<>();public ArrayList<Fixture> fixtures=new ArrayList<>();
        public Tournament(String n,Format f){name=n;format=f;}
        public void generate(){if(teams.size()<2)throw new IllegalArgumentException("Register at least two teams.");if(!fixtures.isEmpty())throw new IllegalArgumentException("Fixtures have already been generated.");
            if(format==Format.KNOCKOUT){int n=1;while(n<teams.size())n*=2;ArrayList<String> list=new ArrayList<>(teams);while(list.size()<n)list.add("BYE");for(int i=0;i<n;i+=2)fixtures.add(new Fixture(list.get(i),list.get(i+1),"Round 1"));}
            else for(int i=0;i<teams.size();i++)for(int j=i+1;j<teams.size();j++)if(format!=Format.GROUPS||i%2==j%2)fixtures.add(new Fixture(teams.get(i),teams.get(j),format==Format.GROUPS?"Group "+(i%2==0?"A":"B"):"League"));
        }
    }
    public static final class Row {public String team;public int played,won,lost,tied,points,runsFor,ballsFor,runsAgainst,ballsAgainst;public double nrr(int bpo){return (ballsFor==0?0:runsFor*(double)bpo/ballsFor)-(ballsAgainst==0?0:runsAgainst*(double)bpo/ballsAgainst);}}
    public static List<Row> standings(Tournament t,List<Match> matches){Map<String,Row> rows=new LinkedHashMap<>();for(String team:t.teams){Row r=new Row();r.team=team;rows.put(team,r);}for(Match m:matches){if(!m.tournament.equals(t.id)||m.innings.size()!=2||!m.state().closed)continue;Row a=rows.get(m.teamA),b=rows.get(m.teamB);if(a==null||b==null)continue;State x=m.innings.get(0).state(m.rules),y=m.state();a.played++;b.played++;int xb=x.wickets>=m.rules.players-1?x.maxBalls:x.legal,yb=y.wickets>=m.rules.players-1?y.maxBalls:y.legal;a.runsFor+=x.runs;a.ballsFor+=xb;a.runsAgainst+=y.runs;a.ballsAgainst+=yb;b.runsFor+=y.runs;b.ballsFor+=yb;b.runsAgainst+=x.runs;b.ballsAgainst+=xb;if(y.runs>=y.target){b.won++;b.points+=2;a.lost++;}else if(y.runs==y.target-1){a.tied++;b.tied++;a.points++;b.points++;}else{a.won++;a.points+=2;b.lost++;}}List<Row> result=new ArrayList<>(rows.values());result.sort((a,b)->a.points!=b.points?b.points-a.points:Double.compare(b.nrr(6),a.nrr(6)));return result;}
    public static int probability(State s,Rules r){if(s.target>0&&s.runs>=s.target)return 100;if(s.closed)return 0;int left=Math.max(0,s.maxBalls-s.legal),need=Math.max(0,s.target-s.runs);double wickets=(r.players-1.0-s.wickets)/Math.max(1,r.players-1);double expected=left*(.8+1.1*wickets);return (int)Math.max(1,Math.min(99,100/(1+Math.exp((need-expected)/Math.max(5,Math.sqrt(left)*2)))));}
    public static int revisedTarget(State s,int newBalls){if(newBalls<=s.legal||newBalls>=s.maxBalls)throw new IllegalArgumentException("Revised total balls must be above balls bowled and below the current limit.");if(s.target==0)return 0;double available=resource(s.maxBalls-s.legal,s.wickets),remaining=resource(newBalls-s.legal,s.wickets);return s.runs+Math.max(1,(int)Math.ceil((s.target-s.runs)*remaining/Math.max(.001,available)));}
    private static double resource(int balls,int wickets){double capacity=Math.max(.1,(10-Math.min(9,wickets))/10.0);return capacity*(1-Math.exp(-balls/(35.0*capacity)));}
}
