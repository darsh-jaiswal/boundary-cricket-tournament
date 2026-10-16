# Boundary Android 1.0.1

- Reduced the signed arm64 APK from 122,660,737 to 89,814,457 bytes by compressing native libraries; offline speech and speaker models remain included.
- Added a direct static download from the production website, plus a GitHub alternative.
- Website deployment checks the release byte count and SHA-256 before exposing the APK. Downloads use an explicit APK MIME type and attachment filename.
- Uses the same release certificate and a higher version code, so it can update version 1.0 without uninstalling.

If your previous phone download is still pending, cancel that pending download first, then open the new link in Chrome. Allow installation from that browser when Android asks. Keep adequate free space for the APK and extracted native libraries.

The original release completed and matched its checksum in a full download check; the phone-specific cause of the finalization stall was not reproduced. This update provides a smaller package and an alternative delivery path.
