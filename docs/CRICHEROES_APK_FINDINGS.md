# Supplied APK investigation

## Correct target acquired through Aptoide

After the user authorized using Aptoide, the current CricHeroes APK was acquired
directly from Aptoide's [CricHeroes listing](https://cricheroes.en.aptoide.com/app),
separately from the initially supplied store installer.

| Field | Verified value |
| --- | --- |
| Local artifact | `references/cricheroes/analysis/CricHeroes-26.10.1.apk` |
| Package | `com.cricheroes.cricheroes.alpha` |
| Version / code | `26.10.1` / `496` |
| File size | 150,742,478 bytes |
| SHA-256 | `e3c42e5fd8a1aef3894df52aa0a050e3baa778818d63019d17f1451d167e96a4` |
| Aptoide MD5 matched | `b73bd1a5c7b745cf95e912ac150271c8` |
| Signature validation | Android SDK `apksigner verify --verbose --print-certs` exited 0; v3 and SourceStamp verified |
| Signer subject | `CN=Abhishek Desai, OU=CricHeroes, O=CricHeroes, L=Ahmedabad, ST=Gujarat, C=IN` |
| Signer SHA-256 | `074e118aa4ec3c96932433257c5bb6e5e907c93b2ed76cb4a10c79de9ab9a023` |
| REA parsed classes / resources | 74,569 / 6,161 |
| REA package Evidence ID | `ev_290356a719a795042f4e19e73a050a06e1f8730399248e0562062d2b5d299974` |

The metadata certificate SHA-1 matches the signer reported by apksigner. This is
local cryptographic validation and agreement with Aptoide metadata, not an
independent comparison against a device-installed Play Store build.

REA succeeded with `REA_JADX_HEAP_MIB=3072`,
`REA_JADX_ACTIVE_PROCESSOR_COUNT=2`, and
`REA_JADX_OPERATION_TIMEOUT_MS=300000`. The 1536 MiB attempt ran out of Java heap;
the initial default attempt failed with unreadable JVM output. Save and reuse
`cricheroes-package-3gb.json`; do not repeat the package overview unnecessarily.

The manifest declares candidate components for scoring, toss, squad selection,
scorecard editing, tournament scheduling, profiles, insights, wagon wheels,
streaming, ground/academy directories, looking-for posts, attendance and NRR.
Declarations identify investigation entry points; they do not establish each
screen's actual runtime behavior or completeness.

REA also inspected
`com.cricheroes.cricheroes.tournament.profile.CalculateNRRActivityKt`:
Evidence `ev_36aed559a9d38d8e43a876e54ab3d8276f52dd1f6a25eb4e03e403da18506876`.
Its method inventory includes `generateNrrCalculation`, `validate`, `shareView`
and `shareBitmap`.

Focused REA method inspection of `generateNrrCalculation` succeeded:
Evidence `ev_ffd68c224b5695602224bbadaaaa5a72a753898b1af736fd768de8c65ea91add`,
body status available, representation Java, source status complete. The method
collects tournament/team/opponent, desired position, batting order, runs and
overs, then makes an authenticated `generateNrr` API request. It displays the
returned `statement` and enables sharing according to `is_share` in that
response. This verifies a server-dependent calculation flow in static code;
the server's qualification algorithm and runtime response are unobserved.
No CricHeroes account or API was used, and no server request was replayed.

## New Boundary NRR planner

Tournaments -> Registrations & schedule -> NRR scenario planner.
The original Boundary implementation uses existing tournament totals and a
hypothetical innings pair to show current NRR, projected NRR and a comparison
with a rival's current NRR. Supports 4/5/6/8 balls per over, all-out allocation
instructions, sharing and input validation. It does not change saved scores or
claim full qualification simulation or CricHeroes algorithm parity.

Validation: `gradlew.bat :app:assembleDebug` passed with the planner and navigation
included. Device behavior is not yet verified. No tests were run.

## Match graphs implementation

Added Match -> Match graphs, with per-innings Manhattan bars (runs per over),
cumulative worm lines, wicket markers, accessible over summaries, refresh and
text sharing. The effective journal replay now exposes immutable progress
snapshots through `Innings.progress`, using the same replay as the scorecard.
Reversed deliveries and retained deliveries after closure are excluded. Extras
can add runs at the same legal-ball coordinate; over buckets use legal balls
before the delivery. A chase retained while innings one reopens is excluded.

