# Players, invitations and workspaces

## Google registration and usernames

1. Open Teams → Add registered player → Continue with Google.
2. In the browser, sign in with Google and choose your cricket display name and unique username.
   Usernames have 3–24 letters, digits or underscores, start with a letter, and are case-insensitive.
   The `user_` prefix is reserved for account codes.
3. Accept the terms and register. Approve **Link my Boundary app** only for your own device request.
4. Return to the app. It refreshes the link automatically; Refresh account status is available as a fallback.
5. Search another registered player's exact `@username`, confirm their name, and add them to your team.
   Existing exact Google-email and player-code lookups continue to work. Existing player IDs and match history are preserved.

Players registered before usernames were introduced can open Manage username / Google account
and choose a handle. Their existing squads remain linked to the same account ID.

## WhatsApp invitations

In a team's Add registered player screen, choose **Invite player on WhatsApp**.
WhatsApp opens a draft containing the team name and a Google-registration link.
Choose the recipient and send it yourself. If WhatsApp is absent, Android offers other sharing apps.
The recipient registers and uses **Share my username on WhatsApp** to send their handle back.
The organizer then finds that handle and confirms Add player. Teams are saved locally;
the invitation does not silently join a remote team or transfer a team database.

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
