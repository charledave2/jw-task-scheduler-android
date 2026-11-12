# Changelog

## 1.0.63
- Move People Directory into More and put Export Studio and Task Review on the main navigation bar.
- Add a More shortcut to the app bar.
- Ask administrators who substituted when a task is marked excused or cancelled.
- Credit completed substitute assignments to the substitute while keeping excused and missed task history with the originally assigned person.
- Add a separate debug application ID and label for installable test builds.


## 1.0.62
- Show assigned tasks in Weekly Task Review on the meeting day and keep them visible until an administrator reviews each task.
- Clarify the empty state when no meeting day with assigned tasks has arrived this week.
- Animate the Home, Calendar, People, and More navigation icons with a spring bounce and tilt on tap.


## 1.0.61
- Added an animated forest-green splash screen with a softly glowing calendar constellation, orbiting schedule, people, and completion icons, and a checkmark reveal.
- Adapted the splash layout and animation for landscape and short screens.


## 1.0.60
- Replaced the hamburger drawer with a forest-green bottom navigation bar for Home, Calendar, Manage, People, and More.
- Added a prominent animated center button that opens Manage Schedule, with permission-aware Calendar and Home fallbacks.
- Moved reports, task review, Export Studio, Settings & Accounts, and profile actions into More.
- Kept shared sign-in, cloud data, biometric lock, and in-app updates in the official app.


## 1.0.59
- Added optional biometric app lock in App Preferences, with supported strong fingerprint/face authentication and phone PIN, pattern or password fallback.
- Require system authentication before enabling or disabling app lock.
- Protect cold launches and lock immediately when the app goes into the background, including local workspaces.
- Hide workspace content behind a secure lock window and block screenshots while app lock is enabled.
- Retain editing and photo-picker state while locked; cancellation or failed authentication never opens the workspace.

## 1.0.58
- People Directory now displays the configured workspace group count instead of Groups 1–9.
- Add/Edit person uses the workspace configuration in shared and local modes, including group choices and eligible meeting labels.
- Renamed the directory status filter to All Status.
- Added clear empty-group handling and validation without silently changing a saved person's group.

## 1.0.57
- Replaced the App Preferences attribution with Visit me on GitHub and the GitHub logo, opening the developer profile.

## 1.0.56
- Updated the app icon, animated splash logo, login and setup branding to the approved forest-green calendar, people and checkmark design.
- Replaced cloudy transparent patches with fully opaque artwork and generated all launcher density sizes.
- Rounded the animated splash tile to match the new app branding.

## 1.0.55
- Made the profile menu more compact with borderless Change photo and Rename actions side by side.
- Reduced profile menu spacing while retaining Two-Tone icons and comfortable touch targets.

## 1.0.54
- Emphasized the signed-in account role in bold uppercase beside the profile photo at the bottom of navigation.
- Added Change photo and Rename buttons to the profile menu, and moved Sign out inside it.
- Hid Workspace and Data & Backup from Viewer settings.
- Kept profile controls scrollable with keyboard-aware spacing and a bottom safe area.

## 1.0.53
- Centered update dialog now shows release changes with Material Two-Tone icons and Update/Later buttons.
- Choosing Later hides the prompt for the current foreground session; it returns on the next app open while an update is available.
- Administrators can choose visible pages for each managed account from Page access.
- Hidden pages are removed from navigation and home shortcuts, with access refreshed when the app resumes.
- Existing role permissions remain in effect; Home and administrator access remain available.

## 1.0.52
- Added administrator password reset for managed accounts.
- Display uploaded account photos in the account grid and modal, with initials fallback.
- Preserve custom permissions during account updates and revoke all sessions on disable or password reset.
- Keep administrator roles protected and remove unsupported admin promotion controls.

## 1.0.51
- Replaced the Accounts & Access list with a responsive profile grid and administrator badge.
- Added a centered account actions dialog with role and account status controls.
- Replaced the Add account text action with a Material You two-tone icon button.


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
