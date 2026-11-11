# Decisions

## 2026-11-11 - Fan app published by Bar, not handed to the station

Bar publishes the APK himself and shares a download page on Facebook. No Google Play for now (Play would need an App Bundle and target API 36). Google's sideloading developer verification is enforced only in BR/ID/SG/TH since 2026-09-30 and goes global in 2027; revisit then.

## 2026-11-11 - New application ID and signing key in v2.0

The v1 keystore and its password were committed to the public repo, so anyone could sign an "update". v2.0 uses a private key in `~/.secrets/radio-kol-hashfela/` and a new ID `io.github.brchn6.kolhashfela` (the generic `com.radioapp` could collide with other apps). Because the ID changed, v2 installs next to v1 rather than over it; v1 users delete the old app themselves (the download page says so). Doing both changes in one release means this happens only once. CI no longer publishes APKs; releases are signed locally.

## 2026-11-11 - Design copies the station's original iOS app, minus the Facebook feed

Orange + logo card + play button + social links, like the 2017-2022 app by Arbel Israeli. The in-app Facebook feed was left out because it would load Facebook tracking code. Nature-photo backgrounds from Wikimedia were dropped (extra network call, attribution requirements).

## 2026-11-11 - Audio focus handled by ExoPlayer

Replaced the custom focus listener (which stopped and released the player on any interruption) with `handleAudioFocus=true`. Transient loss pauses and resumes; permanent loss or unplugged headphones stop the service.
