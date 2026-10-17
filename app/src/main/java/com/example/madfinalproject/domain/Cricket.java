package com.example.madfinalproject.domain;

import java.io.Serializable;
import java.util.*;

/** Pure Java event journal. Derived scorecards are rebuilt after every correction. */
public final class Cricket {
  private Cricket() {}

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public enum Kind {
    BALL,
    BOWLER,
    BATTERS,
    RETIRE,
    RESUME,
    INTERRUPTION,
    END_INNINGS,
    NOTE
  }

  public enum Extra {
    NONE,
    WIDE,
    NO_BALL,
    BYE,
    LEG_BYE,
    PENALTY
  }

  public enum Dismissal {
    NONE,
    BOWLED,
    CAUGHT,
    LBW,
    RUN_OUT,
    STUMPED,
    HIT_WICKET,
    OBSTRUCTING
  }

  public static final class Rules implements Serializable {
    private static final long serialVersionUID = 1L;
    public int ballsPerOver = 6,
        overs = 10,
        players = 11,
        wide = 1,
        noBall = 1,
        bowlerOvers = 2,
        powerplay = 2;
    public boolean lastMan, finalOverExtrasLegal;

    public void validate() {
      if (!Arrays.asList(4, 5, 6, 8).contains(ballsPerOver)
          || overs < 1
          || overs > 100
          || players < 2
          || players > 15
          || wide < 0
          || noBall < 0
          || bowlerOvers < 1
          || powerplay < 0
          || powerplay > overs)
        throw new IllegalArgumentException(
            "Check balls per over, overs, squad size and bowling limits.");
    }
  }

  public static final class Team implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id = id(), name;
    public ArrayList<String> players = new ArrayList<>();

    public Team(String name) {
      this.name = name;
    }

