# Boundary — cricket tournaments and live scoring

Native Android application written in Java for local, college, turf and club cricket. SQLite is the source of truth on the scoring device. A Vercel API backed by Neon publishes score snapshots and stores authenticated, encrypted private journals to the public spectator site.

- Spectator site: https://boundary-cricket-tournament.vercel.app/
- Source: https://github.com/darsh-jaiswal/boundary-cricket-tournament
- [Download the signed Android APK](https://boundary-cricket-tournament.vercel.app/downloads/Boundary-Android-arm64.apk?v=1.0.11) for a modern physical Android phone.
- Emulator: `app/build/outputs/apk/debug/app-x86_64-debug.apk`.
- Universal build: `app/build/outputs/apk/debug/app-universal-debug.apk`.

## First use

Version 1.0.11 adds pull-to-refresh, reliable logout, Google welcome, team invitation requests and future update prompts. See [1.0.11 notes](docs/RELEASE_1.0.11.md), [player and workspace flows](docs/PLAYER_AND_WORKSPACE_FLOWS.md), and [Neon versus SQLite storage](docs/DATABASE_PRODUCTION.md).

The owner-confirmed shop launch uses live Razorpay payments, ₹50 shipping and 3–5 day dispatch.
See [launch stock and store settings](database/inventory/README.md). Customers must sign in with Google to check out.

Version 1.0.5 introduced Google-registered squad eligibility, comparison charts, the merchandise catalog and Razorpay checkout infrastructure, and Terms and Privacy pages. It originally kept the shop in preview; the owner-confirmed launch settings above supersede that status. Google login is enabled in Clerk; each player must complete Boundary registration before joining a new squad. See [1.0.5 notes](docs/RELEASE_1.0.5.md).

Version 1.0.4 adds reversible innings endings, wicket and extras corrections, bowler changes, five match-format presets, and repeatable Super Overs after a tie. These rules apply offline and flow into scorecards, tournament results, and public match pages. Version 1.0.3 added toss, playing squad and opener setup, official match controls, and push-to-talk voice scoring. Version 1.0.2 added player profiles and local rankings, match graphs and awards,
NRR scenarios, squad availability, a dark navigation shell, and RTMP camera
publishing. The web community is at `/community.html` and the OBS score graphic
is at `/overlay.html?match=<id>`. See [release notes](docs/RELEASE_1.0.4.md) for
verification limits and remaining features. Shop, merch, ads and paywalls are
excluded; complete CricHeroes parity has not been reached.

1. Open **My phone account**, register a phone-verified player and link the Android app. In **Teams**, save two teams and use **Add player by phone** for their registered squads.
2. Press **Create a match** on Home. There is no PIN. New playing squads require an online registration check; scoring continues offline afterward.
3. Select both teams, ground, scorer, umpire, toss winner and bat/bowl choice. Set the match rules, choose each playing squad, then select the opening batters.
4. Select a bowler. Tap run buttons to save immediately. Hold a run button to add scoring zones, running runs and overthrows. Extras and wickets have detailed forms.
5. Use **History** to correct any recorded event, including earlier innings. Later deliveries remain in the journal and are replayed. Events after a newly calculated innings finish are retained but excluded until the innings reopens.
6. Save your private match recovery code from Tools if you need to recover or transfer scoring. Spectators only receive the public scoreboard. At innings completion start the chase. The chase target follows first-innings corrections; explicit interruption targets remain official events. Tied matches can continue through repeat Super Overs. Rules lock after the first ball so saved deliveries cannot be reinterpreted.
7. In the Scorer or Umpire match workspace, use **Voice scoring · push to talk**. Hold the microphone, speak and release. Confirm the recognized command before it changes the score. **Tools** also contains optional voice enrollment, PA audio, field radar, scanner, stump cam, publication status and workspace switching.

## Features

| Area | Current behavior |
| --- | --- |
| Roles | Seven device workspaces; spectators/players browse read-only. Device editing follows the selected workspace without a PIN. New squads require registered, verified-phone accounts. Public API reads need no login. Each published match has a device-owned write key. |
| Tournaments | Team registration, league round robin, two groups with semifinals/final, knockout brackets with byes and automatic round advancement. Ties require a decided replay before knockout advancement. Points and NRR derive from completed scorecards. |
| Rules | 4/5/6/8 balls per over, squad size, overs, wide/no-ball penalties, last-batter rules, bowling limit, powerplay length and final-over extra legality. |
| Scoring | Bat runs, wides, no-balls, byes, leg byes, penalty runs, completed runs, overthrows, dismissal types, fielder attribution, incoming batter, retirement and return, batter-end changes, bowling changes, scorer notes and manual innings end. |
| Corrections | Replayed immutable delivery identities, retained reversed events, strike/end-over restoration, bowler figures, fall of wickets, incoming batter restoration, free-hit memory, run-out crossing, retirement reversal and innings reopening. Major reversals have a three-second cancellation window and spoken warning. |
| Offline | Transactional SQLite records and persistent publication outbox. Immediate foreground publication plus Android JobScheduler retries when connectivity returns and restores scheduled work after reboot. |
| Spectators | Real database snapshots, public match directory, score/target/win estimate, commentary and result. Browser refresh every three seconds; publishing depends on Android scheduling and network conditions. |
| Voice | Push-to-talk scoring via the bundled open source Vosk Indian English model. Optional three-sample speaker enrollment checks recordings of at least three seconds. Every command requires explicit confirmation. |
| PA | Android text-to-speech routes through the selected system audio output; boundaries, wickets, incoming players and over changes are announced. |
| Insights | Per-ball statistical win estimate, deterministic commentary/report, history-based scoring zones, tactical prompt and nine draggable fielders with powerplay-aware initial positions. |
| Scanner | Bundled on-device ML Kit OCR, editable text, row guesses, reviewed scorecard import and career-stat integration. |
| Replay | CameraX rolling four-second segments, two completed segments plus the active segment, latest-five-second export with keyframe padding, 0.25× playback, paired same-Wi-Fi review page and remote review request. |
| Interruptions | Official enters revised remaining balls. A local resource curve uses remaining balls and wickets; score, target and estimate update through a journal event. |

## Accuracy and deployment boundaries

Speaker verification is probabilistic. The 0.65 cosine threshold is a starting setting, not a ground-validated security guarantee. A crowded ground can contain overlapping speakers, replayed audio or very short utterances. Commands still require official confirmation. Do not advertise perfect unauthorized-voice rejection.

The win model, captain suggestions, commentary and match narrative are local algorithms; no paid generative AI service is configured. The interruption curve is a local estimate, not licensed official DLS. Handwritten OCR must be reviewed. Camera replay is an official visual review aid: it does not automatically detect the exact foot or bail event. The camera screen must stay open; paired replay phones must share Wi-Fi. Cache segments are bounded temporary files rather than an exact eight-second RAM buffer, and segment transitions may have gaps.

Roles are local to the scoring device, not a distributed account/organization service. Scorer and umpire assignments are recorded names. There is no email login or distributed organization account service. Scoring authority can be transferred using the private match recovery code. Public snapshots are published alongside a separately encrypted complete match journal. Tools → Recover full cloud match requires the private recovery code. Recovery restores rules, all deliveries and both squads. Keep that code private; it grants scoring authority. Voice enrollment remains on this phone and is excluded from OS backup.

The published APK uses the project's private release signing certificate for direct installation. It is not published on the Play Store. The local `signing/` folder contains the keystore and its password properties and is excluded from Git. Back up that folder securely: future updates require the same certificate. If an earlier debug build is installed, export its private recovery code before uninstalling it to install the release build; Android does not allow replacement across different certificates.

## Architecture

`domain/Cricket.java` has framework-free rules, events, replay, tournament progression, standings and statistical estimates. `domain/VoiceCommands.java` parses commands. `data/Repository.java` persists versioned Java records in SQLite and queues snapshots. `Publication.java` schedules authorized HTTPS publication. Activities and `FieldView` implement the Android UI. `api/matches.js` atomically checks match ownership and revisions in Postgres. `web/index.html` renders public data using text nodes.

Schema migration is intentionally explicit: the early SharedPreferences starter data is preserved separately and is not silently imported into the new match journal. Java record classes declare serial version 1; future incompatible changes require a migration.

## Build and check

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
npm ci
vercel deploy --prod --yes
```

Device tests require an emulator or connected phone. The publication verification script needs a development `DATABASE_URL`; obtain it using `vercel env pull .env.local --yes`, then run `node --env-file=.env.local scripts/verify-publication.mjs`. It creates one random fixture and deletes only that fixture afterward. Environment files and the local Vercel binding are ignored by Git.

Release signing loads `signing/release.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Without that local file, release builds are unsigned. Signed phone output is `app/build/outputs/apk/release/app-arm64-v8a-release.apk`.

See [third-party notices](docs/THIRD_PARTY_NOTICES.md) for bundled speech assets and SDK references.
