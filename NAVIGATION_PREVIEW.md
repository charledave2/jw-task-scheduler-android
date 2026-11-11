# Navigation preview

This branch is an isolated, installable UI preview, never a production release.

- Package: `com.quarry.congregationscheduler.navtest`
- App name: JW Scheduler Preview
- Debug build with an independent local sample workspace and sample people.
- Production shared API and release updater are disabled in code.
- Bottom navigation: Home, Calendar, raised animated Manage, People, More.
- More includes the remaining permitted pages and existing profile controls.
- The preview workflow runs unit and emulator tests and checks installation alongside v1.0.59. It uploads an APK artifact; it creates no release and has read-only repository permissions.

Build: `gradle :app:assembleDebug`

Do not merge the preview identity and local-only safeguards into production.
