# Boundary Android 1.0.8

Google players choose a unique public username for exact player lookup. Existing account IDs,
team memberships and saved scorecards remain intact. The registration page restores an existing
profile so returning players can link a device without registering again.

Team player selection includes WhatsApp invitations to Google registration. Recipients share their
username back; organizers search and confirm Add player. Sending remains under the user's control.

The profile drawer gives Connections and QR actions a full-width row. The player account screen
uses the shared palette, a single Google registration entry point and automatic account-link refresh
on browser return. Expired sessions can reconnect. Duplicate Manager/Captain workspaces migrate
to Organizer; the remaining roles include descriptions.

Razorpay integration is retained. Live sales still require actual stock and owner-provided shipping,
dispatch and support configuration. See [workflow guide](PLAYER_AND_WORKSPACE_FLOWS.md).

Validation: signed debug/release builds; 95 Android unit tests; ten JavaScript/database/UI-regression
tests, including migration checks against temporary tables; two emulator UI tests at 360dp and
320dp widths (the latter with 1.3× font scale). Drawer rendering was also inspected from an emulator
screenshot. Independent Java and JavaScript review completed. Real Google OAuth completion,
WhatsApp message delivery and a live money transaction remain physical-device/provider checks.
