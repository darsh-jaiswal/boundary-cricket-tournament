# Boundary Android 1.0.9

Player registration now displays progress and success/errors beside its action. Returning players
see their saved identity and **Save display name** instead of another registration prompt.
Usernames become permanent after their first successful save, enforced by the API and a database
trigger. Existing usernames and player IDs are retained; legacy accounts can choose a username once.

Android Logout revokes the device token, clears the local account link, returns Home in Spectator
mode and opens the browser sign-out flow. Revocation errors are reported instead of hidden.
The browser ends its Boundary session and verifies sign-out; failures offer retry. Logout completion
survives Activity recreation. Saved teams/matches and player registrations are retained.

The player page also offers browser-only sign-out. This does not log out Google's other services.
No payment configuration, inventory or scoring rules change in this release.

Validation: signed debug/release build and 95 Android unit tests; 16 JavaScript/database
checks, including immutable-username migration against temporary tables and logout failure/retry.
Three emulator tests cover local identity teardown, Activity recreation and interrupted-process
recovery, with saved-team retention. A 360px browser check exercised registration, display-name
updates, locked usernames, visible API errors, both sign-out routes and hidden signed-out controls
using a mocked identity provider. Independent Java/JavaScript review completed. Real Clerk
browser logout and the complete phone-to-browser flow still need physical-device acceptance.
