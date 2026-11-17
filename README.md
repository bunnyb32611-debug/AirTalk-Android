# AirTalk Android prototype

AirTalk is an Android offline-messaging prototype. The current implementation contains a dark teal Compose UI and a Bluetooth Classic RFCOMM chat path. Wi-Fi Direct is not implemented yet, and this prototype has not been device-tested.

## Build without a laptop (GitHub Actions)

1. On your Android phone, open https://github.com and sign in or create an account.
2. Create a new **public** repository named `AirTalk` (public repositories allow GitHub Actions on the free plan).
3. Upload the contents of this project ZIP to the repository root. Make sure `.github/workflows/android-apk.yml` is included. If the GitHub mobile upload page hides dot-folders, use the browser's Desktop site mode.
4. Open the repository's **Actions** tab and select **Build AirTalk APK**.
5. Tap **Run workflow**. Wait for the green check mark.
6. Open the completed run and download the `AirTalk-debug-apk` artifact. Extract it and install `app-debug.apk` on both phones. Android may ask you to allow installs from that browser/file manager.

The workflow builds a debug APK; it does not sign a Play Store release. Keep the repository public only if you are comfortable sharing this prototype source publicly.

## Current limitations

- Bluetooth Classic RFCOMM only; Wi-Fi Direct is not implemented.
- Both phones should be Bluetooth-paired first. On the receiving phone, open AirTalk so its listening socket starts; then scan/connect from the sending phone.
- This is an early prototype and needs real-device testing. Do not treat it as a finished secure messenger.
