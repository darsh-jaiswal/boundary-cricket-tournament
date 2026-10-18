# Boundary Android 1.0.7

Players sign in with Google through Clerk and complete Boundary player registration. Teams can add registered players by their Google email or Boundary player code. The server checks Google identity and registration before accepting player lookup and new-match squads. Existing player IDs, saved teams and match history are preserved; legacy players must complete Google registration before new matches.

Phone verification and the local profile phone field are removed. Community and shop authentication use Google. New orders use the account email for communication. The checkout page explains disabled checkout; production sales, inventory and delivery remain unconfigured, so this release does not enable real payments.

Version code 8 uses the existing release certificate, supporting updates over the signed 1.0.6 APK.

Validation: signed release build and 95 Android unit tests; six JavaScript identity tests; migration tested on temporary database tables, including legacy preservation, required Google identity fields and duplicate-email rejection. Independent Java and JavaScript review completed. Full lint reports existing issues outside this change. Real Google OAuth completion and physical-phone testing are not claimed.
