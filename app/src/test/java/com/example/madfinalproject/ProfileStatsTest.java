package com.example.madfinalproject;

import static org.junit.Assert.*;

import com.example.madfinalproject.domain.Career;
import com.example.madfinalproject.domain.Cricket.*;
import com.example.madfinalproject.domain.PlayerProfile;
import com.example.madfinalproject.domain.ProfileStats;
import java.util.*;
import org.junit.Test;

public class ProfileStatsTest {
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private static Match match(String opener, String bowler, int overs) {
    Match m = new Match("Lions", "Tigers");
    m.rules.overs = overs;
    m.rules.bowlerOvers = overs;
    m.innings.add(new Innings("Lions", "Tigers", opener, "Dev", 0));
    Event e = new Event();
    e.kind = Kind.BOWLER;
    e.player = bowler;
    m.add(e);
    return m;
  }

  private static void run(Match m, int runs, int zone) {
    Event e = new Event();
    e.bat = runs;
    e.completed = runs % 2;
    e.boundary = runs == 4 || runs == 6;
    e.zone = zone;
    m.add(e);
  }

  private static void caught(Match m, String incoming, String fielder) {
    Event e = new Event();
    e.dismissal = Dismissal.CAUGHT;
    e.incoming = incoming;
    e.fielder = fielder;
    m.add(e);
  }

  private static Map<String, String> byLabel(List<ProfileStats.Stat> stats) {
    Map<String, String> map = new LinkedHashMap<>();
    for (ProfileStats.Stat s : stats) map.put(s.label, s.value);
    return map;
  }

  @Test
  public void battingTilesShowDashesInsteadOfInventedAveragesAndRates() {
    Match m = match("Ravi", "Kabir", 2);
    run(m, 4, 1);
    run(m, 0, -1);
    Career ravi = Career.of(Collections.singletonList(m), new Career.Who("Ravi").add("Lions", "Ravi"), null);
    Map<String, String> tiles = byLabel(ProfileStats.stats(ProfileStats.BATTING, ravi));
    assertEquals(Arrays.asList("Mat", "Inns", "NO", "Runs", "HS", "Avg", "SR", "30s", "50s", "100s", "4s", "6s", "Ducks", "Won", "Loss"),
        new ArrayList<>(tiles.keySet()));
    assertEquals("4", tiles.get("Runs"));
    assertEquals("4*", tiles.get("HS"));
    assertEquals("-", tiles.get("Avg")); // never dismissed
    assertEquals("200.00", tiles.get("SR"));
    assertEquals("1", tiles.get("4s"));
  }

  @Test
  public void bowlingAndFieldingTilesCountWhatWasRecorded() {
    Match m = match("Ravi", "Kabir", 2);
    run(m, 4, 2);
    caught(m, "Sam", "Kabir"); // caught and bowled
    Career kabir = Career.of(Collections.singletonList(m), new Career.Who("Kabir").add("Tigers", "Kabir"), null);
    Map<String, String> bowl = byLabel(ProfileStats.stats(ProfileStats.BOWLING, kabir));
    assertEquals(Arrays.asList("Mat", "Inns", "Overs", "Maidens", "Wkts", "Runs", "Best", "Eco", "SR", "Avg", "Wides", "NB", "Dots", "4s", "6s"),
        new ArrayList<>(bowl.keySet()));
    assertEquals("0.2", bowl.get("Overs"));
    assertEquals("1/4", bowl.get("Best"));
    assertEquals("12.00", bowl.get("Eco"));
    assertEquals("1", bowl.get("4s"));
    Map<String, String> field = byLabel(ProfileStats.stats(ProfileStats.FIELDING, kabir));
    assertEquals("1", field.get("Catches"));
    assertEquals("1", field.get("Caught & bowled"));
    assertEquals("0", field.get("Run outs"));

    Career nobody = Career.of(Collections.singletonList(m), new Career.Who("Zed").add("Tigers", "Zed"), null);
    Map<String, String> none = byLabel(ProfileStats.stats(ProfileStats.BOWLING, nobody));
    assertEquals("-", none.get("Overs"));
    assertEquals("-", none.get("Best"));
    assertEquals("-", none.get("Eco"));
  }

  @Test
  public void sectionsFollowRecordedBallAndMatchTypesOnly() {
    Match tennis = match("Ravi", "Kabir", 10);
    tennis.ballType = "TENNIS";
    tennis.matchType = "BOX";
    run(tennis, 1, 4);
    Match leather = match("Ravi", "Kabir", 20);
    leather.ballType = "LEATHER";
    leather.matchType = "LIMITED_OVERS";
    run(leather, 6, 4);
    Match unknown = match("Ravi", "Kabir", 10);
    unknown.ballType = null; // saved before ball types existed
    unknown.matchType = null;
    run(unknown, 2, -1);
    Career.Who ravi = new Career.Who("Ravi").add("Lions", "Ravi");
    Career all = Career.of(Arrays.asList(tennis, leather, unknown), ravi, null);

    List<ProfileStats.Section> sections = ProfileStats.sections(all.matches);
    assertEquals("[Leather, Tennis, Box Cricket]", sections.toString());
    assertEquals(Arrays.asList(10, 20), ProfileStats.oversPlayed(all.matches));

    Career.Filter base = new Career.Filter();
    base.overs = 10;
    Career.Filter tennisTens = sections.get(1).filter(base);
    assertEquals("TENNIS", tennisTens.ballType);
    assertEquals(10, tennisTens.overs);
    assertEquals(0, base.ballType.length()); // the base filter is left alone
    assertEquals(1, Career.of(Arrays.asList(tennis, leather, unknown), ravi, tennisTens).batStats().runs);
    assertEquals("BOX", sections.get(2).filter(null).matchType);

    assertEquals(2, ProfileStats.withZone(all.faced).size()); // the unrecorded direction is left out
  }

  @Test
  public void scoresDatesAndLabels() {
    Match m = match("Ravi", "Kabir", 2);
    run(m, 4, -1);
    run(m, 1, -1);
    assertArrayEquals(new String[] {"5/0", "0.2 Ov"}, ProfileStats.score(m, "Lions"));
    assertNull(ProfileStats.score(m, "Tigers"));

    long day = 24L * 60 * 60 * 1000;
    long now = 1_760_000_000_000L; // 9 Oct 2025 08:53 UTC
    assertEquals("Today", ProfileStats.matchDate(now - 60_000, now, UTC));
    assertEquals("Yesterday", ProfileStats.matchDate(now - day, now, UTC));
    assertEquals("07-Oct-25", ProfileStats.matchDate(now - 2 * day, now, UTC));
    assertEquals("Since 09 Oct, 2025", ProfileStats.since(now, UTC));

    assertEquals("1 match", ProfileStats.matchesLabel(1));
    assertEquals("41 matches", ProfileStats.matchesLabel(41));

    PlayerProfile p = new PlayerProfile();
    assertEquals("", ProfileStats.roleSentence(p));
    p.battingOrder = "Middle-order batter";
    p.bowlingStyle = "Right-arm leg break";
    assertEquals(Collections.singletonList("Middle-order batter"), ProfileStats.battingRole(p));
    assertEquals("Middle-order batter, Right-arm leg break", ProfileStats.roleSentence(p));

    assertEquals("abc123XY", ProfileStats.shortId("user_abc123XYZ789"));
    assertEquals("abc", ProfileStats.shortId("user_abc"));
    assertEquals("", ProfileStats.shortId(null));
  }
}
