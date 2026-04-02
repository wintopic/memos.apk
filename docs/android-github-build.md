# Build APK On GitHub

If you do not have a local Android toolchain, you can build an installable APK on GitHub Actions.

## What It Produces

- The workflow builds a debug APK.
- Debug APKs are signed with the default debug keystore, so they can be installed directly on devices or emulators.
- This is suitable for testing and private use.
- It is not suitable for app store release.

## How To Run It

1. Push your branch to GitHub.
2. Open the repository on GitHub.
3. Go to **Actions**.
4. Open **Build Android APK**.
5. Click **Run workflow**.
6. Wait for the job to finish.
7. Download the artifact named `memos-android-debug-apk`.

The downloaded file is:

- `memos-android-debug.apk`

## Notes

- The workflow first builds the web frontend.
- It then generates the Go mobile AAR with a pinned `golang.org/x/mobile` version that still includes `bind`.
- Finally it builds the Android app with Gradle.

## Release APK

If you need a signed release APK or AAB, add a signing config and GitHub secrets later. The current workflow is intentionally kept to the installable debug APK path.
