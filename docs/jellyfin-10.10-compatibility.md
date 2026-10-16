# Jellyfin 10.10 compatibility

This branch keeps Findroid's current UI and players and targets Jellyfin Server 10.10.x, especially 10.10.7. It is not a claim that every feature has been tested against a live server. Support for newer servers is unverified.

## Compatibility boundary

- Jellyfin Kotlin SDK 1.7.1 declares a minimum server version of 10.10.0 and an API version of 10.10.7. Keep the SDK on the 1.7 release line; SDK 1.8.0 raises the minimum to 10.11.0.
- Server discovery keeps the SDK's normal compatibility checks. This branch does not bypass version validation.
- API calls use the 10.10-era endpoint groups for authentication, Quick Connect, libraries, playback reporting, favorites, watched state, media segments, trickplay, devices, and offline user-data sync.
- Trickplay uses the SDK's `TrickplayInfo` model. Dolby Vision detection accepts the 10.10 enum values and falls back to `VideoDoViTitle` when provided by the server.
- The `-jf10.10` version suffix identifies these builds. Debug APKs retain upstream's `.debug` application ID so they can be installed alongside an official release. They do not share its login or download data.

## Automated checks

Use the repository's JDK 17, Android SDK 37, and build-tools 37.0.0 toolchain:

```sh
./gradlew ktfmtCheck
./gradlew :data:testDebugUnitTest :modes:film:testDebugUnitTest
./gradlew :app:phone:assembleLibreDebug
# Also build Android TV:
./gradlew assembleDebug
```

The regression tests pin the supported SDK server versions, deserialize representative 10.10 trickplay and Dolby Vision metadata, and cover all supported HDR/Dolby Vision ranges, missing metadata, and the title fallback. The build workflow runs both test suites before assembling APKs.

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
