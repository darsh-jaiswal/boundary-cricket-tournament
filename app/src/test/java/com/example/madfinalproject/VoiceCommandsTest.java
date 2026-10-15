package com.example.madfinalproject;

import static org.junit.Assert.*;

import com.example.madfinalproject.domain.Cricket;
import com.example.madfinalproject.domain.VoiceCommands;
import org.junit.Test;

public class VoiceCommandsTest {
  @Test
  public void undoTwo() {
    VoiceCommands.Command c = VoiceCommands.parse("Undo the last two balls");
    assertEquals(VoiceCommands.Action.UNDO, c.action);
    assertEquals(2, c.count);
  }

  @Test
  public void reversedNotOut() {
    assertEquals(
        VoiceCommands.Action.NOT_OUT,
        VoiceCommands.parse("Umpire reversed the decision not out").action);
  }

  @Test
  public void changeSixToFourUsesDestination() {
    VoiceCommands.Command c = VoiceCommands.parse("Change last ball from six runs to four runs");
    assertEquals(4, c.runs);
    assertEquals(VoiceCommands.Action.CORRECT, c.action);
  }

  @Test
  public void addressedCorrection() {
    VoiceCommands.Command c =
        VoiceCommands.parse("Correction ball 3.2 was leg byes not runs off the bat");
    assertEquals(3, c.over);
    assertEquals(2, c.ball);
    assertEquals(Cricket.Extra.LEG_BYE, c.extra);
  }

  @Test
  public void widePlusOne() {
    VoiceCommands.Command c = VoiceCommands.parse("Wide ball plus one extra run");
    assertEquals(Cricket.Extra.WIDE, c.extra);
    assertEquals(1, c.runs);
  }

  @Test
  public void naturalNamedBoundary() {
    VoiceCommands.Command c = VoiceCommands.parse("Four runs by Rohan off Aman");
    assertEquals(VoiceCommands.Action.SCORE, c.action);
    assertEquals(4, c.runs);
  }

  @Test
  public void unknownCannotScoreDot() {
    assertEquals(
        VoiceCommands.Action.UNKNOWN, VoiceCommands.parse("Hello everyone good afternoon").action);
  }
}
