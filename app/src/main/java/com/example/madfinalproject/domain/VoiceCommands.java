package com.example.madfinalproject.domain;

import java.util.Locale;
import java.util.regex.*;

/** Parses score commands only; authorization and confirmations are handled by the UI. */
public final class VoiceCommands {
  public enum Action {
    SCORE,
    UNDO,
    NOT_OUT,
    CORRECT,
    REVIEW,
    UNKNOWN
  }

  public static final class Command {
    public Action action = Action.UNKNOWN;
    public int runs, count = 1, over = -1, ball = -1;
    public Cricket.Extra extra = Cricket.Extra.NONE;
    public String normalized;
  }

  private VoiceCommands() {}

  public static int number(String text) {
    String[] words = {
      "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"
    };
    for (int i = 0; i < words.length; i++)
      if (text.matches(".*\\b(?:" + words[i] + "|" + i + ")\\b.*")) return i;
    return 0;
  }

  public static Command parse(String input) {
    Command c = new Command();
    String s = input.toLowerCase(Locale.ROOT).replace('-', ' ');
    c.normalized = s;
    if (s.contains("undo")) {
      c.action = Action.UNDO;
      c.count = Math.max(1, number(s));
      return c;
    }
    if (s.contains("not out")) {
      c.action = Action.NOT_OUT;
      return c;
    }
    if (s.contains("check run out") || s.contains("check no ball")) {
      c.action = Action.REVIEW;
      return c;
    }
    c.extra =
        s.contains("wide")
            ? Cricket.Extra.WIDE
            : s.contains("no ball")
                ? Cricket.Extra.NO_BALL
                : s.contains("leg bye")
                    ? Cricket.Extra.LEG_BYE
                    : s.contains("bye") ? Cricket.Extra.BYE : Cricket.Extra.NONE;
    c.action = s.contains("change") || s.contains("correction") ? Action.CORRECT : Action.SCORE;
    Matcher address = Pattern.compile("\\bball\\s+(\\d+)\\.(\\d+)\\b").matcher(s);
    if (address.find()) {
      c.over = Integer.parseInt(address.group(1));
      c.ball = Integer.parseInt(address.group(2));
    }
    String target = s.contains(" to ") ? s.substring(s.lastIndexOf(" to ") + 4) : s;
    c.runs = number(target);
    boolean known =
        target.matches(
            ".*\\b(zero|one|two|three|four|five|six|[0-9]|dot|wide|bye|ball|wicket|out)\\b.*");
    if (!known) c.action = Action.UNKNOWN;
    return c;
  }
}
