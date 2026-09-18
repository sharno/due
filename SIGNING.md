# Release signing

GitHub Releases contain an installable APK from the `signed` build type. The
`signed` variant uses the long-lived Due signing key supplied to the release
job through GitHub Actions secrets. Pull requests and branch builds never get
access to that key.

The ordinary `release` build intentionally has no signing configuration. It
therefore remains unsigned and reproducible for F-Droid.

## Key custody

The keystore and its passwords must not be committed. Keep at least one
encrypted offline backup of the keystore, alias, store password, and key
password. Losing the keystore means users cannot receive updates signed by the
same certificate.

The repository secrets used by the workflow are:

- `DUE_SIGNING_KEYSTORE_BASE64`
- `DUE_SIGNING_STORE_PASSWORD`
- `DUE_SIGNING_KEY_ALIAS`
- `DUE_SIGNING_KEY_PASSWORD`

Only push a version tag after confirming that the signing secrets and their
offline backup are available. A tag build publishes `due-installable.apk`; the
unsigned F-Droid APK remains in the workflow artifact.

Direct-download and F-Droid APKs may have different signing certificates until
F-Droid's verified-reproducible signing is configured. Export todos before
switching channels.
