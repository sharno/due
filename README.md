# Due

A deliberately small, offline Android todo list. Every task has a required due date and time, which default to today and now. When an unfinished task becomes due, Due keeps an ongoing notification visible with the outstanding work until it is completed.

## Build

```sh
nix develop -c gradle assembleDebug
```

The first Gradle build downloads Android/Compose dependencies into Gradle's cache. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The flake pins the toolchain for CI and reproducible F-Droid builds, but it is declared for
`x86_64-linux` only. On another platform, install a JDK 17, Gradle 8.x (**not** 9, which AGP 8.7 does
not support) and the Android SDK with platform 35, point `local.properties` at the SDK, and run
`gradle assembleDebug` directly.

Debug builds carry an `applicationIdSuffix` of `.debug` and are named **Due (dev)**, so a development
build installs alongside a release build instead of colliding with it.

## Skills and ratings

Attach one or more **skills** to a task — a guitar technique, a language, focus, anything you want to
get better at. When you tick the task off, Due asks you to rate each attached skill from 1 to 10 for
that session. Rating is always optional: the task completes whether or not you rate it, and the sheet
can be dismissed. A task completed from its notification is recorded too, and resurfaces on the task
list as a session waiting to be rated.

**Skill stats** shows each skill's average out of 10, how many sessions it has, best and worst, a
trend against the preceding sessions, and a sparkline of recent ratings on a fixed 1–10 scale.

Deleting a task keeps the ratings it produced, so your averages never change retroactively. Deleting
a *skill* is an archive by default, which hides it and detaches it from tasks while keeping its
history; permanent deletion is a separate action and tells you how many ratings it will destroy.

## Filtering, gestures and appearance

The task list opens on **To do**, which covers everything outstanding including overdue tasks; the
other filters are Overdue, Done and All, and your choice is remembered. Each chip shows a count.

**Swipe a task left** to delete it (with a confirmation), **swipe right** to edit it, or **tap** it
to expand the due date, repeat rule and attached skills inline.

**Settings → Appearance** offers a System/Light/Dark switch and a primary colour: ten presets, or any
colour at all through the picker. Every other colour in the app — containers, surfaces, outlines — is
generated from your choice, for both light and dark, with contrast checked in the test suite so text
stays readable whatever you pick.

## Android permissions

On first launch, allow notifications. For exact to-the-minute alarms on Android 12+, use **Enable exact reminders** in the app settings; without that permission Android may defer alarms while the device is idle. Due rechecks and restores each persistent overdue-task notification when the app opens, the app is updated, the device reboots, or the time/time-zone changes. If Android or a device manufacturer dismisses an overdue notification, Due immediately re-posts it and runs a best-effort 15-minute watchdog until that task is complete.

## Data safety

Todos are stored locally in a Room database. Use **Settings → Export todos** to
create a portable JSON backup before changing devices or switching between
F-Droid and direct-download builds. The backup carries todos, skills, sessions and
ratings together; files written by older versions still import, and older versions
can still read newer files. **Import todos** replaces everything, and only after
confirmation. For an optional one-way cloud backup, use **Settings →
Set up automatic backup**, choose a Google Drive document (or another Android
document provider), and set a passphrase. Due rewrites an AES-GCM encrypted
snapshot after each todo change; it does not sign in to Google, run a sync
service, or resolve cross-device conflicts. Keep the passphrase—Due cannot
recover it. **Restore encrypted backup** imports that snapshot after
confirmation. Re-select the cloud file and configure automatic backup after
restoring the app on another device. Android backup includes the todo database
and app settings, but uninstalling the app still removes its local data.

New tasks default to **Does not repeat**. The repeat picker also supports daily,
weekly selections across multiple weekdays, monthly dates, annual dates,
weekdays, custom intervals, and optional end dates or occurrence counts.

## F-Droid

Due is designed for F-Droid: it is fully offline and contains no network
services, accounts, advertising, analytics, trackers, or proprietary SDKs.
Release and submission steps are documented in [F_DROID.md](F_DROID.md).
Direct-download signing and key-custody details are documented in
[SIGNING.md](SIGNING.md).

The flake also provides F-Droid's scanner. Scan a release APK with:

```sh
nix develop -c fdroid scanner --exit-code app/build/outputs/apk/release/app-release-unsigned.apk
```

## License

Copyright © 2026 Mohamed Elsharnouby. Due is licensed under
[GPL-3.0-or-later](LICENSE).
