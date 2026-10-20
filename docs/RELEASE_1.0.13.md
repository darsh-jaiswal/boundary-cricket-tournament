# Boundary Android 1.0.13

Includes the official Google sign-in button from 1.0.12 and an automatic update prompt.
When a foreground release check finds a newer version, Boundary displays Update now
and Later without requiring a banner tap. The checks run on resume and every minute.
Later, Back or opening the download postpones the automatic reminder for that version
for 24 hours. The banner and Profile > Check for updates remain available immediately.
Automatic prompts wait while another window is focused or the scoring screen is open.

Update now opens the validated signed APK download. Android asks the user to install
it over the existing app. This is direct APK distribution, not Google Play in-app updates.
Install 1.0.13 once to enable automatic prompts for future releases; older code cannot
gain the prompt through a metadata-only server change.
