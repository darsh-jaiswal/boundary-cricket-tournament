# Boundary Android 1.0.14

Mandatory Google sign-in opens the official Clerk hosted portal. New players choose a display name and unique username; usernames can now be edited. A short-lived single-use code returns the authenticated identity to the requesting Android installation. Guest access is removed.

The shop uses full-screen product details with size selection, Buy now and Add to cart. Native checkout provides cart editing, account-scoped Indian delivery address, price details and Pay through the official Razorpay Android SDK. The server creates and verifies orders. Changed carts require cancellation of any previously saved unpaid order before checkout.

Managers search registered players by username and add the confirmed profile. Private invitations open Google sign-in, registration if needed, and explicit Join team. The manager imports server membership into the local squad, deduplicated by account ID.

Reference evidence: REA analysis of the supplied CricHeroes 26.10.1 APK, AddPlayerOptionsActivityKt.bindActions, confirms separate search/contact and WhatsApp/share/copy/QR invitation actions. Boundary adapts registered-player search to usernames. This is static APK evidence, not proof of CricHeroes backend behavior or full visual parity.

Validation: JavaScript behavioral tests and Android release/unit build. Real Google account completion and a live payment charge require user testing; no live charge is performed by automation.
