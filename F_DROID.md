# F-Droid release checklist

This project contains upstream Fastlane metadata in
`fastlane/metadata/android/en-US/`. It deliberately does not contain an F-Droid
build recipe yet: that recipe must reference an immutable commit from the public
upstream repository.

## Preconditions

- Choose and add a FLOSS license in `LICENSE`.
- Publish the complete source history to a public Git repository.
- Capture and commit a 512×512 PNG icon and at least one phone screenshot under
  `fastlane/metadata/android/en-US/images/`. These are needed for a polished
  listing and for F-Droid's Latest tab eligibility.
- Test notification behaviour on a physical Android device.

## Release

1. Build the release APK from a clean checkout with:

   ```sh
   nix develop -c gradle :app:assembleRelease
   ```

2. Increment `versionCode` and `versionName` in `app/build.gradle.kts`, update
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, commit, and
   create an annotated release tag such as `v1.0.0`.
3. Publish the source repository and the developer-signed release APK. Keep the
   signing key stable for every future release.
4. Fork `https://gitlab.com/fdroid/fdroiddata`, add
   `metadata/dev.sharno.due.yml`, run `fdroid lint` and the CI build, then open a
   `New App: Due` merge request.

## Initial fdroiddata recipe

After the public repository and `v1.0.0` tag exist, use the tag's full commit
hash rather than the tag name:

```yaml
Categories:
  - Time
License: <SPDX-license-id>
AuthorName: Mohamed Elsharnouby
AuthorEmail: sharnoby3@gmail.com
SourceCode: <public-source-url>
IssueTracker: <public-issues-url>

RepoType: git
Repo: <public-clone-url>

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: <full-40-character-commit-hash-for-v1.0.0>
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

The app has no AntiFeatures: it does not use network services, advertising,
analytics, trackers, proprietary SDKs, or non-free assets.

## Reproducibility

The release build disables AGP's embedded VCS information and generated PNGs
for vector resources. Build signed release APKs from the exact, clean tagged
commit using JDK 17 and Gradle from the flake. Once the public release APK is
available, compare it with the F-Droid CI build before enabling signature-copy
verification.
