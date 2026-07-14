# Radio Kol Hashfela — Agent handoff notes

## Project overview

Minimal Android app that streams **Radio Kol Hashfela 103.6FM**
without ads, trackers, or unnecessary permissions. Built entirely
with command-line tools (no Gradle, no IDE, no external deps).

## Key facts

| Item | Value |
|------|-------|
| **Project root** | `~/dev/radio-kol-hashfela/` |
| **Stream URL** | `https://radio.streamgates.net/stream/1036kh` (AAC+ 64kbps) |
| **Website** | `https://www.1036kh.com/` |
| **GitHub** | `https://github.com/brchn6/radio-kol-hashfela` |
| **Min SDK** | API 26 (Android 8.0) |
| **Target SDK** | API 35 |
| **Package** | `com.radioapp` |
| **Build** | `./build.sh` — uses aapt2, javac, d8, apksigner |
| **Signing** | Self-signed keystore at `radio.keystore` (pass: `radio123`) |
| **APK** | Output at `build/radio.apk` |
| **Release** | `gh release create vX.Y.Z --title "..." --notes "..." build/radio.apk` |

## What the app does

- Single screen with a random Hashfela/Shfela nature photo background
  loaded from Wikimedia Commons
- Centered play/stop button
- Title: "Radio Kol Hashfela" with status/track text
- Streams in a foreground service (`RadioService`) so playback
  continues when the screen is off or other apps are in use
- Notification with Play/Stop media controls
- Shows ICY stream metadata when the station sends real song info

## Source files

| File | What it does |
|------|-------------|
| `src/…/MainActivity.java` | UI layout, play/stop toggle, background image loading, metadata display |
| `src/…/RadioService.java` | ExoPlayer (Media3) + foreground service + notification + auto-reconnect + ICY metadata reader |
| `AndroidManifest.xml` | Permissions (INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS) |
| `res/values/strings.xml` | Strings (stream URL, app name, labels) |
| `build.sh` | Build script — compiles, dexes, packages, signs |
| `libs/` | Bundled Media3 (ExoPlayer) JARs — no Gradle needed, play works without network drops |
| `logo.png` | Station logo (scraped from 1036kh.com) |
| `mockup.svg` | Phone mockup for README |

## Permissions (and what they're for)

- `INTERNET` — to stream the audio
- `FOREGROUND_SERVICE` — to keep playing in background
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — required for Android 14+
- `POST_NOTIFICATIONS` — required for Android 13+

That's it. No camera, mic, location, contacts, storage, or phone
state.

## Player engine

Uses **ExoPlayer (Media3 1.10.0)** — Google's recommended player, used by
YouTube Music, Spotify, and every real-world radio app. Handles buffering
and network drops (biking, cell handoffs) seamlessly without glitching.

Media3 JARs are bundled in `libs/` (no Gradle needed):
- `media3-common` — core types
- `media3-exoplayer` — the player engine
- `media3-datasource` — HTTP streaming
- `media3-decoder` — audio decoding
- `media3-extractor` — format detection (AAC, MP3, etc.)
- `guava` — Google core libs (Media3 dependency)
- `failureaccess` — Guava utility (Android variant)

## Product paths

There are two product paths defined in this repo:

### Minimal (main — current)
A clean radio player with no song recognition, no API keys, no
network calls beyond streaming and Wikimedia Commons images.
Track names come only from ICY metadata if the station ever sends
real song info. ~2.3 MB APK.

### Maximal (backend-proxy experiment — documented in `docs/`)
Add song recognition by running a local backend proxy:

| Service | Status | Docs |
|---------|--------|------|
| **AudioTag** | Prototype proven on `dev/audiotag` history. API key stays server-side. Free quota ~10,800 seconds/month. | `docs/audiotag-evaluation.md` |
| **ShazamIO** | Python/Rust backend using reverse-engineered Shazam API. Works well as proxy. | `docs/audiotag-evaluation.md` |
| **ACRCloud** | Credential plumbing existed in prototype but never tested on live stream. | — |

README structure notes:
1. Logo (centered, from `logo.png`)
2. Unofficial disclaimer
3. What makes this different
4. Install instructions
5. Mockup image
6. Tech details + product paths
7. License

## Common tasks

```bash
# Build
cd ~/dev/radio-kol-hashfela && ./build.sh

# Install to phone via ADB when connected
adb install -r build/radio.apk

# Push to phone via KDE Connect
kdeconnect-cli --share build/radio.apk --device <device-id>

# Release a new version
gh release create v1.0.1 --title "v1.0.1" --notes "..." build/radio.apk

# GitHub remote
git remote add origin https://github.com/brchn6/radio-kol-hashfela.git
```

## Android SDK location

`~/Android/Sdk/` — installed via command-line tools.
Build-tools 35.0.0, platform android-35.

## JDK location

`~/tools/jdk17/` — downloaded from Adoptium since the system
only has JRE (OpenJDK 25) without javac.
