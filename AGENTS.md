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
- `dev/audiotag` branch: automatic AudioTag recognition using a local
  `.env` API key at build time; no visible AudioTag button
- Last playlist/history: stores up to 5 recognized tracks locally and
  provides a Copy playlist button

## Source files

| File | What it does |
|------|-------------|
| `src/…/MainActivity.java` | UI layout, play/stop toggle, background image loading, track history/copy UI |
| `src/…/RadioService.java` | MediaPlayer + foreground service + notification + auto-reconnect + metadata/AudioTag recognition |
| `AndroidManifest.xml` | Permissions (INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS) |
| `res/values/strings.xml` | Strings (stream URL, app name, labels) |
| `build.sh` | Build script — compiles, dexes, packages, signs; on `dev/audiotag` reads local `.env` and generates a temporary API-key resource |
| `logo.png` | Station logo (scraped from 1036kh.com) |
| `mockup.svg` | Phone mockup for README |

## Permissions (and what they're for)

- `INTERNET` — to stream the audio
- `FOREGROUND_SERVICE` — to keep playing in background
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — required for Android 14+
- `POST_NOTIFICATIONS` — required for Android 13+

That's it. No camera, mic, location, contacts, storage, or phone
state.

## README structure

1. Logo (centered, from `logo.png`)
2. Unofficial disclaimer ("just a listener, not affiliated")
3. What makes this different (no ads, no creepy permissions, etc.)
4. Install instructions (download release / build yourself)
5. Mockup image
6. Tech details
7. License

## Secrets / AudioTag notes

- Real AudioTag API key must live only in local `.env`:
  `AUDIOTAG_API_TOKEN=...`
- `.env` is gitignored. Never commit a real AudioTag key, generated
  `build/generated-res/values/secrets.xml`, or an APK intended to keep
  the key private.
- Current automatic AudioTag recognition captures a relatively large
  AAC sample (~384 KB, roughly up to ~50 seconds as AudioTag sees it)
  because smaller raw AAC+ samples were rejected as "audio is too short".
- With a 10,800-second free budget, assume roughly 216 recognitions per
  budget period at ~50 seconds/sample. Automatic checks can miss a song
  change until the next interval; tune interval vs quota carefully.
- Public-release design should move AudioTag calls to a backend proxy so
  the key is never embedded in the APK.

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
