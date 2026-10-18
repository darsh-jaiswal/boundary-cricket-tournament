package com.example.madfinalproject.domain;

import java.io.Serializable;

/** A registry lookup receipt, keyed separately from legacy team records. */
public final class RegisteredPlayer implements Serializable {
  private static final long serialVersionUID = 1L;
  public final String teamId, name, accountId;
  public RegisteredPlayer(String teamId, String name, String accountId) {
    this.teamId = teamId; this.name = name; this.accountId = accountId;
  }
  public String storageId() { return teamId + ":" + name; }
}
