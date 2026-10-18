package com.example.madfinalproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import org.junit.Test;

public class SocialTest {
  private static final String ORIGIN = PlayerAccount.ORIGIN;

  @Test public void payloadRoundTripsThroughParse() {
    String id = "user_2pQx9ZkLmN0aB";
    assertEquals(ORIGIN + "/player.html?id=" + id, Social.qrPayload(id));
    assertEquals(id, Social.parseQr(Social.qrPayload(id)));
    assertEquals(id, Social.parseQr("  " + Social.qrPayload(id) + "\n"));
  }

  @Test public void readsTheIdWhereverItSitsInTheQuery() {
    assertEquals("user_abc", Social.parseQr(ORIGIN + "/player.html?ref=qr&id=user_abc#follow"));
    assertEquals("user_abc", Social.parseQr(ORIGIN + "?id=user_abc"));
    assertEquals("user_abc", Social.parseQr(ORIGIN + "/player.html?id=user%5Fabc"));
  }

  @Test public void rejectsCodesThatAreNotBoundaryProfiles() {
    assertNull(Social.parseQr(null));
    assertNull(Social.parseQr(""));
    assertNull(Social.parseQr("user_abc"));
    assertNull(Social.parseQr("WIFI:S:Ground;T:WPA;P:secret;;"));
    assertNull(Social.parseQr("https://example.com/player.html?id=user_abc"));
    assertNull(Social.parseQr("http://boundary-cricket-tournament.vercel.app/player.html?id=user_abc"));
    assertNull(Social.parseQr(ORIGIN + ".evil.example/player.html?id=user_abc"));
    assertNull(Social.parseQr(ORIGIN + "@evil.example/player.html?id=user_abc"));
    assertNull(Social.parseQr(ORIGIN + ":8443/player.html?id=user_abc"));
    assertNull(Social.parseQr(ORIGIN + "/player.html"));
    assertNull(Social.parseQr(ORIGIN + "/player.html#id=user_abc"));
    assertNull(Social.parseQr(ORIGIN + "/player.html?link=user_abc"));
  }

  @Test public void rejectsIdsThatAreNotAccountIds() {
    for (String id : new String[] {"", "user_", "admin", "user_ab-c", "user_abc%20x", "user_abc%2", "user_abc'--"})
      assertNull(id, Social.parseQr(ORIGIN + "/player.html?id=" + id));
  }

  @Test public void outdatedServerAnswersReadAsNotDeployed() {
    assertEquals(Social.NOT_DEPLOYED, Social.friendly(new IllegalStateException("Unknown action")));
    assertEquals(Social.NOT_DEPLOYED, Social.friendly(new IllegalStateException("Sign in to continue")));
    assertEquals(Social.NOT_DEPLOYED, Social.friendly(new IllegalStateException("Player service returned 404")));
  }

  @Test public void serverMessagesPassThroughAndFailuresStayFriendly() {
    assertEquals("This player has not registered on Boundary yet",
        Social.friendly(new IllegalStateException("This player has not registered on Boundary yet")));
    assertEquals(Social.UNAVAILABLE, Social.friendly(new IllegalStateException()));
    assertEquals(Social.UNAVAILABLE, Social.friendly(new IllegalArgumentException("Value <html> of type java.lang.String")));
    assertEquals("Check your internet connection and try again.", Social.friendly(new IOException("Unable to resolve host")));
  }
}
