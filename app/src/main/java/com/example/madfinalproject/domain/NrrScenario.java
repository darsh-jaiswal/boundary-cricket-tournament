package com.example.madfinalproject.domain;

/** A hypothetical innings pair added to existing tournament run and over totals. */
public final class NrrScenario {
  private NrrScenario() {}

  public static final class Result {
    public final double current, projected, delta;

    private Result(double current, double projected) {
      this.current = current;
      this.projected = projected;
      this.delta = projected - current;
    }
  }

  public static Result calculate(
      Cricket.Row team,
      int runsFor,
      int ballsFaced,
      int runsAgainst,
      int ballsBowled,
      int ballsPerOver) {
    if (team == null) throw new IllegalArgumentException("Select a tournament team.");
    if (runsFor < 0 || runsAgainst < 0)
      throw new IllegalArgumentException("Runs must be zero or greater.");
    if (ballsFaced <= 0 || ballsBowled <= 0)
      throw new IllegalArgumentException("Both ball totals must be greater than zero.");
    if (ballsPerOver != 4 && ballsPerOver != 5 && ballsPerOver != 6 && ballsPerOver != 8)
      throw new IllegalArgumentException("Choose 4, 5, 6 or 8 balls per over.");
    if (team.runsFor < 0 || team.runsAgainst < 0
        || !Double.isFinite(team.oversFor) || !Double.isFinite(team.oversAgainst)
        || team.oversFor < 0 || team.oversAgainst < 0)
      throw new IllegalArgumentException("The saved tournament totals are inconsistent.");
    double projected =
        ((double) team.runsFor + runsFor) / (team.oversFor + ballsFaced / (double) ballsPerOver)
            - ((double) team.runsAgainst + runsAgainst)
                / (team.oversAgainst + ballsBowled / (double) ballsPerOver);
    return new Result(team.nrr(ballsPerOver), projected);
  }
}
