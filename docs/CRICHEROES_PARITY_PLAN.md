# Boundary: CricHeroes comparison and 24-hour delivery plan

Updated 17 October 2026. This is a public-feature audit and implementation queue,
not a claim of complete app inspection or feature parity. G-stack and P-stack
workflows are excluded.

Current user scope: cricket and Pro functionality are intended to be free in
Boundary. Shop, merchandise and ads are excluded. No subscription or feature
paywall is implemented. This scope does not mean full Pro parity is complete.
The 15 supplied images were reviewed and used for the dark navigation shell,
My Cricket tabs, teal actions and quick-create menu; the images stay local.

## REA acquisition

- Repository: https://github.com/morluto/rea
- GitHub repository search sorted by stars returned this project first, with
  297,594 stars at acquisition. Counts change.
- Source: `C:\Users\darsh\Documents\Codex\tools\rea`
- Commit: `4a076f368c62bdfb19415accad342e03a38d1005`
- CLI: `C:\Users\darsh\Documents\Codex\tools\rea-runtime\node_modules\.bin\rea.cmd`
- Installed package: `rea-agents@6.5.0`. CLI help runs successfully.
- REA's current Android guide supports static APK inspection on Windows x64
  using a full JDK and a separately supplied JADX engine.
- No device was listed by the Android SDK's `adb devices -l`. The supplied
  `cricheroes (1).apk` was inspected successfully but identifies as Aptoide
  (`cm.aptoide.pt`, version `9.22.5.3`). See [APK findings](CRICHEROES_APK_FINDINGS.md).
  The correct CricHeroes 26.10.1 artifact was then acquired directly through
  Aptoide and successfully inspected by REA. See the findings for digests and
  cryptographic signature verification.

## Feature comparison

The Boundary column is based on source inspection, not a device acceptance run.
Existing features require regression verification before release.

| Feature family | Public CricHeroes evidence | Boundary source / next action |
| --- | --- | --- |
| Scoring and corrections | [Official feature overview](https://blog.cricheroes.com/10-unique-cricheroes-features-that-will-blow-your-mind/) | Event journal, extras, wickets, undo and correction already implemented. |
| Tournament management | [Official league overview](https://cricheroes.com/global) | League, groups, knockout, registrations, fixtures and standings already implemented. |
| Player performance | [Official PRO overview](https://blog.cricheroes.com/cricheroes-pro-just-got-better/) | Career totals already present; richer profiles, awards and breakdowns need work. |
| Match graphs | Public feature audit plus APK graph-model inventory | Native Manhattan and worm graphs, wicket markers and accessible over summaries implemented; device acceptance pending. |
| Tournament leaderboards | Official PRO overview above | Batting and bowling boards added in this change; includes live figures and share text. Fielding and MVP remain outstanding. |
| NRR planning | Official feature overview above, now a confirmed APK class entry | Team NRR scenario planner implemented; full qualification simulation outstanding. |
| Streaming and broadcast overlays | Official feature overview above | Public spectator scores and a new OBS browser scoreboard overlay exist in source. Overlay deployment pending; video streaming outstanding. |
| Automatic highlights | Official feature overview above | Local rolling replay exists; automated highlight production outstanding. |
| Player/official discovery | Official feature overview above | Distributed directory and discovery outstanding. |
| Challenges and achievements | Official feature overview above | Outstanding. |
| Watch and lock-screen experiences | Official feature overview above | Outstanding; platform-specific scope must be selected. |
| Accounts and shared authority | [Official FAQ](https://cricheroes.com/faq?colorcode=0e4686) is a behavior investigation entry point | Boundary currently uses device roles, official PIN and match recovery keys. Shared accounts/organization permissions outstanding; exact CricHeroes flows unobserved. |

This is not an exhaustive inventory. Record observed screens, actions, edge cases,
account requirements and current app version before labeling any row parity.

## Proposed delivery order within 24 hours

| Time allocation | Deliverable |
| --- | --- |
| Hours 0-2 | Acquire REA; identify current app baseline; inspect target APK or installed app when available; maintain evidence matrix. |
| Hours 2-8 | Close tournament and player-stat gaps: leaderboards, performance breakdowns, sharing and match discovery. |
| Hours 8-14 | Select and implement differentiators supported by the current architecture; candidate features are qualification scenarios, richer score export and broadcast overlay. |
| Hours 14-20 | Exercise complete organizer, scorer, player and spectator journeys; handle correction, offline and recovery edge cases. |
| Hours 20-24 | Package signed APK, check upgrade/install and download, produce final feature/status list. |

Full parity across streaming, accounts, commerce, community and platform integrations
cannot responsibly be promised in one day. Reorder this queue around the user's
deadline priority; do not replace unfinished features with nonfunctional controls.

## First implemented gap: leaderboards

Open Tournaments, select a tournament's Registrations & schedule action, then
choose Batting leaderboard or Bowling leaderboard. Each report can be shared.

- Aggregation uses the tournament ID and team plus player name, so same-named
  players on different teams are not merged.
- Derived state excludes reversed deliveries and deliveries retained after closure.
- A retained chase is excluded if first-innings correction reopens innings one.
- Bowling economy uses each match's configured balls per over.
- Tables include live matches and explicitly say so. No official CricHeroes MVP
  formula, geographical ranking or cross-device player identity is claimed.
- No persistent database or serialized match schema was changed.
- Validation: `gradlew.bat :app:assembleDebug` succeeded. Independent Java review
  identified the retained-chase edge case above, which was corrected before the
  successful build. Device behavior has not been exercised. No tests were run.
- A pre-existing accidental `codex` prefix before the XML declaration in
  `activity_main.xml` prevented resource compilation; that prefix was removed.

## Remaining evidence needed

The correct APK is available locally and REA inspection works. Continue focused
method tracing; corroborate interactive behavior through a device when needed.
Retain original source and branding for Boundary's implementation.
