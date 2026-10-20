# Players, invitations and workspaces

## Google registration and usernames

1. Open Teams → Add registered player → Continue with Google.
2. In the browser, sign in with Google and choose your cricket display name and unique username.
   Usernames have 3–24 letters, digits or underscores, start with a letter, and are case-insensitive.
   The `user_` prefix is reserved for account codes. Once saved, a username is permanent.
3. Accept the terms and register. Approve **Link my Boundary app** only for your own device request.
4. Return to the app. It refreshes the link automatically; Refresh account status is available as a fallback.
5. Search another registered player's exact `@username`, confirm their name, and add them to your team.
   Existing exact Google-email and player-code lookups continue to work. Existing player IDs and match history are preserved.

Players registered before usernames were introduced can open Manage username / Google account
and choose a handle. Their existing squads remain linked to the same account ID.
Returning registered players see their saved username as read-only and can update their display name.
Saving shows progress and success/errors next to the action button.

## Logout

Android Logout immediately removes the active local account credentials and returns to the
Google welcome screen, even when offline. Remote device-token revocation is queued privately
and retried when the app can reach the server; queued tokens cannot authenticate the app.
It opens `/logout.html` in the browser to end its Boundary/Clerk session. The browser
confirms sign-out or offers retry on failure. It does not sign you out of Google's other services.
The registration page also has a separate **Sign out of this browser** button; that browser action
does not revoke another device's app token. Logout keeps player registration, permanent username,
saved teams, tournaments and match history.

## WhatsApp invitations

In a team's Add registered player screen, choose **Share via WhatsApp**, Share, Copy link,
or Team QR. The organizer must have a linked Google registration. A team link lasts seven days.
WhatsApp opens a draft; choose the recipient and send it yourself. If WhatsApp is absent,
Android offers other sharing apps. The recipient opens the link, signs in with Google,
registers a permanent username if needed, and requests to join. The organizer refreshes
Join requests and confirms Add player. Accepted requests can be imported again on this device
if its local squad was reset. Duplicate account IDs cannot be added twice to the same team.
Exact username search remains available for directly adding an already registered player.
Invitations and requests live in Neon; the resulting squad remains in the organizer's SQLite.
This flow does not transfer a team database or provide full squad synchronization.

## First launch, updates and refresh

First launch shows Continue with Google and Explore as a guest. Google sign-in opens the
browser; approve Link my Boundary app for this device, then return. Phone login is not used.
Local profile preferences and photos are isolated by linked account and hidden after logout.

From Android 1.0.11 onward, the foreground app checks for a newer signed release on resume
and every minute. An update banner opens a Later / Update choice; Profile also has Check
for updates. Install 1.0.11 once to enable this feature on older installations.

Drag down when already at the top of Home, My Cricket or Store to refresh. Store reloads
the online catalog; the main screens reload saved data and check for updates. The player
account screen refreshes its link status, and the team invitation screen reloads join requests.
Pull-to-refresh does not install an APK or replace an active scoring journal.

## Workspace modes

| Workspace | Purpose | Can change local match data? |
| --- | --- | --- |
| Organizer | Set up teams, matches, fixtures and tournaments | Yes |
| Scorer | Ball-by-ball scoring, wickets, extras, corrections and innings changes | Yes |
| Umpire | Official scoring workspace plus the umpire-tools shortcut and official voice profile | Yes |
| Player | Linked Google player profile, connections and viewing matches | No |
| Spectator | View match scores and scorecards | No |

Manager and Captain were labels with the same behaviour as Organizer. They are removed;
saved selections migrate to Organizer. These are local workspace modes, not account-based
remote authorization. The three editing modes share editing access on the device.

## Full scorer test

Create two teams and add registered players by username. Create a match, record the toss,
set format/rules and players per side, choose each playing squad, then choose the two openers.
The server validates each squad's Google registrations before the match starts.
Choose a bowler; score runs, extras and dismissals. On wickets select the incoming batter;
at over boundaries select the next bowler. Use Undo/History for corrections.
End innings or reach its limit, start the chase with its openers and bowler, then score to completion.
Check scorecard, result, graphs and player figures. Scoring is stored in SQLite;
online squad validation and optional publication use the server.

## Razorpay checkout

The existing integration uses Razorpay Orders, server-side signature checks and payment reconciliation.
The payment button needs Google login, available inventory and an open store.
Live keys alone do not open the store: the owner must configure sellable variant stock/prices,
`MERCH_SALES_ENABLED=true`, `MERCH_SHIPPING_PAISE`, `MERCH_DISPATCH_DAYS` and `MERCH_SUPPORT_EMAIL`.
Use `scripts/configure-merchandise.mjs` with an owner-supplied inventory file.
The owner-confirmed live launch is recorded in [store launch configuration](../database/inventory/README.md).
Production uses the confirmed stock, ₹50 shipping, 3–5 day dispatch and the owner's support email.
The disabled seeds remain development examples. Stock files are never reapplied by deployment.
