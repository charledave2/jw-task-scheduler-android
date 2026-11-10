# Changelog

## 1.0.50
- Replaced lilac input outlines with green-gray theme colors in light and dark mode.
- Added two-tone icons to the Sign In and Back to setup buttons.


## 1.0.49
- Overall Top Performer now uses the full review history in local and shared workspaces.
- Shared review history loads through one authenticated API request.


## 1.0.48
- Added centered Material You two-tone buttons to Initial Setup, including a cloud icon for shared workspace.
- Added Overall and Monthly modes to the Home page Top Performer card.


## 1.0.47
- Removed the profile popup’s delete-photo and close buttons.
- Placed the name edit icon beside the profile name and kept the name editor visible above the keyboard.
- Added bottom navigation safe-area spacing beneath Profile and Sign out.


## 1.0.46
- The in-app update banner returns on app resume until the update is installed.
- Reworked profile editing: tap the photo to upload, tap the name to edit, and aligned the action buttons.


## 1.0.45
- Refined the anchored profile popup spacing and alignment.
- Matched popup and Material surface-container colors to the app’s green-gray palette in dark and light mode.


## 1.0.44
- Fixed assignment updates so legacy same-day duplicates do not block unrelated assignments or clearing a position.
- Moved the profile shortcut into the navigation drawer above Sign out.
- Replaced the centered profile dialog with a compact popup anchored to the profile shortcut.


## 1.0.43
- Enlarged the Export Studio preview on first open while keeping it responsive on shorter screens.
- Improved share button icon visibility in light and dark mode using Material You two-tone icons and theme-aware contrast.
- Updated global icon tinting to follow the active Material theme content colors.


## 1.0.42
- Added an in-app update banner that checks public GitHub Releases.
- Added verified APK download and Android installer handoff.
- Added a signing-certificate check to prevent publishing an update that cannot replace the current app.

## 1.0.41
- Fixed export preview sizing on short portrait and landscape screens.
- Restored vertical scrolling while keeping pinch-to-zoom enabled.

## 1.0.40
- Restored the calendar’s uniform theme colors for dates, meeting markers, and schedule details.
- Removed the duplicate calendar section title and repeated meeting time text.
- Constrained the export preview to a responsive viewport so it stays visible on short landscape screens.
- Disabled unused preview panning so vertical page scrolling works while pinch-to-zoom remains available.

## 1.0.39
- Restored Schedule Calendar as a regular page.
- Meeting dates open a centered, scrollable details modal.
- Added distinct Thursday teal, Saturday amber, and Sunday muted blue calendar colors for light and dark themes.

## 1.0.38
- Opened Schedule Calendar as a centered modal with safe-area spacing and scrolling on shorter screens.
- Added safe drawing insets around the app so content avoids system bars.
- Added a persistent app-wide text-size control in App Preferences.

## 1.0.37
- Made the navigation drawer scrollable so all tools remain reachable in landscape and short-height screens.
- Preserved the current screen and in-memory state when rotating the device, avoiding a splash-screen reload.

## 1.0.36
- Fixed the Android shared-workspace API endpoint so sign-in loads congregation configuration correctly.
- Added shared-workspace setup for groups, departments, assignment roles, and meeting days/times.
- Connected cloud schedule, assignments, reviews, reports, and personnel eligibility to congregation setup.
- Added custom department membership support for shared workspaces.

## 1.0.35
- Added device-only local workspace setup and optional shared workspace sign-in.
- Added manual JSON backup and restore for local workspace data.
- Local mode stores schedules, personnel, reviews, and profile photos on-device.
- Switching from local mode to shared mode does not upload local data.
- Improved weekly task review and performance history reliability.
- Updated app branding to JW Task Scheduler.
