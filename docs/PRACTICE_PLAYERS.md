# Account-scoped practice squads

Practice players live in a separate table, have no Google login or public profile,
and are labelled “Practice” in their names. Only their owning organizer can list
or validate them for a scoring test. Real players still require Google registration.

An administrator can explicitly seed three players into the most recently invited
MI and CSK teams for a verified account:

```powershell
node --env-file=.env.production.local scripts/seed-practice-squads.mjs <email> <username>
```

The command checks the email and username, adds rows atomically, and is safe to
repeat without duplicating players. It does not create or alter Google identities.

In Android 1.0.15, open Teams, select the latest MI or CSK, open Add players,
and tap Refresh squad. Accepted practice rows import into the local squad.
Then start a match, select three players per side, choose opening batters and
a bowler, and score deliveries. These are test players; their names remain
labelled Practice on the resulting scorecards. The existing app may describe a
saved receipt as Google registered; practice players are simulated identities.
