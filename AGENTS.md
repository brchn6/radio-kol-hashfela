# Kol Hashfela 103.6FM fan app - agent notes

Read `README.md` first: it has the exact build, test and release commands. This file adds what an agent needs on top.

## Key facts

| Item | Value |
|------|-------|
| Project root | `~/dev/radio-kol-hashfela/` |
| What it is | Unofficial one-station Android radio app, published by Bar (GitHub `brchn6`) as a sideloaded APK, shared on Facebook via the download page |
| Application ID | `io.github.brchn6.kolhashfela` (Java classes stay in `com.radioapp`; `build.sh` generates R there with aapt2 custom-package) |
| Stream | `https://radio.streamgates.net/stream/1036kh` (AAC+ 64 kbps, ICY title is a generic placeholder) |
| Station site / socials | `https://www.1036kh.com/`, facebook.com/1036kh, instagram.com/1036kh, WhatsApp 972585851036 |
| Min / target SDK | 26 / 35 (compiled against android-35, build-tools 35.0.0) |
| Build | `./build.sh` (signed) or `UNSIGNED=1 ./build.sh` (compile check). No Gradle; Media3 JARs in `libs/` |
| Signing key | `~/.secrets/radio-kol-hashfela/` (keystore + password file). Never print, copy or commit them |
| APK | `build/KolHashfela.apk`; release asset must keep that exact name |
| Download page | `site/` → GitHub Pages `https://brchn6.github.io/radio-kol-hashfela/` via `.github/workflows/pages.yml` |
| Original app the design copies | iOS App Store id1255901182 ("קול השפלה - רדיו ישראל 103.6", Arbel Israeli, last update 2022) |
| SDK / JDK | `~/Android/Sdk/`, `~/tools/jdk17/` |

## Rules for this repo

- Releases are signed only on the workstation with the private key. CI (`build.yml`) is compile-only and must never sign or publish an APK.
- Bump `versionCode` in `AndroidManifest.xml` for every release.
- Never push, create releases or change Pages settings without Bar's explicit go.
- Keep the app minimal: no analytics, no ads SDKs, no Facebook SDK or embedded feeds, no new permissions without a reason in the README table.
- Keep the "unofficial fan app" line in the app footer and on the download page.

## Architecture in one paragraph

`RadioService` is a foreground service that owns an ExoPlayer with `handleAudioFocus=true` (pauses for calls/navigation, resumes after) and `handleAudioBecomingNoisy=true`. Permanent focus loss or unplugged headphones trigger a full stop. Errors reconnect with exponential backoff (2s to 30s). Playback state lives in static fields (`getState()`, `getTrack()`) and every change is broadcast as `ACTION_STATE_CHANGED` inside the app; `MainActivity` renders only from that state. A framework `MediaSession` carries the logo and title to the lock screen and Bluetooth.

## History

- v1.x (`com.radioapp`, signed with a key that was committed to the public repo) is retired. Its key is public, so the app ID and key were both changed in v2.0. v2 installs next to v1 (different ID), so v1 users end up with two apps until they delete the old one; the download page tells them they can.
- Song recognition experiments (AudioTag, ShazamIO) are documented in `docs/audiotag-evaluation.md` and in git history; they are not in the app.