REA class search for `Manhattan` returned no class-name matches among 74,569
parsed classes (Evidence `ev_85be203efbd79cafa11f603b06b5ee40d83bd7fb80e7d205b08d5ef2b389a24c`).
This is only a bounded negative name search, not evidence the feature is absent.
The broader `Graph` search found CricHeroes graph models for runs per over,
boundaries, extras, wickets, partnerships and insights (Evidence
`ev_9447307f18b34323fec83c765f8cd30b5c7de7ee52348ad6b11bdf1c5d6caf37`).
Boundary's graphs are an original implementation; target rendering and algorithms
have not been compared at runtime.

Validation: debug APK assembly and the existing `CricketEngineTest` suite passed
after the replay extraction. Java review found no concrete replay or chart bugs.
No new tests were added; no Android device acceptance run has been performed.

## Target identity: mismatch

REA inspected the user-supplied `cricheroes (1).apk` on 17 October 2026.

| Field | Observed value |
| --- | --- |
| File size | 20,072,145 bytes |
| SHA-256 | `188bbdb899be2da401ac0471ba4d10693612fa1067f2ea0987cc6e1afcfd4374` |
| Package | `cm.aptoide.pt` |
| Version | `9.22.5.3` |
| Version code | `12060` |
| Minimum / target SDK | 16 / 32 |
| Parsed classes / resources | 12,333 / 4,089 |
| Provider | JADX headless 0.7.1 through REA 6.5.0 |
| REA Evidence ID | `ev_83c0227577e015be9a0e964b3365da0fb5a8bee3ed9db8026bd7f9f6b32e0935` |

The package identifies Aptoide. The filename does not establish CricHeroes
identity. This APK cannot support conclusions about CricHeroes features.
No target code was executed, and no signature authenticity check was performed.

Complete local evidence is stored at
`references/cricheroes/analysis/package.json`. It and the input APK are excluded
from source control; references are excluded from Vercel uploads.

## Questions and status

| Question | Status |
| --- | --- |
| Is the initially supplied artifact CricHeroes? | No: manifest package is `cm.aptoide.pt`; the correct APK was subsequently acquired separately. |
| Can REA and JADX inspect an APK on this host? | Yes: inspection completed and returned artifact-bound evidence. |
| What are CricHeroes' actual screens, client flows and algorithms? | Partial: manifest entry points and NRR class members inspected; runtime and broader method tracing remain. |
| Can the entire CricHeroes backend be reconstructed from this file? | No evidence: it is a different app; client analysis alone would not establish backend implementation. |
| What useful implementation continued? | Boundary broadcast overlay, based on the existing score publication contract and prior public-feature audit. |

## Continue the investigation

Use the newly acquired CricHeroes APK for static analysis. A device recording
or connected device can corroborate runtime navigation when useful; another
upload is no longer required for static inspection.

Use REA `inspect-android-package` first, then focused `search-android-classes`,
`inspect-android-class`, `inspect-android-method` and `trace-android-references`
for match setup, scoring, tournaments, player profiles, discovery and streaming.
Record which behavior is observed, inferred or unresolved. Reimplement features
in Boundary's own code and branding.

## Boundary broadcast overlay

- Web entry: `/overlay.html?match=<published-match-id>`.
- Android entry: Tools -> Publish match -> Share broadcast overlay.
- Designed for an OBS browser source at 1280 x 720, with a transparent background.
- Reads the existing public score API every three seconds, including in a hidden
  browser source. It does not receive scoring authority or private recovery data.
- Shows score, overs, chase requirements, result and scorer publication age.
- Keeps the previous score visible when updates fail, with an interruption label.
- This adds the scoreboard graphic. It does not implement video ingestion,
  hosting or a streaming service.
- Deployment and device acceptance are pending. The production overlay URL will
  work only after the updated web assets are deployed.
- Validation: debug APK assembly and JavaScript syntax check succeeded. Java and
  JavaScript review completed; the overlay now clears withdrawn public scores.
  No device/browser acceptance tests were run.
