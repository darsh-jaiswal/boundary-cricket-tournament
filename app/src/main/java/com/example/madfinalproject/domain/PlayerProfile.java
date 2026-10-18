package com.example.madfinalproject.domain;

import java.io.Serializable;
import java.util.Locale;

/** Self-described playing attributes. Stored per device, keyed by team and player name. */
public final class PlayerProfile implements Serializable {
  private static final long serialVersionUID = 1L;

  public static final String[] BALL_TYPES = {"TENNIS", "LEATHER"};
  public static final String[] MATCH_TYPES = {"LIMITED_OVERS", "BOX", "HUNDRED"};
  public static final String[] PITCH_TYPES = {"TURF", "MATTING", "CEMENT", "ASTROTURF", "ROUGH"};
  public static final String[] BATTING_HANDS = {"RHB", "LHB"};
  public static final String[] BATTING_ORDERS = {
    "Top-order batter", "Middle-order batter", "Lower-order batter"
  };
  public static final String[] BOWLING_STYLES = {
    "Right-arm fast", "Right-arm medium", "Right-arm off break", "Right-arm leg break",
    "Left-arm fast", "Left-arm medium", "Slow left-arm orthodox", "Left-arm wrist spin"
  };
  public static final String[] ROLES = {"Batter", "Bowler", "All-rounder", "Wicket-keeper batter"};

  public String team = "", name = "";
  public String battingHand = "", battingOrder = "", bowlingStyle = "", role = "", city = "";

  public static String key(String team, String name) {
    return "profile:" + team + "\u0000" + name;
  }

  public static String label(String code) {
    if (code == null || code.isEmpty()) return "Not recorded";
    switch (code) {
      case "TENNIS": return "Tennis";
      case "LEATHER": return "Leather";
      case "LIMITED_OVERS": return "Limited Overs";
      case "BOX": return "Box Cricket";
      case "HUNDRED": return "The Hundred";
      case "ASTROTURF": return "Astroturf";
      default:
        return code.charAt(0) + code.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }
  }

  /** PACE, SPIN or "" when the style is not recorded. */
  public static String paceOrSpin(String bowlingStyle) {
    if (bowlingStyle == null || bowlingStyle.isEmpty()) return "";
    String s = bowlingStyle.toLowerCase(Locale.ROOT);
    return s.contains("fast") || s.contains("medium") ? "PACE" : "SPIN";
  }

  public String summary() {
    StringBuilder b = new StringBuilder();
    if (!battingOrder.isEmpty()) b.append(battingOrder);
    if (!battingHand.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(battingHand);
    if (!bowlingStyle.isEmpty()) b.append(b.length() > 0 ? "\n" : "").append(bowlingStyle);
    return b.toString();
  }
}
