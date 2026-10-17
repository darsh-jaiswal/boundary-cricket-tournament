# Official scoring workspace

The Scorer and Umpire roles open the selected match's scoring workspace. It
shows live score, batters, bowler, recent effective deliveries, the toss and
official names. Touch scoring, wicket details, history, corrections and voice
scoring are available from the same match screen. A device-local official PIN
protects changes; role labels are not remote account assignments.

Match setup now chooses two teams, toss winner and bat/bowl decision, rules,
the playing squad for each side, and opening batters. Squads are saved with
the match so subsequent team edits do not change a live match. Fixture match
and tournament records are saved in one SQLite transaction.

Push-to-talk uses the existing [Vosk Android repository](https://github.com/alphacep/vosk-api)
and the bundled [small Indian English model](https://alphacephei.com/vosk/models).
Hold the microphone button, speak, and release; audio is recorded for at most
15 seconds and transcribed offline. A cancelled press discards its audio. An accessibility activation records for
five seconds. A scorer can optionally enroll three voice samples for local
speaker matching on recordings of at least three seconds. All recognized
commands require an explicit review and confirmation before the match journal
changes. Recognition accuracy and exact CricHeroes dashboard parity have not
been established on a physical cricket ground.

The CricHeroes 26.10.1 APK's `TossActivity` class inventory contains team,
bat/bowl choice and toss methods (REA Evidence
`ev_77744ccf8f9b77a6db2bb6e8409ac61c723f18d34c44c0c03901f80322cacad6`).
Its `MatchScoreCardActivity` inventory contains quick action, scorecard edit,
undo, bowler change, innings completion and playing squad menu entry points.
These static entry points informed investigation; runtime layout and behavior
remain unverified.
