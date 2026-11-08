# Android 1.0.18 — Time-based Greeting and Animated Splash

Manage Schedule now opens to a compact week-based agenda with all day cards collapsed. Meeting shortcuts are ordered Thursday, Saturday, then Sunday; each day card opens or closes independently. Department filters and an integrated open-assignments switch make the week easier to scan. Export Studio is focused on Weekly Card PNG sharing, with month controls, arrow-based week navigation, a week picker dialog, and a zoomable poster preview. Sharing sends the generated image directly to Android share targets. The poster uses a parchment background, color-coded meeting headers, and Mic Men, Audio / Video, and Platform columns. The redundant Monthly View drawer entry is removed, and the drawer sign-out action has an exit icon and label at the lower left. Settings no longer duplicates the profile action. Schedule generation has been removed; assignments remain manual.

The schedule data loader now fills every meeting with all required Mic Men, Audio / Video, and Platform positions, including positions that do not yet have saved assignments. Group eligibility is applied to manual assignment: Thursday allows Groups 1–9 unless Thursday-exempt, Saturday allows Groups 5–9, and Sunday allows Groups 1–4. Department roles remain required. The personnel editor uses a compact 3×3 group selector with meeting-day eligibility labels.

Administrators can add, edit, activate or deactivate, and delete personnel in People Directory. The editor manages groups, department roles, and Thursday exemption. Existing remarks and assignment policy fields are preserved when editing.

Build the debug APK with JDK 21 and Android SDK 35 using `gradle assembleDebug`.
