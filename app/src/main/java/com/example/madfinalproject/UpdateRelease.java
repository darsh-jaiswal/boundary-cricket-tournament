package com.example.madfinalproject;

import java.util.Locale;

/** Validated public release metadata. Version codes are authoritative when supplied. */
public final class UpdateRelease {
  public final String version, source;
  public final long versionCode, bytes;

  public UpdateRelease(String version, long versionCode, String source, String sha256, long bytes) {
    if (version == null || !version.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
        || versionCode < 0 || versionCode > 2100000000L
        || !expectedSource(version).equals(source)
        || sha256 == null || !sha256.matches("[a-f0-9]{64}") || bytes <= 0 || bytes > 100_000_000)
      throw new IllegalArgumentException("Invalid Android release metadata");
    this.version = version;
    this.versionCode = versionCode;
    this.source = source;
    this.bytes = bytes;
  }

  private static String expectedSource(String version) {
    return "https://github.com/darsh-jaiswal/boundary-cricket-tournament/releases/download/v"
        + version + "/Boundary-Android-arm64.apk";
  }

  public boolean newerThan(long installedCode, String installedVersion) {
    if (versionCode > 0) return versionCode > installedCode;
    // Older release manifests did not publish a version code.
    if (installedVersion == null || !installedVersion.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")) return false;
    String[] latest = version.split("\\."), installed = installedVersion.split("\\.");
    for (int i = 0; i < latest.length; i++) {
      int difference = Integer.compare(Integer.parseInt(latest[i]), Integer.parseInt(installed[i]));
      if (difference != 0) return difference > 0;
    }
    return false;
  }

  public String sizeLabel() { return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0); }
}
