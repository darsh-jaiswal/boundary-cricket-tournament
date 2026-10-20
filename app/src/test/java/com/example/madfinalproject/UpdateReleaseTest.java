package com.example.madfinalproject;

import static org.junit.Assert.*;
import org.junit.Test;

public class UpdateReleaseTest {
  private UpdateRelease release(String version, long code) {
    return new UpdateRelease(version, code, source(version), "a".repeat(64), 95_000_000);
  }

  private String source(String version) {
    return "https://github.com/darsh-jaiswal/boundary-cricket-tournament/releases/download/v"
        + version + "/Boundary-Android-arm64.apk";
  }

  @Test public void numericVersionsDoNotCompareLexicographically() {
    assertTrue(release("1.0.10",0).newerThan(10,"1.0.9"));
    assertFalse(release("1.0.9",0).newerThan(11,"1.0.10"));
    assertTrue(release("1.10.0",0).newerThan(1,"1.9.99"));
    assertTrue(release("2.0.0",0).newerThan(1,"1.99.99"));
    assertFalse(release("1.0.10",0).newerThan(11,"1.0.10"));
  }

  @Test public void versionCodePreventsSameVersionAndDowngradePrompts() {
    assertTrue(release("1.0.11",12).newerThan(11,"1.0.10"));
    assertFalse(release("1.0.11",12).newerThan(12,"1.0.11"));
    assertFalse(release("1.0.11",12).newerThan(13,"1.0.12"));
    assertFalse(release("9.0.0",12).newerThan(12,"1.0.11"));
  }

  @Test public void malformedMetadataAndUntrustedDownloadsAreRejected() {
    for (String bad : new String[]{"", "1.0", "1.0.11-beta", "../download", "9999999.0.0"})
      assertThrows(IllegalArgumentException.class, () -> release(bad,12));
    assertThrows(IllegalArgumentException.class, () -> release(null,12));
    assertThrows(IllegalArgumentException.class, () -> release("1.0.11",-1));
    assertThrows(IllegalArgumentException.class, () -> release("1.0.11",2100000001L));
    for (String bad : new String[]{"http://evil.test/app.apk",source("1.0.10"),source("1.0.11")+"?redirect=evil"})
      assertThrows(IllegalArgumentException.class, () -> new UpdateRelease("1.0.11",12,bad,"a".repeat(64),95_000_000));
    assertThrows(IllegalArgumentException.class, () -> new UpdateRelease("1.0.11",12,source("1.0.11"),"bad",95_000_000));
    for (long bytes : new long[]{0,-1,100_000_001})
      assertThrows(IllegalArgumentException.class, () -> new UpdateRelease("1.0.11",12,source("1.0.11"),"a".repeat(64),bytes));
  }

  @Test public void legacyMetadataDoesNotGuessAnUnknownInstalledVersion() {
    assertFalse(release("1.0.11",0).newerThan(12,null));
    assertFalse(release("1.0.11",0).newerThan(12,"unknown"));
    assertEquals("95.0 MB",release("1.0.11",12).sizeLabel());
  }
}
