# Boundary Android 1.0.11

First launch now shows Google sign-in with an optional guest entry. Logout clears the
active Android identity immediately, returns to the welcome screen, and retries remote
revocation if offline. Profile preferences and photos are isolated by account. Google
registration and permanent usernames remain the identity flow; no phone OTP is required.

Teams have seven-day invitation links with WhatsApp, system sharing, copy and QR options.
Players sign in, register if necessary, and request to join. Organizers review and accept
requests or search an existing username directly. Duplicate registered accounts cannot
be added twice after changing display names. Migration 015 stores hashed invitations and
owner-scoped requests in Neon. Team rosters and scoring remain local SQLite records.

Pull down at the top of Home, My Cricket, Store, player account or team invitations to
refresh the relevant data. Store failures keep saved products with retry guidance.

The foreground app checks for releases on resume and every minute. Newer releases show
an optional update banner; Profile has a manual check. Update opens the validated,
version-specific APK download. Install this release once to enable future update prompts
on installations running 1.0.10 or earlier.

Validation includes Android debug/release builds and 97 unit tests; JavaScript/database
checks of authentication, invitation ownership, expiry, pagination and idempotence; mobile
browser invitation and registration checks with simulated identity; Android device checks
of welcome/logout, account isolation, update prompts and the pull-to-refresh gesture.
Browser simulation does not verify a real user's Google OAuth approval on their phone.
No payment is charged by these checks.
