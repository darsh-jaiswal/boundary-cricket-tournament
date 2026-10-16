# Boundary — cricket tournaments and live scoring

Native Android application written in Java for local, college, turf and club cricket. SQLite is the source of truth on the scoring device. A Vercel API backed by Neon publishes score snapshots and stores authenticated, encrypted private journals to the public spectator site.

- Spectator site: https://boundary-cricket-tournament.vercel.app/
- Source: https://github.com/darsh-jaiswal/boundary-cricket-tournament
- Install on a modern physical Android phone: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
- Emulator: `app/build/outputs/apk/debug/app-x86_64-debug.apk`.
- Universal build: `app/build/outputs/apk/debug/app-universal-debug.apk`.

## First use

1. Open **Teams** and register two teams, entering player names separated by commas.
2. Press **Create a match** on Home. The first editing action asks you to create a six-to-twelve digit official PIN. Repeat the action after unlocking.
3. Select both teams, ground, scorer and umpire, then set match rules. The first two registered players open the innings; use **Batters** to change ends.
4. Select a bowler. Tap run buttons to save immediately. Hold a run button to add scoring zones, running runs and overthrows. Extras and wickets have detailed forms.
5. Use **History** to correct any recorded event, including earlier innings. Later deliveries remain in the journal and are replayed. Events after a newly calculated innings finish are retained but excluded until the innings reopens.
6. Save your private match recovery code from Tools if you need to recover or transfer scoring. Spectators only receive the public scoreboard. At innings completion start the chase. The chase target follows first-innings corrections; explicit interruption targets remain official events.
7. **Tools** contains voice enrollment, PA audio, field radar, scanner, stump cam, publication status and the control lock.

## Features

| Area | Current behavior |
| --- | --- |
| Roles | Seven device workspaces; spectators/players browse read-only. Officials unlock device editing with a salted, iterated PIN hash. Public API reads need no login. Each published match has a device-owned write key. |
| Tournaments | Team registration, league round robin, two groups with semifinals/final, knockout brackets with byes and automatic round advancement. Ties require a decided replay before knockout advancement. Points and NRR derive from completed scorecards. |
| Rules | 4/5/6/8 balls per over, squad size, overs, wide/no-ball penalties, last-batter rules, bowling limit, powerplay length and final-over extra legality. |
| Scoring | Bat runs, wides, no-balls, byes, leg byes, penalty runs, completed runs, overthrows, dismissal types, fielder attribution, incoming batter, retirements, batter-end changes and bowling changes. |
| Corrections | Replayed immutable delivery identities, retained reversed events, strike/end-over restoration, bowler figures, fall of wickets, incoming batter restoration, free-hit memory, run-out crossing, retirement reversal and innings reopening. Major reversals have a three-second cancellation window and spoken warning. |
| Offline | Transactional SQLite records and persistent publication outbox. Immediate foreground publication plus Android JobScheduler retries when connectivity returns and restores scheduled work after reboot. |
| Spectators | Real database snapshots, public match directory, score/target/win estimate, commentary and result. Browser refresh every three seconds; publishing depends on Android scheduling and network conditions. |
| Voice | Three enrollment samples, pretrained local speaker embeddings, similarity rejection indicator, optional wake word, same-audio offline Indian-English transcription, confirmed natural scoring and corrections. |
| PA | Android text-to-speech routes through the selected system audio output; boundaries, wickets, incoming players and over changes are announced. |
| Insights | Per-ball statistical win estimate, deterministic commentary/report, history-based scoring zones, tactical prompt and nine draggable fielders with powerplay-aware initial positions. |
| Scanner | Bundled on-device ML Kit OCR, editable text, row guesses, reviewed scorecard import and career-stat integration. |
| Replay | CameraX rolling four-second segments, two completed segments plus the active segment, latest-five-second export with keyframe padding, 0.25× playback, paired same-Wi-Fi review page and remote review request. |
| Interruptions | Official enters revised remaining balls. A local resource curve uses remaining balls and wickets; score, target and estimate update through a journal event. |

## Accuracy and deployment boundaries

Speaker verification is probabilistic. The 0.65 cosine threshold is a starting setting, not a ground-validated security guarantee. A crowded ground can contain overlapping speakers, replayed audio or very short utterances. Commands still require official confirmation. Do not advertise perfect unauthorized-voice rejection.

The win model, captain suggestions, commentary and match narrative are local algorithms; no paid generative AI service is configured. The interruption curve is a local estimate, not licensed official DLS. Handwritten OCR must be reviewed. Camera replay is an official visual review aid: it does not automatically detect the exact foot or bail event. The camera screen must stay open; paired replay phones must share Wi-Fi. Cache segments are bounded temporary files rather than an exact eight-second RAM buffer, and segment transitions may have gaps.

Roles are local to the scoring device, not a distributed account/organization service. Scorer and umpire assignments are recorded names. There is no email login or distributed organization account service. Scoring authority can be transferred using the private match recovery code. Public snapshots are published alongside a separately encrypted complete match journal. Tools → Recover full cloud match requires the private recovery code. Recovery restores rules, all deliveries and both squads. Keep that code private; it grants scoring authority. Voice enrollment remains on this phone and is excluded from OS backup.

The APK is signed with the Android development key for direct installation and college evaluation. Play Store publication and an owner-controlled release signing key are separate work.

## Architecture

`domain/Cricket.java` has framework-free rules, events, replay, tournament progression, standings and statistical estimates. `domain/VoiceCommands.java` parses commands. `data/Repository.java` persists versioned Java records in SQLite and queues snapshots. `Publication.java` schedules authorized HTTPS publication. Activities and `FieldView` implement the Android UI. `api/matches.js` atomically checks match ownership and revisions in Postgres. `web/index.html` renders public data using text nodes.

Schema migration is intentionally explicit: the early SharedPreferences starter data is preserved separately and is not silently imported into the new match journal. Java record classes declare serial version 1; future incompatible changes require a migration.

## Build and check

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
npm ci
vercel deploy --prod --yes
```

Device tests require an emulator or connected phone. The publication verification script needs a development `DATABASE_URL`; obtain it using `vercel env pull .env.local --yes`, then run `node --env-file=.env.local scripts/verify-publication.mjs`. It creates one random fixture and deletes only that fixture afterward. Environment files and the local Vercel binding are ignored by Git.

See [third-party notices](docs/THIRD_PARTY_NOTICES.md) for bundled speech assets and SDK references.