    public String toString() {
      return name;
    }
  }

  public static final class Scorebook implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id = id(), name, team;
    public ArrayList<ImportedPlayer> players = new ArrayList<>();

    public Scorebook(String name, String team) {
      this.name = name;
      this.team = team;
    }
  }

  public static final class ImportedPlayer implements Serializable {
    private static final long serialVersionUID = 1L;
    public String name;
    public int runs, balls, wickets, conceded, bowled;

    public ImportedPlayer(String line) {
      String[] fields = line.split(",");
      if (fields.length != 6)
        throw new IllegalArgumentException(
            "Use player,runs,balls,wickets,conceded,bowled on each line.");
      name = fields[0].trim();
      if (name.isEmpty()) throw new IllegalArgumentException("Player name is required.");
      runs = Integer.parseInt(fields[1].trim());
      balls = Integer.parseInt(fields[2].trim());
      wickets = Integer.parseInt(fields[3].trim());
      conceded = Integer.parseInt(fields[4].trim());
      bowled = Integer.parseInt(fields[5].trim());
      if (runs < 0 || balls < 0 || wickets < 0 || conceded < 0 || bowled < 0)
        throw new IllegalArgumentException("Statistics cannot be negative.");
    }
  }

  public static final class Event implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id = id(),
        player = "",
        partner = "",
        bowler = "",
        incoming = "",
        fielder = "",
        note = "";
    public Kind kind = Kind.BALL;
    public Extra extra = Extra.NONE;
    public Dismissal dismissal = Dismissal.NONE;
    public int bat, extras, completed, overthrow, zone = -1, revisedBalls, revisedTarget;
    public boolean nonStrikerOut, crossed, boundary, reversed;
    public long created = System.currentTimeMillis();

    public String label() {
      if (reversed) return "Decision Reversed";
      if (kind == Kind.END_INNINGS) return "Innings ended early";
      if (kind == Kind.NOTE) return "Scorer note · " + note;
      if (kind != Kind.BALL) return kind + " " + player;
      return dismissal != Dismissal.NONE
          ? "W · " + dismissal.name().replace('_', ' ')
          : (extra != Extra.NONE
              ? extra.name().replace('_', ' ') + " " + (bat + extras)
              : String.valueOf(bat + overthrow));
    }
  }

  public static final class Batting implements Serializable {
    private static final long serialVersionUID = 1L;
    public int runs, balls, fours, sixes;
    public boolean out, retired;
    public int[] zones = new int[8];
    public String dismissal = "";
  }

  public static final class Bowling implements Serializable {
    private static final long serialVersionUID = 1L;
    public int runs, balls, wickets, wides, noBalls;
  }

  public static final class State implements Serializable {
    private static final long serialVersionUID = 1L;
    public int runs, wickets, legal, target, maxBalls;
    public String striker, nonStriker, bowler = "";
    public boolean freeHit, closed, needsBatter;
    public LinkedHashMap<String, Batting> batting = new LinkedHashMap<>();
    public LinkedHashMap<String, Bowling> bowling = new LinkedHashMap<>();
    public EnumMap<Extra, Integer> extras = new EnumMap<>(Extra.class);
    public ArrayList<String> falls = new ArrayList<>(), commentary = new ArrayList<>();
    public Map<String, Integer> catches = new LinkedHashMap<>();

    public String overs(Rules r) {
      return legal / r.ballsPerOver + "." + legal % r.ballsPerOver;
    }

    public double rate(Rules r) {
      return legal == 0 ? 0 : (double) runs * r.ballsPerOver / legal;
    }

    public Batting bat(String p) {
      return batting.computeIfAbsent(p, k -> new Batting());
    }

    public Bowling bowl(String p) {
      return bowling.computeIfAbsent(p, k -> new Bowling());
    }

    void swap() {
      String p = striker;
      striker = nonStriker;
      nonStriker = p;
    }
  }

  public static final class Progress {
    public final int legal, runs, wickets, legalBefore, runsBefore, wicketsBefore;
    public final String strikerBefore, nonStrikerBefore, bowler, fielder, label;
    public final Dismissal dismissal;
    public final Extra extra;

    private Progress(State state, int legalBefore, int runsBefore, int wicketsBefore,
        String strikerBefore, String nonStrikerBefore, String bowler, Event event) {
      legal = state.legal;
      runs = state.runs;
      wickets = state.wickets;
      this.legalBefore = legalBefore;
      this.runsBefore = runsBefore;
      this.wicketsBefore = wicketsBefore;
      this.strikerBefore = strikerBefore;
      this.nonStrikerBefore = nonStrikerBefore;
      this.bowler = bowler;
      fielder = event.fielder;
      label = event.label();
      dismissal = event.dismissal;
      extra = event.extra;
    }
  }

  public static final class Innings implements Serializable {
    private static final long serialVersionUID = 1L;
    public String battingTeam, bowlingTeam, opener, partner;
    public int target;
    public ArrayList<Event> events = new ArrayList<>();

    public Innings(String a, String b, String p, String q, int target) {
      battingTeam = a;
      bowlingTeam = b;
      opener = p;
      partner = q;
      this.target = target;
    }

    public State state(Rules r) {
      return state(r, target);
    }

    public State state(Rules r, int initialTarget) {
      return replay(r, initialTarget, null);
    }

    public List<Progress> progress(Rules r, int initialTarget) {
      List<Progress> points = new ArrayList<>();
      replay(r, initialTarget, points::add);
      return points;
    }

    private State replay(Rules r, int initialTarget,
        java.util.function.Consumer<Progress> progress) {
      State s = new State();
      s.striker = opener;
      s.nonStriker = partner;
      s.target = initialTarget;
      s.maxBalls = r.overs * r.ballsPerOver;
      s.bat(opener);
      s.bat(partner);
      boolean endedEarly = false;
      for (Event e : events) {
        if (e.reversed) {
          s.commentary.add("Decision Reversed: " + e.note);
          continue;
        }
        if (s.closed && e.kind == Kind.BALL) {
          s.commentary.add("Retained after innings completion: " + e.label());
          continue;
        }
        int legalBefore = s.legal, runsBefore = s.runs, wicketsBefore = s.wickets;
        String strikerBefore = s.striker, nonStrikerBefore = s.nonStriker;
        String effectiveBowler = e.bowler.isEmpty() ? s.bowler : e.bowler;
        switch (e.kind) {
          case BOWLER:
            s.bowler = e.player;
            s.bowl(e.player);
            break;
          case BATTERS:
            if (!s.bat(e.player).out && !s.bat(e.partner).out
                && !s.bat(e.player).retired && !s.bat(e.partner).retired) {
              s.striker = e.player;
              s.nonStriker = e.partner;
              s.bat(e.player);
              s.bat(e.partner);
            }
            break;
          case RETIRE:
            if (!e.player.equals(s.striker) && !e.player.equals(s.nonStriker)) {
              s.commentary.add("Retained invalid retirement: " + e.player);
              break;
            }
            Batting replacement = s.batting.get(e.incoming);
            if (!e.incoming.isEmpty() && (e.incoming.equals(s.striker)
                || e.incoming.equals(s.nonStriker)
                || replacement != null && (replacement.out || replacement.retired))) {
              s.commentary.add("Retained invalid replacement: " + e.incoming);
              break;
            }
            s.bat(e.player).retired = true;
            if (e.player.equals(s.striker)) s.striker = e.incoming;
            else if (e.player.equals(s.nonStriker)) s.nonStriker = e.incoming;
            if (!e.incoming.isEmpty()) s.bat(e.incoming);
            break;
          case RESUME:
            Batting returning = s.batting.get(e.player);
            String vacantEnd = e.nonStrikerOut ? s.nonStriker : s.striker;
            if (returning == null || !returning.retired || returning.out || !vacantEnd.isEmpty()) {
              s.commentary.add("Retained invalid return: " + e.player);
              break;
            }
            s.bat(e.player).retired = false;
            if (e.nonStrikerOut) s.nonStriker = e.player;
            else s.striker = e.player;
            break;
          case INTERRUPTION:
            s.maxBalls = e.revisedBalls;
            s.target = e.revisedTarget;
            break;
          case END_INNINGS:
            endedEarly = true;
            s.commentary.add(s.overs(r) + " · Innings ended early by the scorer. "
                + s.runs + "/" + s.wickets);
            break;
          case NOTE:
            s.commentary.add(s.overs(r) + " · Scorer note: " + e.note);
            break;
          case BALL:
            apply(s, r, e);
            break;
        }
        s.closed =
            endedEarly || (s.target > 0 && s.runs >= s.target)
                || s.legal >= s.maxBalls
                || s.wickets >= r.players - (r.lastMan ? 0 : 1);
        if (progress != null && e.kind == Kind.BALL)
          progress.accept(new Progress(s, legalBefore, runsBefore, wicketsBefore,
              strikerBefore, nonStrikerBefore, effectiveBowler, e));
      }
      s.needsBatter = s.striker.isEmpty() || s.nonStriker.isEmpty();
      return s;
    }

    private void apply(State s, Rules r, Event e) {
      if (!e.bowler.isEmpty()) s.bowler = e.bowler;
      String batter = s.striker, bowler = s.bowler;
      Batting bt = s.bat(batter);
      Bowling bw = s.bowl(bowler);
      boolean wide = e.extra == Extra.WIDE,
          nb = e.extra == Extra.NO_BALL,
          pen = e.extra == Extra.PENALTY;
      boolean legal =
          (!wide && !nb && !pen)
              || ((wide || nb) && r.finalOverExtrasLegal && s.legal >= s.maxBalls - r.ballsPerOver);
      int automatic = wide ? r.wide : nb ? r.noBall : 0;
      int total = e.bat + e.extras + e.overthrow + automatic;
      s.runs += total;
      if (e.extra != Extra.NONE)
        s.extras.put(
            e.extra,
            s.extras.getOrDefault(e.extra, 0)
                + e.extras
                + automatic
                + (e.extra == Extra.NO_BALL ? 0 : e.overthrow));
      if (!wide && !pen && !nb) {
        bt.balls++;
      }
      bt.runs += e.bat + (e.extra == Extra.NONE || nb ? e.overthrow : 0);
      if (e.boundary && e.bat == 4) bt.fours++;
      if (e.boundary && e.bat == 6) bt.sixes++;
      if (e.zone >= 0 && e.zone < 8) bt.zones[e.zone] += e.bat + e.overthrow;
      if (legal) {
        s.legal++;
        bw.balls++;
      }
      if (e.extra != Extra.BYE && e.extra != Extra.LEG_BYE && !pen) bw.runs += total;
      if (wide) bw.wides += automatic + e.extras;
      if (nb) bw.noBalls += automatic;
      boolean dismissalAllowed =
          e.dismissal != Dismissal.NONE
              && (!(s.freeHit || nb)
                  || e.dismissal == Dismissal.RUN_OUT
                  || e.dismissal == Dismissal.OBSTRUCTING)
              && (!wide
                  || e.dismissal == Dismissal.RUN_OUT
                  || e.dismissal == Dismissal.STUMPED
                  || e.dismissal == Dismissal.HIT_WICKET
                  || e.dismissal == Dismissal.OBSTRUCTING);
      String dismissed = e.nonStrikerOut ? s.nonStriker : s.striker;
      if (((e.completed + e.overthrow) % 2 == 1) ^ e.crossed) s.swap();
      if (r.lastMan && s.striker.isEmpty()) s.swap();
      if (dismissalAllowed) {
        s.wickets++;
        Batting out = s.bat(dismissed);
        out.out = true;
        out.dismissal = e.dismissal.name();
        if (e.dismissal != Dismissal.RUN_OUT && e.dismissal != Dismissal.OBSTRUCTING) bw.wickets++;
        if (!e.fielder.isEmpty())
          s.catches.put(e.fielder, s.catches.getOrDefault(e.fielder, 0) + 1);
        s.falls.add(s.runs + "/" + s.wickets + " " + dismissed + " (" + s.overs(r) + ")");
        String next = e.incoming;
        if (r.lastMan && s.wickets == r.players - 1) next = "";
        if (dismissed.equals(s.striker)) s.striker = next;
        else if (dismissed.equals(s.nonStriker)) s.nonStriker = next;
        if (!next.isEmpty()) s.bat(next);
        if (r.lastMan && s.striker.isEmpty()) s.swap();
      }
      if (legal && s.legal % r.ballsPerOver == 0) {
        s.swap();
        if (r.lastMan && s.striker.isEmpty()) s.swap();
        s.bowler = "";
      }
      if (nb) s.freeHit = true;
      else if (!wide && !pen) s.freeHit = false;
      s.commentary.add(
          s.overs(r)
              + " · "
              + (dismissalAllowed
                  ? "Wicket! "
                      + dismissed
                      + " departs, "
                      + e.dismissal.name().replace('_', ' ').toLowerCase(Locale.ROOT)
                      + "."
                  : e.boundary && e.bat == 6
                      ? batter + " sends it all the way for six!"
                      : e.boundary && e.bat == 4
                          ? batter + " finds the gap for four!"
                          : wide
                              ? "Wide called. " + total + " added."
                              : nb
                                  ? "No-ball! Free hit follows. " + total + " added."
                                  : batter + ": " + total + " run" + (total == 1 ? "" : "s") + ". ")
              + " "
              + s.runs
              + "/"
              + s.wickets);
    }
  }

  public static final class Match implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id = id(),
        name = "",
        teamA,
        teamB,
        tossWinner = "",
        tossDecision = "",
        scorer = "",
        umpire = "",
        venue = "",
        tournament = "";
    public Rules rules = new Rules();
    public ArrayList<Innings> innings = new ArrayList<>();
    public ArrayList<String> rosterA = new ArrayList<>(), rosterB = new ArrayList<>();
    public long updated = System.currentTimeMillis();
    public int revision;

    public Match(String a, String b) {
      teamA = a;
      teamB = b;
      name = a + " v " + b;
    }

    public Innings current() {
      return innings.get(activeIndex());
    }

    public int activeIndex() {
      return innings.size() > 1 && !innings.get(0).state(rules).closed ? 0 : innings.size() - 1;
    }

    public State state() {
      return activeIndex() == 1
          ? current().state(rules, innings.get(0).state(rules).runs + 1)
          : current().state(rules);
    }

    public String result() {
      if (innings.size() < 2 || !state().closed) return "Live";
      State a = innings.get(0).state(rules), b = state();
      if (b.runs >= b.target)
        return teamB
            + " won by "
            + Math.max(0, rules.players - (rules.lastMan ? 0 : 1) - b.wickets)
            + " wickets";
      if (b.runs == b.target - 1) return "Match tied";
      return teamA + " won by " + Math.max(0, b.target - 1 - b.runs) + " runs";
    }

    public void add(Event e) {
      State s = state();
      if (e.kind == Kind.BALL) {
        if (s.closed)
          throw new IllegalArgumentException("Innings is complete. Undo or start the chase.");
        if (s.bowler.isEmpty())
          throw new IllegalArgumentException("Select a bowler for this over.");
        if (s.striker.isEmpty() || s.nonStriker.isEmpty() && !rules.lastMan)
          throw new IllegalArgumentException("Select both batters.");
        if (e.bat < 0 || e.extras < 0 || e.completed < 0 || e.overthrow < 0)
          throw new IllegalArgumentException("Runs cannot be negative.");
        boolean legalDismissal = e.dismissal == Dismissal.NONE
            || ((!s.freeHit && e.extra != Extra.NO_BALL)
                || e.dismissal == Dismissal.RUN_OUT || e.dismissal == Dismissal.OBSTRUCTING)
                && (e.extra != Extra.WIDE || e.dismissal == Dismissal.RUN_OUT
                    || e.dismissal == Dismissal.STUMPED || e.dismissal == Dismissal.HIT_WICKET
                    || e.dismissal == Dismissal.OBSTRUCTING);
        if (!legalDismissal)
          throw new IllegalArgumentException("That dismissal is not allowed on this delivery.");
        if (e.dismissal != Dismissal.NONE && !e.incoming.isEmpty()) {
          ArrayList<String> battingRoster = current().battingTeam.equals(teamA) ? rosterA : rosterB;
          if (!battingRoster.isEmpty() && !battingRoster.contains(e.incoming))
            throw new IllegalArgumentException("Incoming batter is not in the playing squad.");
          Batting incoming = s.batting.get(e.incoming);
          if (incoming != null && (incoming.out || incoming.retired
              || e.incoming.equals(s.striker) || e.incoming.equals(s.nonStriker)))
            throw new IllegalArgumentException("Choose an available incoming batter.");
        }
        e.bowler = s.bowler;
      }
      if (e.kind == Kind.BOWLER) {
        ArrayList<String> bowlingRoster = current().bowlingTeam.equals(teamA) ? rosterA : rosterB;
        if (!bowlingRoster.isEmpty() && !bowlingRoster.contains(e.player))
          throw new IllegalArgumentException("Bowler is not in the playing squad.");
      }
      if (e.kind == Kind.BATTERS) {
        ArrayList<String> battingRoster = current().battingTeam.equals(teamA) ? rosterA : rosterB;
        if (e.player.equals(e.partner) || e.player.isEmpty() || e.partner.isEmpty())
          throw new IllegalArgumentException("Choose two different batters.");
        if (!battingRoster.isEmpty()
            && (!battingRoster.contains(e.player) || !battingRoster.contains(e.partner)))
          throw new IllegalArgumentException("Both batters must be in the playing squad.");
      }
      if (e.kind == Kind.BATTERS && (s.bat(e.player).out || s.bat(e.partner).out
          || s.bat(e.player).retired || s.bat(e.partner).retired))
        throw new IllegalArgumentException("Dismissed or retired batters cannot be selected at the crease.");
      if (e.kind == Kind.RETIRE) {
        if (s.closed || (!e.player.equals(s.striker) && !e.player.equals(s.nonStriker)))
          throw new IllegalArgumentException("Choose a current batter to retire.");
        if (!e.incoming.isEmpty()) {
          ArrayList<String> battingRoster = current().battingTeam.equals(teamA) ? rosterA : rosterB;
          Batting replacement = s.batting.get(e.incoming);
          if (!battingRoster.isEmpty() && !battingRoster.contains(e.incoming)
              || e.incoming.equals(s.striker) || e.incoming.equals(s.nonStriker)
              || replacement != null && (replacement.out || replacement.retired))
            throw new IllegalArgumentException("Choose an available replacement batter.");
        }
      }
      if (e.kind == Kind.RESUME) {
        Batting returning = s.batting.get(e.player);
        String end = e.nonStrikerOut ? s.nonStriker : s.striker;
        if (s.closed || returning == null || !returning.retired || returning.out || !end.isEmpty())
          throw new IllegalArgumentException("A retired batter can return only to a vacant end.");
      }
      if (e.kind == Kind.END_INNINGS && s.closed)
        throw new IllegalArgumentException("This innings has already finished.");
      if (e.kind == Kind.NOTE && (e.note == null || e.note.trim().isEmpty()
          || e.note.length() > 500))
        throw new IllegalArgumentException("Enter a note of 1–500 characters.");
      current().events.add(e);
      revision++;
      updated = System.currentTimeMillis();
    }

    public void undo(int count) {
      if (count < 1) throw new IllegalArgumentException("Undo count must be positive.");
      List<Event> es = current().events;
      for (int i = es.size() - 1; i >= 0 && count > 0; i--) {
        Event e = es.get(i);
        if (!e.reversed) {
          String label = e.label();
          e.reversed = true;
          e.note = "Undone " + label;
          if (e.kind == Kind.BALL
              || e.kind == Kind.RETIRE
              || e.kind == Kind.RESUME
              || e.kind == Kind.INTERRUPTION
              || e.kind == Kind.END_INNINGS
              || e.kind == Kind.NOTE) count--;
        }
      }
      revision++;
    }

    public void startChase(String p, String q) {
      if (innings.size() != 1 || !state().closed)
        throw new IllegalArgumentException("Finish the first innings before starting the chase.");
      innings.add(new Innings(teamB, teamA, p, q, state().runs + 1));
      revision++;
    }
  }

  public enum Format {
    LEAGUE,
    KNOCKOUT,
    GROUPS
  }

  public static final class Fixture implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id = id(), a, b, matchId = "", round;

    public Fixture(String a, String b, String r) {
      this.a = a;
      this.b = b;
      round = r;
    }
  }

  public static final class Tournament implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id = id(), name;
    public Format format;
    public ArrayList<String> teams = new ArrayList<>();
    public ArrayList<Fixture> fixtures = new ArrayList<>();

    public Tournament(String n, Format f) {
      name = n;
      format = f;
    }

    public void generate() {
      if (teams.size() < 2) throw new IllegalArgumentException("Register at least two teams.");
      if (format == Format.GROUPS && teams.size() < 4)
        throw new IllegalArgumentException("Two groups need at least four teams.");
      if (!fixtures.isEmpty())
        throw new IllegalArgumentException("Fixtures have already been generated.");
      if (format == Format.KNOCKOUT) {
        int n = 1;
        while (n < teams.size()) n *= 2;
        ArrayList<String> list = new ArrayList<>(teams);
        while (list.size() < n) list.add("BYE");
        for (int i = 0; i < n / 2; i++)
          fixtures.add(new Fixture(list.get(i), list.get(n - 1 - i), "Round 1"));
      } else
        for (int i = 0; i < teams.size(); i++)
          for (int j = i + 1; j < teams.size(); j++)
            if (format != Format.GROUPS || i % 2 == j % 2)
              fixtures.add(
                  new Fixture(
                      teams.get(i),
                      teams.get(j),
                      format == Format.GROUPS ? "Group " + (i % 2 == 0 ? "A" : "B") : "League"));
    }

    /**
     * Advances completed rounds; replayed results also invalidate an unplayed downstream pairing.
     */
    public void advance(List<Match> matches) {
      if (fixtures.isEmpty() || format == Format.LEAGUE) return;
      if (format == Format.GROUPS) {
        ArrayList<Fixture> group = new ArrayList<>();
        for (Fixture f : fixtures) if (f.round.startsWith("Group")) group.add(f);
        for (Fixture f : group) if (winner(f, matches) == null) return;
        ArrayList<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (Row row : standings(this, matches)) {
          if (teams.indexOf(row.team) % 2 == 0) a.add(row.team);
          else b.add(row.team);
        }
        if (a.size() < 2 || b.size() < 2) return;
        pair("Semifinal", 0, a.get(0), b.get(1));
        pair("Semifinal", 1, b.get(0), a.get(1));
        ArrayList<String> winners = new ArrayList<>();
        for (Fixture f : fixtures)
          if (f.round.equals("Semifinal")) {
            String w = winner(f, matches);
            if (w == null) return;
            winners.add(w);
          }
        if (winners.size() == 2) pair("Final", 0, winners.get(0), winners.get(1));
        return;
      }
      String round = "Round 1";
      for (int depth = 1; depth < 10; depth++) {
        ArrayList<String> winners = new ArrayList<>();
        for (Fixture f : fixtures)
          if (f.round.equals(round)) {
            String w = winner(f, matches);
            if (w == null) return;
            winners.add(w);
          }
        if (winners.size() < 2) return;
        String next = "Round " + (depth + 1);
        for (int i = 0; i < winners.size(); i += 2)
          pair(next, i / 2, winners.get(i), i + 1 < winners.size() ? winners.get(i + 1) : "BYE");
        round = next;
      }
    }

    private void pair(String round, int index, String a, String b) {
      int n = 0;
      for (Fixture f : fixtures)
        if (f.round.equals(round)) {
          if (n++ == index) {
            if (!f.a.equals(a) || !f.b.equals(b)) {
              f.a = a;
              f.b = b;
              f.matchId = "";
            }
            return;
          }
        }
      fixtures.add(new Fixture(a, b, round));
    }

    private String winner(Fixture f, List<Match> matches) {
      if (f.a.equals("BYE")) return f.b;
      if (f.b.equals("BYE")) return f.a;
      for (Match m : matches)
        if (m.id.equals(f.matchId) && m.innings.size() == 2 && m.state().closed) {
          State s = m.state();
          if (s.runs >= s.target) return m.teamB;
          if (s.runs < s.target - 1) return m.teamA;
          return null;
        }
      return null;
    }
  }

  public static final class Row {
    public String team;
    public double oversFor, oversAgainst;
    public int played, won, lost, tied, points, runsFor, ballsFor, runsAgainst, ballsAgainst;

    public double nrr(int bpo) {
      return (oversFor == 0 ? 0 : runsFor / oversFor)
          - (oversAgainst == 0 ? 0 : runsAgainst / oversAgainst);
    }
  }

  public static List<Row> standings(Tournament t, List<Match> matches) {
    Map<String, Row> rows = new LinkedHashMap<>();
    for (String team : t.teams) {
      Row r = new Row();
      r.team = team;
      rows.put(team, r);
    }
    for (Match m : matches) {
      if (!m.tournament.equals(t.id) || m.innings.size() != 2 || !m.state().closed) continue;
      if (t.format == Format.GROUPS) {
        boolean groupMatch = false;
        for (Fixture fixture : t.fixtures)
          if (fixture.round.startsWith("Group") && fixture.matchId.equals(m.id)) groupMatch = true;
        if (!groupMatch) continue;
      }
      Row a = rows.get(m.teamA), b = rows.get(m.teamB);
      if (a == null || b == null) continue;
      State x = m.innings.get(0).state(m.rules), y = m.state();
      a.played++;
      b.played++;
      int xb = x.wickets >= m.rules.players - 1 ? x.maxBalls : x.legal,
          yb = y.wickets >= m.rules.players - 1 ? y.maxBalls : y.legal;
      a.runsFor += x.runs;
      a.ballsFor += xb;
      a.runsAgainst += y.runs;
      a.ballsAgainst += yb;
      a.oversFor += xb / (double) m.rules.ballsPerOver;
      a.oversAgainst += yb / (double) m.rules.ballsPerOver;
      b.runsFor += y.runs;
      b.ballsFor += yb;
      b.runsAgainst += x.runs;
      b.ballsAgainst += xb;
      b.oversFor += yb / (double) m.rules.ballsPerOver;
      b.oversAgainst += xb / (double) m.rules.ballsPerOver;
      if (y.runs >= y.target) {
        b.won++;
        b.points += 2;
        a.lost++;
      } else if (y.runs == y.target - 1) {
        a.tied++;
        b.tied++;
        a.points++;
        b.points++;
      } else {
        a.won++;
        a.points += 2;
        b.lost++;
      }
    }
    List<Row> result = new ArrayList<>(rows.values());
    result.sort(
        (a, b) -> a.points != b.points ? b.points - a.points : Double.compare(b.nrr(6), a.nrr(6)));
    return result;
  }

  public static int probability(State s, Rules r) {
    if (s.target == 0) {
      double strength = Math.max(0, (r.players - 1.0 - s.wickets) / Math.max(1, r.players - 1));
      double projected =
          s.runs + (s.closed ? 0 : Math.max(0, s.maxBalls - s.legal) * (.5 + .8 * strength));
      double par = s.maxBalls * 1.3;
      return (int)
          Math.max(
              1,
              Math.min(
                  99,
                  100
                      / (1
                          + Math.exp((par - projected) / Math.max(8, Math.sqrt(s.maxBalls) * 2)))));
    }
    if (s.target > 0 && s.runs >= s.target) return 100;
    if (s.closed) return 0;
    int left = Math.max(0, s.maxBalls - s.legal), need = Math.max(0, s.target - s.runs);
    double wickets = (r.players - 1.0 - s.wickets) / Math.max(1, r.players - 1);
    double expected = left * (.8 + 1.1 * wickets);
    return (int)
        Math.max(
            1,
            Math.min(
                99, 100 / (1 + Math.exp((need - expected) / Math.max(5, Math.sqrt(left) * 2)))));
  }

  public static int revisedTarget(State s, int newBalls) {
    if (newBalls <= s.legal || newBalls >= s.maxBalls)
      throw new IllegalArgumentException(
          "Revised total balls must be above balls bowled and below the current limit.");
    if (s.target == 0) return 0;
    double available = resource(s.maxBalls - s.legal, s.wickets),
        remaining = resource(newBalls - s.legal, s.wickets);
    return s.runs
        + Math.max(1, (int) Math.ceil((s.target - s.runs) * remaining / Math.max(.001, available)));
  }

  private static double resource(int balls, int wickets) {
    double capacity = Math.max(.1, (10 - Math.min(9, wickets)) / 10.0);
    return capacity * (1 - Math.exp(-balls / (35.0 * capacity)));
  }
}
