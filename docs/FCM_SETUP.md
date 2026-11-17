# Task assignment notifications

Firebase project: `jw-task-scheduler`. Release package: `com.quarry.congregationscheduler`.
The supplied client configuration is at `app/src/release/google-services.json`.
It is a client configuration, not a server private key. Never add a service-account key to this repository or APK.

## Backend prerequisite

Use the accompanying **jw-task-scheduler-fcm-backend-patch.zip**. The backend source supplied for this change was an older snapshot, so it is intentionally delivered as new files plus an integration diff, not a full replacement deployment. Apply and review that diff against the current deployed backend. Do not replace newer workspace, department, or permission code with the snapshot.

1. Apply the additive `0010_fcm_notifications.sql` migration (rename the numeric prefix if already used).
2. Install the new notification endpoints and helper; merge the assignment-save integration into every current assignment-writing route.
3. In Firebase project settings → Service accounts, generate a service-account key for `jw-task-scheduler`. Enable Firebase Cloud Messaging HTTP v1 API if needed.
4. In the Cloudflare Pages production environment add an **encrypted secret** named `FCM_SERVICE_ACCOUNT_JSON` containing the full service-account JSON. Add the same secret in preview only if preview notifications are intended. Redeploy. Do not commit this value.
5. Sign in as admin, open Settings & Accounts → Accounts & Access → account → Link personnel. Select the correct personnel by ID; matching names are not used.
6. Install the release app, sign in to the shared workspace, and allow notifications. Open Settings & Accounts → Task notifications → Test. A successful server response means FCM accepted the message; verify it also appears on the actual device.
7. Assign a future task to that personnel and save. Verify the device receives the task, tapping opens the schedule date, unchanged saves do not resend, and signing out stops delivery locally.

## Behavior and limits

- Shared workspace only. Local workspace data stays local.
- Every populated task slot, including backup roles, may notify; other slots need not be filled.
- The server links a user ID to a personnel ID and stores a separate registration per installation. All registered devices for that linked active account are targeted.
- A notification is queued only when the saved personnel changes. Outbox inserts and assignment saves are one D1 transaction.
- Queued messages are checked against current assignments, active users, links, and device ownership before delivery. Unregistered FCM tokens are removed.
- Transient failures stay queued with exponential backoff. Dispatch runs after assignment saves/device registration and through the admin Retry queued notifications action. Pages alone does not automatically wake up at retry time: add a scheduled Worker invoking a protected server-side drain if unattended retries are required. At most 20 outbox entries are dispatched per call, so larger queues need subsequent drains.
- FCM delivery can be delayed, especially with no internet, Android force-stop, or manufacturer battery restrictions. Tests do not prove real-device delivery.
- Data messages carry account ID. The app rejects messages for other accounts or local mode and deduplicates 100 recent event IDs. Notification content is private on the lock screen.
- Notifications preserve biometric app lock and page permissions. A user without Schedule Calendar access goes to Home.
- After Android permission changes or reconnecting, reopen the app to refresh registration. The app also retries temporary registration failures through WorkManager.
- TEST/debug builds keep `.test` and do not register production notifications. For testing FCM, use the release package; a separate Firebase Android client is needed to enable FCM on `.test`.
- Service-account setup, production deployment, signing and physical-device verification are still required before this feature is live.

## Validation

PR build compiles debug and release Kotlin and validates the release Firebase configuration. Existing unit/emulator checks remain enabled. Backend regression tests cover recipients, unchanged saves, backup tasks, account changes, disabled/unlinked users, failures/retries, unregistered tokens, authorization and HTTP v1 OAuth signing.
