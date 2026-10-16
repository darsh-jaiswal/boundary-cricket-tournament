# Database choice and production operation

## Decision

Keep Neon PostgreSQL for cloud persistence and SQLite for Android offline scoring.
The existing provisioned database is reused; this change does not add a provider,
create a replacement database, or enable a paid plan.

PostgreSQL supports the relational tournament/account model we will need as the
app grows. Current cloud storage remains match snapshots plus encrypted journals;
teams, tournaments and player registration are still primarily device records.
Shared accounts and server-enforced organization roles remain separate work.

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
