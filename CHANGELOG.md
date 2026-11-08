# Changelog

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
