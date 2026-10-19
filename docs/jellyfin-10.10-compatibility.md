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
- Current build: `1.1.0-jf10.10-1.2`.
- Compatibility-only updates increment the final revision: `1.1.0-jf10.10-1.1`, `1.1.0-jf10.10-1.2`, and so on.
- After integrating the latest upstream `master`, take its official version and reset the compatibility revision to `1.0`: for example, `1.2.0-jf10.10-1.0` if upstream becomes `1.2.0`. The server marker remains `jf10.10`.
- Update `Versions.APP_NAME` for the displayed version. Keep Android's `Versions.APP_CODE` greater than every previously delivered build's code, including after upstream merges; the current code is `36` (Release 1.1 used `35`; earlier previews used `33` and `34`). Do not reset this numeric code with the displayed compatibility revision.

## Automated checks

Use the repository's JDK 17, Android SDK 37, and build-tools 37.0.0 toolchain:

```sh
./gradlew ktfmtCheck
./gradlew :buildSrc:test verifyCompatibilityRelease
python3 -m unittest discover -s scripts -p "test_*.py"
./gradlew :data:testDebugUnitTest :modes:film:testDebugUnitTest \
  :player:local:testDebugUnitTest :app:phone:testLibreDebugUnitTest
./gradlew :app:phone:assembleLibreDebug
# Also build Android TV:
./gradlew assembleDebug
```

The Release configuration checks run before every Release build and also run in Debug CI without needing private keys. They enforce the independent application ID, compatibility version scheme, non-debuggable status, R8/resource shrinking, and rejection of Gradle signing configurations (including default/flavor configurations). The validation rules have their own regression tests.

The regression tests pin the supported SDK server versions, deserialize representative 10.10 trickplay and Dolby Vision metadata, and cover all supported HDR/Dolby Vision ranges, missing metadata, and the title fallback. The build workflow runs the compatibility and frame-capture regression suites alongside APK assembly.

## Independent signed Release

The independent Release uses `dev.jdtech.jellyfin.compat`, display name `Findroid`, and a dedicated signing key. Debug remains `dev.jdtech.jellyfin.debug` / `Findroid Debug`. The official application, compatibility Release, and Debug preview keep separate app data. Install the compatibility Release as a new application and sign in again; no login or downloaded media are migrated. Phone and TV APKs share the compatibility Release identity and are alternatives, not two apps to install on one device.

Release enables R8 optimization and resource shrinking and explicitly disables debugging. Gradle produces unsigned Release APKs; signing is a separate local step so the private key and passwords never enter Gradle, repository files, CI logs, or published artifacts. Debug CI remains usable without signing credentials. Do not substitute a Debug signing key or upload an unsigned APK as a signed Release.

Build and test with JDK 17 / Android SDK 37 / build-tools 37.0.0:

```sh
./gradlew :buildSrc:test verifyCompatibilityRelease ktfmtCheck \
  :data:testDebugUnitTest :modes:film:testDebugUnitTest \
  :player:local:testDebugUnitTest :app:phone:testLibreDebugUnitTest \
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
  --out "$OUTPUT_DIR/findroid-1.1.0-jf10.10-1.2-phone-arm64-v8a-release.apk" \
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
  "$OUTPUT_DIR/findroid-1.1.0-jf10.10-1.2-phone-arm64-v8a-release.apk" \
  --build-tools "$BUILD_TOOLS" --version 1.1.0-jf10.10-1.2 --version-code 36 \
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

## Capture the current video frame (phone/tablet)

The camera button between playback speed and audio tracks captures the currently playing video, including paused frames. It does not invoke Android screen capture, request screen-recording permission, or fetch a Jellyfin thumbnail at a nearby timestamp.

- ExoPlayer copies only its video `SurfaceView` using `PixelCopy`. Player controls, Android system bars, and separate subtitle overlays are not part of that surface. Output uses the rendered surface dimensions, downscaled only if its longest edge exceeds 4096 pixels; it is not an original-resolution decoder-frame export.
- The saved image follows the video aspect ratio, not the device screen: a 4:3 video on a 16:9 screen remains 4:3, excluding the player-added pillarbox bars. ExoPlayer FIT and ZOOM both retain the complete video surface aspect ratio; PixelCopy does not copy the cropped parent window. Black bars encoded into the video itself remain part of the frame.
- mpv uses its native `screenshot-to-file … video` command. This omits its subtitles and OSD, which otherwise live inside the rendered surface. Burned-in subtitles that are part of the video itself cannot be removed. The native call runs off the UI thread and is synchronized with native-player destruction.
- Android 10 and later save a PNG directly to `Pictures/Findroid` in the gallery, without photo-library or storage permission. Android 9 first captures the frame and then opens Android's one-file save picker, avoiding broad legacy storage permission. Canceling the picker discards the temporary image.
- The button is disabled until video is ready and while a save is in progress. Seek/item changes during capture reject the result. Capture/save errors show a message and remove incomplete gallery entries. No full-screen screenshot fallback is used.
- While playing, the snapshot is the current frame available to the renderer when capture executes. Pause before capturing when a specific animation frame matters. Protected surfaces and some tunneled/hardware-output combinations cannot be copied. HDR/Dolby Vision color/bit depth depends on the backend and device: ExoPlayer's PNG is an 8-bit rendered-surface copy, and mpv uses its screenshot renderer. Neither path promises source-bit-exact HDR output.
- TV uses separate, incomplete playback controls and does not receive this phone/tablet toolbar button.

Before release, verify on real Android 9 and Android 10+ devices with both ExoPlayer and mpv:

1. Capture paused and playing SDR animation with controls/system bars visible. Open the PNG and confirm correct frame/aspect ratio, no player UI, and no separate subtitles.
2. Repeat taps, seek immediately during capture, skip to the next item, rotate/resize, exit playback, and enter/leave PiP during a save. Confirm no crash, stale-frame export, duplicate save, or permanently disabled button.
3. Check a narrow landscape phone, a display cutout, and a tablet. The six top-right actions must remain reachable and labelled for accessibility.
4. On Android 9, save, cancel, and return from the file picker; repeat after activity recreation. The saved frame must be the one captured before the picker opened.
5. On Android 10+, verify `Pictures/Findroid`, missing/full storage handling, and that failed saves leave no blank/pending gallery item.
6. Check GPU and hardware-decoded mpv, ExoPlayer tunneled playback where available, HDR10/HLG/Dolby Vision, and protected/unsupported output. Record device/backend limitations rather than treating a successful compile as device validation.

Additional unit checks: `./gradlew :app:phone:testLibreDebugUnitTest :player:local:testDebugUnitTest` covers capture-size limits and incomplete native PNG rejection. Actual PixelCopy, MediaStore, native mpv screenshots, and file-picker behavior require the device checks above.

Implementation references: [Android PixelCopy](https://developer.android.com/reference/android/view/PixelCopy), [Android shared-media storage](https://developer.android.com/training/data-storage/shared/media), and [mpv screenshot commands](https://mpv.io/manual/stable/#screenshot-commands).
