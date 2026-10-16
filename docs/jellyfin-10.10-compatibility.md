# Jellyfin 10.10 compatibility

This branch keeps Findroid's current UI and players and targets Jellyfin Server 10.10.x, especially 10.10.7. It is not a claim that every feature has been tested against a live server. Support for newer servers is unverified.

## Compatibility boundary

- Jellyfin Kotlin SDK 1.7.1 declares a minimum server version of 10.10.0 and an API version of 10.10.7. Keep the SDK on the 1.7 release line; SDK 1.8.0 raises the minimum to 10.11.0.
- Server discovery keeps the SDK's normal compatibility checks. This branch does not bypass version validation.
- API calls use the 10.10-era endpoint groups for authentication, Quick Connect, libraries, playback reporting, favorites, watched state, media segments, trickplay, devices, and offline user-data sync.
- Trickplay uses the SDK's `TrickplayInfo` model. Dolby Vision detection accepts the 10.10 enum values and falls back to `VideoDoViTitle` when provided by the server.
- The `-jf10.10-<compatibility revision>` suffix identifies these builds. Debug APKs retain upstream's `.debug` application ID so they can be installed alongside an official release. They do not share its login or download data.

## Version naming

- Format: `<upstream version>-jf10.10-<compatibility revision>`.
- Current build: `1.1.0-jf10.10-1.1`.
- Compatibility-only updates increment the final revision: `1.1.0-jf10.10-1.1`, `1.1.0-jf10.10-1.2`, and so on.
- After integrating the latest upstream `master`, take its official version and reset the compatibility revision to `1.0`: for example, `1.2.0-jf10.10-1.0` if upstream becomes `1.2.0`. The server marker remains `jf10.10`.
- Update `Versions.APP_NAME` for the displayed version. Keep Android's `Versions.APP_CODE` greater than every previously delivered build's code, including after upstream merges; the current code is `35` (the earlier previews used `33` and `34`). Do not reset this numeric code with the displayed compatibility revision.

## Automated checks

Use the repository's JDK 17, Android SDK 37, and build-tools 37.0.0 toolchain:

```sh
./gradlew ktfmtCheck
./gradlew :buildSrc:test verifyCompatibilityRelease
python3 -m unittest discover -s scripts -p "test_*.py"
./gradlew :data:testDebugUnitTest :modes:film:testDebugUnitTest
./gradlew :app:phone:assembleLibreDebug
# Also build Android TV:
./gradlew assembleDebug
```

The Release configuration checks run before every Release build and also run in Debug CI without needing private keys. They enforce the independent application ID, compatibility version scheme, non-debuggable status, R8/resource shrinking, and rejection of Gradle signing configurations (including default/flavor configurations). The validation rules have their own regression tests.

The regression tests pin the supported SDK server versions, deserialize representative 10.10 trickplay and Dolby Vision metadata, and cover all supported HDR/Dolby Vision ranges, missing metadata, and the title fallback. The build workflow runs both test suites before assembling APKs.

## Independent signed Release

The independent Release uses `dev.jdtech.jellyfin.compat`, display name `Findroid`, and a dedicated signing key. Debug remains `dev.jdtech.jellyfin.debug` / `Findroid Debug`. The official application, compatibility Release, and Debug preview keep separate app data. Install the compatibility Release as a new application and sign in again; no login or downloaded media are migrated. Phone and TV APKs share the compatibility Release identity and are alternatives, not two apps to install on one device.

Release enables R8 optimization and resource shrinking and explicitly disables debugging. Gradle produces unsigned Release APKs; signing is a separate local step so the private key and passwords never enter Gradle, repository files, CI logs, or published artifacts. Debug CI remains usable without signing credentials. Do not substitute a Debug signing key or upload an unsigned APK as a signed Release.

Build and test with JDK 17 / Android SDK 37 / build-tools 37.0.0:

```sh
./gradlew :buildSrc:test verifyCompatibilityRelease ktfmtCheck \
  :data:testDebugUnitTest :modes:film:testDebugUnitTest \
  :app:phone:assembleLibreRelease :app:tv:assembleLibreRelease
```

