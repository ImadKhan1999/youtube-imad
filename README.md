# iTube

iTube is an Android YouTube client with the look and feel of the official YouTube app, built with
Kotlin, Jetpack Compose and Media3.

## Features

- **No ads.** Videos are played straight from YouTube's media streams, extracted on the device
  with [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor). The player never
  loads YouTube's ad endpoints.
- **Background play** with a media notification and lock-screen controls.
- **Popup (picture-in-picture)** player, automatically when you press Home on a playing video.
- **Quality selection** (144p up to 4K / 60 fps where available), **playback speed**
  (0.25x to 3x), **loop**, **audio-track** selection for dubbed videos, double-tap to seek,
  swipe-down to mini player, landscape full screen.
- YouTube-style home feed with category chips, Explore, search with suggestions,
  channels (Videos / Shorts / Live / Playlists tabs), playlists, comments and descriptions.
- An on-device library: subscriptions (with a subscriptions feed), history, Watch later and
  liked videos.
- Opens YouTube links shared from other apps.
- **Downloads**: videos (MP4/WebM up to 4K) or audio only, whole playlists with a checklist,
  offline playback from the Downloads screen. Files land in Movies/iTube and Music/iTube.
- **Sign in with Google** through Google's own sign-in page. Imports your subscriptions and
  shows your personal recommendations on Home.
- Captions, SponsorBlock segment skipping, play all / shuffle for playlists.
- **Auto-update**: iTube checks GitHub Releases on start and installs new versions in-app.

Coming next: downloads (yt-dlp) and Google sign-in.

## Building

The GitHub Actions workflow builds a debug and a release APK on every push; download them from the
run's **Artifacts**. Locally:

```
./gradlew assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 35). Minimum Android version: 8.0 (API 26).

## Releasing

Push a tag such as `v1.2.3` (or run the **Release** workflow by hand). The workflow builds an
APK signed with the release key from the repository secrets `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, and publishes it as a GitHub Release.

## Notes

iTube is not affiliated with YouTube or Google. Blocking ads and downloading videos go against
YouTube's Terms of Service; use it for personal use only.

## License

GPL-3.0-or-later, the same as NewPipe Extractor, which iTube links against. Parts of the
streaming code are adapted from [NewPipe](https://github.com/TeamNewPipe/NewPipe).
