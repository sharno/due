# F-Droid release checklist

Due is ready for an F-Droid submission. Its copy-ready initial build recipe is
[`fdroid-metadata/dev.sharno.due.yml`](fdroid-metadata/dev.sharno.due.yml).
F-Droid's build metadata belongs in its separate `fdroiddata` repository, not
in this app's source repository; this copy makes the submission reviewable
before it is proposed upstream.

## Completed readiness work

- GPL-3.0-or-later source license.
- Public, tagged source release: [`v1.3.1`](https://github.com/sharno/due/tree/v1.3.1).
- Upstream Fastlane title, descriptions, changelog, and 512×512 PNG icon.
- Offline-only source with no account, ads, analytics, tracking, network
  service, proprietary SDK, or bundled binary dependency.
- Release APK builds from the pinned Nix toolchain and passes F-Droid's APK
  scanner.
- Clean release builds are byte-for-byte identical locally.
- AGP VCS metadata and generated vector PNGs are disabled for release
  reproducibility.
- Direct-download tester APKs are signed in CI with a long-lived key documented
  in [SIGNING.md](SIGNING.md).

## Still required before submitting

- Test core todo and notification behaviour on at least one physical Android
  device.
- Capture and commit at least one genuine phone screenshot to
  `fastlane/metadata/android/en-US/images/phoneScreenshots/`. This is needed
  for F-Droid's Latest-tab listing criteria.
- Configure F-Droid verified-reproducible signing if you want F-Droid and
  direct-download APKs to share the same signing identity. F-Droid can instead
  sign its own build, so this does not block the initial submission.

## Submit the initial recipe

1. Fork <https://gitlab.com/fdroid/fdroiddata> and create a branch.
2. Copy `fdroid-metadata/dev.sharno.due.yml` to
   `metadata/dev.sharno.due.yml` in that fork.
3. Run `fdroid lint dev.sharno.due` and the fork's CI build. Address any
   review/CI feedback; the recipe deliberately uses the immutable 40-character
   release commit rather than a mutable tag.
4. Open a `New App: Due` merge request against F-Droid's `master` branch.
5. Respond to review questions and wait for the build cycle. After acceptance,
   F-Droid builds, signs (unless verified upstream signing is configured), and
   publishes the APK.

## Future releases

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (maximum
   500 characters).
3. Build from a clean checkout with `nix develop -c gradle :app:assembleRelease`.
4. Test the APK, commit, and make an annotated `v<versionName>` tag.
5. Publish an upstream signed APK when reproducible signature verification is
   enabled. F-Droid's `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`
   then detect the release and create the next build entry.
