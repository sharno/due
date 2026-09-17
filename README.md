# Due

A deliberately small, offline Android todo list. Every task has a required due date and time, which default to today and now. When an unfinished task becomes due, Due keeps an ongoing notification visible with the outstanding work until it is completed.

## Build

```sh
nix develop -c gradle assembleDebug
```

The first Gradle build downloads Android/Compose dependencies into Gradle's cache. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Android permissions

On first launch, allow notifications. For exact to-the-minute alarms on Android 12+, use **Enable exact reminders** in the app settings; without that permission Android may defer alarms while the device is idle. Due rechecks and restores the persistent overdue notification when the app opens, the app is updated, the device reboots, or the time/time-zone changes. If Android or a device manufacturer dismisses an overdue notification, Due immediately re-posts it and runs a best-effort 15-minute watchdog until the task is complete.

## F-Droid

Due is designed for F-Droid: it is fully offline and contains no network
services, accounts, advertising, analytics, trackers, or proprietary SDKs.
Release and submission steps are documented in [F_DROID.md](F_DROID.md).

The flake also provides F-Droid's scanner. Scan a release APK with:

```sh
nix develop -c fdroid scanner --exit-code app/build/outputs/apk/release/app-release-unsigned.apk
```

## License

Copyright © 2026 Mohamed Elsharnouby. Due is licensed under
[GPL-3.0-or-later](LICENSE).
