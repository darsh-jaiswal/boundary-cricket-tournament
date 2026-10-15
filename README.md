# Boundary — local cricket tournament companion

An Android Java prototype for local, college, turf and club cricket. It provides role navigation, a live scoring pad, offline ball-event storage, competition and insight screens, and entry points for voice scoring, scorebook scanning, stump cam review and stadium announcements.

## Build

Open this project in Android Studio or run `gradlew.bat :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Current scope

Ball events and selected basic rules are stored on-device with SharedPreferences. The scoring model is a starter implementation, not a complete laws-of-cricket engine: batsman/bowler attribution, full innings progression, historical event replay, tournament calculations and DLS need a normalized match model and rule-specific verification. Speech uses Android recognition with explicit confirmation; it does not perform speaker identification. OCR, cloud sync, live fan broadcasts, trained win probabilities, CameraX rolling replay and audio announcements need integrations that are not bundled. UI cards identify these limits rather than claiming connected services.

The `web/` directory is a static spectator scoreboard shell suitable for Vercel. It uses sample data and is not connected to the Android app or a live backend.
