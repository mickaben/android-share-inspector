# Android Share Inspector

Minimal Android diagnostic app that displays the raw `Intent` payload received from Android's share sheet.

It requests no network permission and does not resolve URLs or call external APIs.

## Build

GitHub Actions builds a debug APK on every push to `main` and on manual dispatch.
Download the artifact `share-inspector-debug-apk` from the latest `Build APK` workflow run.

## Test

1. Install the APK.
2. In Google Maps, open a place.
3. Tap Share.
4. Choose Share Inspector.
5. Tap Copy report.
6. Paste the report for analysis.
