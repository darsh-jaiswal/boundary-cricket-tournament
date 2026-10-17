# Boundary 1.0.3

Match creation now records a toss winner and bat/bowl decision, chooses exact
playing squads, and confirms opening batters. Scheduled fixture and match
records save together. The Scorer and Umpire workspaces show match details,
recent effective deliveries and direct access to push-to-talk scoring.

Voice scoring uses the bundled open source Vosk Indian English model. Press
and hold, speak, release, then confirm the recognized command. Cancelled
touches and backgrounding discard the recording. Optional local speaker
enrollment remains available for longer utterances. Scoring still requires
the device official PIN.

The signed arm64 APK is 94,734,199 bytes with SHA-256
`794ee824dc189ceeffa91b6f8107b2d4277e42f7bc38f75042035b68bbd362cc`.
Release assembly and lint completed. Ground audio accuracy and the full
CricHeroes scoring workflow have not been checked on a physical device; this
release is an incremental implementation, not feature or visual parity.
