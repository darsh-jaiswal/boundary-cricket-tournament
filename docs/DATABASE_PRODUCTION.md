# Database choice and production operation

## Decision

Keep Neon PostgreSQL for cloud persistence and SQLite for Android offline scoring.
The existing provisioned database is reused; this change does not add a provider,
create a replacement database, or enable a paid plan.

PostgreSQL supports the relational tournament/account model we will need as the
app grows. Cloud storage includes published match snapshots, encrypted journals,
Google player registrations, public usernames, device links, community data and shop data.
Team squads, tournaments and scoring journals remain primarily device records.
Shared accounts and server-enforced organization roles remain separate work.

## What is stored where

| Location | Data |
| --- | --- |
| SQLite (`boundary.db`) inside Android | Teams, tournaments, match rules, selected squads, ball-by-ball journals, player/team profile records, and pending-publication outbox |
| Neon: `cricket_matches` | Published spectator score snapshots, revision/time, hashed match authority, encrypted private match journal |
| Neon: player tables | Clerk account ID, cricket display name, permanent username, keyed Google-email hash, provider, terms version and registration time; follower relationships |
| Neon: device accounts | Hashed device token/IP, linked account ID, expiry and lookup counters |
| Neon: team invitations and join requests | Organizer account ID, local team UUID/name, hashed invitation token, seven-day expiry; requesting player ID, pending/accepted status and timestamps |
| Neon: community tables | Posts/replies, author/account ID, category/city and timestamps |
| Neon: merchandise tables | Products/variants, material/size/colour, prices, stock, reservations and order items/totals/status |
| Neon: shop orders | Customer name, delivery address and email; Razorpay order/payment/refund references and status; terms version/timestamps |
| Neon: operations | Rate-limit counters and schema-migration ledger/checksums |

Clerk manages Google sign-in/session identity; Boundary does not store Google passwords.
Card numbers, CVV and UPI PIN are handled by Razorpay, not saved by Boundary in Neon.
Legacy phone-hash fields may remain from earlier registrations; new player registration uses Google.
Profile photos/preferences and local account credentials use Android private files/preferences,
not SQLite tables. Offline voice models/recordings and replay files are also local files.

SQLite is an embedded relational database: it lives on the phone and works without a database
server. Neon hosts PostgreSQL online. The app does not switch one off when the other is available:
SQLite saves scoring locally, and selected published data is sent to Neon. Registration, squad
validation, community and checkout require the online services. Full team/tournament cloud sync
is not implemented merely by adding Neon.

## Current safeguards

- Parameterized queries and HTTPS; cloud credentials stay in server environment variables.
- Device match authority is checked before writing or recovering private journals.
- Atomic revision checks reject stale/conflicting writes.
- Database constraints reject negative revisions, invalid authority hashes, mismatched IDs and private fields in public snapshots.
- An index supports the latest-match listing without sorting the complete table.
- Reviewed SQL migrations are stored in `database/migrations/` with canonical line-ending checksums.
- Each migration runs atomically with its ledger entry under a transaction advisory lock. Changed applied migrations fail deployment.
- API requests perform data operations only; deployment runs migrations first.
- SQLite persists scoring and pending publication before cloud transfer.

## Migration procedure

Create a new numbered SQL file for each schema change. Never edit an applied file.
Use additive changes compatible with the old API because the previous deployment
continues serving requests while a new deployment builds. For breaking changes,
use expand/backfill/contract across separate deployments.

Run `node --env-file=.env.local scripts/migrate-database.mjs` locally, or let the
Vercel build run `npm run db:migrate` using its server-side DATABASE_URL. Migrations
abort on invalid existing data and do not silently delete or reset records.

## Free now, scale later

Provider quotas change; check https://neon.com/pricing and the linked Vercel
Marketplace resource before relying on exact allowances. Public direct-plan
allowances may differ from an existing Marketplace plan. No availability SLA or
unlimited usage is assumed for a free deployment.

Monitor storage, compute, transfer and query latency. Three-second spectator
polling grows database demand with audience size; introduce server-side fanout or
short-lived public caching before a large tournament launch. Keep private-journal
responses uncached. Upgrade capacity on the same PostgreSQL database when needed.

Before a public launch, configure and verify independent encrypted backups and a
restore drill, define a retention policy, and add shared identity/organization
permissions and abuse limits. These are not claimed complete by choosing Neon.
For backups use standard pg_dump/pg_restore through an appropriate direct
connection; store backups privately, outside the repository and web deployment.

## Remaining scaling work

Normalize cloud organizations, members, tournaments, teams, registrations and
player identities as shared-account workflows are introduced. Keep immutable
delivery identities and scoring revision checks. Store replay media in object
storage rather than PostgreSQL. Keep speaker recordings private on the phone.