Keep the dedicated keystore and its two password files in a restricted directory outside the checkout and deliverable directories. Do not print passwords, put them on a command line, or commit them. The example below uses `SIGNING_DIR` for that private directory, `BUILD_TOOLS` for the SDK build-tools directory, and `OUTPUT_DIR` for public artifacts. Back up the key and passwords privately before distributing the first release, then retain this same key for all subsequent updates.

```sh
"$BUILD_TOOLS/apksigner" sign \
  --ks "$SIGNING_DIR/findroid-compat-release.jks" \
  --ks-key-alias findroid-compat \
  --ks-pass "file:$SIGNING_DIR/store-password.txt" \
  --key-pass "file:$SIGNING_DIR/key-password.txt" \
  --debuggable-apk-permitted false \
  --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out "$OUTPUT_DIR/findroid-1.1.0-jf10.10-1.1-phone-arm64-v8a-release.apk" \
  app/phone/build/outputs/apk/libre/release/phone-libre-arm64-v8a-release-unsigned.apk
```

Repeat for `app/tv` with the TV input/output names. Signing with build-tools 37 also aligns the APK's native-library entries to 16 KiB; alignment alone does not certify every native library's ELF page-size support or device compatibility.

The dedicated public certificate SHA-256 is:

```text
a99931d4c73f6fe364537ce711cada94c8ba8f4960392e066d0c0fda84f2215d
```

Verify each final signed APK (use `app/tv`'s mapping for the TV APK):

```sh
python3 scripts/verify_compat_release.py \
  "$OUTPUT_DIR/findroid-1.1.0-jf10.10-1.1-phone-arm64-v8a-release.apk" \
  --build-tools "$BUILD_TOOLS" --version 1.1.0-jf10.10-1.1 --version-code 35 \
  --certificate-sha256 a99931d4c73f6fe364537ce711cada94c8ba8f4960392e066d0c0fda84f2215d \
  --mapping app/phone/build/outputs/mapping/libreRelease/mapping.txt
```

The verifier checks the package identity, version, label, non-debuggable/non-test-only manifest, ARM64-only native libraries, signature v3 for supported Android versions plus a separate v2-block verification for API 24–27, the exact public certificate, ZIP alignment, absence of embedded keystores, and R8 mapping/configuration. It checks the mapping ID against the APK's Release-mode R8 DEX marker to reject stale or wrong-module mappings. Preserve the R8 mapping and configuration alongside the source commit for crash diagnosis. Confirm resource shrinking is enabled in the Release configuration and that R8 generated the corresponding `shrunk_resources_proto_format` output. A successful build and cryptographic verification do not replace the live-device/server checklist below.

## Live-server verification

Before treating a build as ready for daily use, run this checklist against Jellyfin 10.10.7 with an ordinary, non-administrator test account:

- Add the server, sign in with username/password, sign out, and sign in with Quick Connect if enabled.
- Browse libraries, movies, series, seasons, episodes, search, suggestions, and continue watching.
- Play a video with each enabled player; seek, pause, resume, and switch available audio/subtitles.
- Check SDR, HDR10, HDR10+, HLG, and Dolby Vision metadata using suitable media and hardware.
- Verify progress, favorites, and watched/unwatched state on the server, including after reconnecting from offline use.
- Verify trickplay thumbnails and skip segments when those features are configured on the server.
- Download a video, play it offline, reconnect, and confirm watch progress syncs.

Successful compilation and unit tests do not replace these device/server checks. No server address or credentials are needed for the automated tests.

## References

- [SDK 1.7.1 release and compatibility](https://github.com/jellyfin/jellyfin-sdk-kotlin/releases/tag/v1.7.1)
- [SDK 1.8.0 release and compatibility](https://github.com/jellyfin/jellyfin-sdk-kotlin/releases/tag/v1.8.0)
- [SDK server version constants](https://github.com/jellyfin/jellyfin-sdk-kotlin/blob/v1.7.1/jellyfin-core/src/commonMain/kotlin/org/jellyfin/sdk/Jellyfin.kt)
- [Jellyfin 10.10.7 Dolby Vision metadata](https://github.com/jellyfin/jellyfin/blob/v10.10.7/MediaBrowser.Model/Entities/MediaStream.cs)
