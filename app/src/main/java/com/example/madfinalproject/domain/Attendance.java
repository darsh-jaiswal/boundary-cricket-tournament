package com.example.madfinalproject.domain;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Device-local availability notes; independent of scored match journals and publication. */
public final class Attendance implements Serializable {
  private static final long serialVersionUID = 1L;
  public enum Status { UNKNOWN, AVAILABLE, UNAVAILABLE }
  public final String matchId;
  public long updated;
  private final Map<String, Status> statuses = new LinkedHashMap<>();

  public Attendance(String matchId) {
    if (matchId == null || matchId.isEmpty()) throw new IllegalArgumentException("A match is required.");
    this.matchId = matchId;
  }

  public String storageId() { return matchId + "-attendance"; }

  public Status status(String team, String player) {
    return statuses.getOrDefault(key(team, player), Status.UNKNOWN);
  }

  public void set(String team, String player, Status status) {
    if (status == null) throw new IllegalArgumentException("Choose an availability status.");
    String key = key(team, player);
    if (status == Status.UNKNOWN) statuses.remove(key);
    else statuses.put(key, status);
  }

  private String key(String team, String player) {
    if (team == null || team.isEmpty() || player == null || player.isEmpty())
      throw new IllegalArgumentException("A team and player are required.");
    return team + "\u0000" + player;
  }
}
