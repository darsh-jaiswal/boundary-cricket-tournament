# Shared community status

Clerk resource `clerk-citron-coin` was provisioned through the Vercel Marketplace
and connected to `boundary-cricket-tournament`. The existing Neon database is
retained. Secrets remain in Vercel environment variables and ignored `.env.local`.

`002_community.sql` was applied successfully through the migration runner.
Community posts and replies persist in PostgreSQL. Public reads are available;
writes require a verified Clerk session. The server checks author ownership for
post removal and serializes each user's writes before checking posting limits.

Web entry is `/community.html`; Android Home links to it. Categories include
players, opponents, umpires, scorers, grounds, coaches and cricket updates.
City/text search filters the latest 100 posts. Replies show the first 100 records.
Posts/replies are public; this feature does not implement private direct messages,
payments, booking confirmation, distributed match authority or native account sync.

JavaScript syntax checks and Android debug assembly passed. Reviewer findings
about concurrency caps and auth-error visibility were addressed. Database migration
was successful. First-user sign-in and authenticated posting require acceptance
verification; provisioning is not evidence those flows work end to end.

The native camera publisher, rich profiles, match awards and local availability
screens compile in the APK. Streaming delivery, native screen acceptance and
full CricHeroes UI parity are still outstanding.

Deployment: production `https://boundary-cricket-tournament.vercel.app/community.html`
is ready (deployment `dpl_FQDYQMFfTehjmXUcwidu8wi6DcLo`). The public posts API
returned HTTP 200 and an empty array; publishable-key config was present. Chrome
rendered the Sign in / join control and Clerk email/Google sign-in form.
The form explicitly reports Development mode and the generated application name
`clerk-citron-coin`. Production Clerk activation/branding and first-user
authenticated posting are outstanding. No account was created during this pass.
The initial community deployment served the previous 1.0.1 APK. The 1.0.2 release
packages the Android additions; see RELEASE_1.0.2.md and web/android-release.json
for the current release metadata.
