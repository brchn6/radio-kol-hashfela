# Progress

## 2026-11-11 - v2.0: public-ready fan app + download page

Goal (Bar): an app he can publish himself on Facebook ("I built this app to listen to my favorite radio, download it here"), because another (Arabic) station now overlaps 103.6FM over the air.

Done (uncommitted at time of writing, see git status):

- Redesign modelled on the station's original iOS app (App Store id1255901182): orange gradient, logo on white card, big play button with connecting spinner, Hebrew UI, WhatsApp-to-station pill, Facebook/Instagram/YouTube/website links, "unofficial fan app" footer with version.
- Adaptive launcher icon from the station logo (there was no icon before).
- RadioService rewrite: ExoPlayer audio focus (pause for calls/Waze, resume), stop on headphones unplugged / permanent focus loss, backoff reconnect, ICY titles via player metadata (no second connection), lock-screen logo via MediaSession metadata, state broadcast so the play button always matches reality.
- Edge-to-edge insets handled (Android 15 target 35).
- New app ID `io.github.brchn6.kolhashfela`, versionCode 2 / 2.0.
- New private signing key in `~/.secrets/radio-kol-hashfela/`; old public `radio.keystore` removed; build.sh refuses to sign without the key.
- CI changed to compile-only (`build.yml`); `pages.yml` publishes `site/`.
- `site/`: Hebrew download page with install steps, OG preview image, light/dark toggle.
- README and AGENTS.md rewritten.

Verified: `./build.sh` builds and signs `build/KolHashfela.apk` (2.2 MB); `aapt2 dump badging` shows the new ID, version, label and icon; download page checked at phone width in light and dark.

Phone test 2026-11-11 on Bar's Galaxy S22 (Android 16), via adb:

- PASS launch → connecting → live; layout fits with edge-to-edge insets; audio focus held (dumpsys audio).
- PASS media STOP key → service stops, UI shows play + "לחצו על נגן כדי להאזין".
- PASS screen off (dozing) → still PLAYING.
- PASS 15s airplane mode: buffer covers it, no audible gap; reconnect fires the moment the network returns.
- PASS 40s airplane mode: ~30s buffer, then BUFFERING; back to PLAYING ~1s after network returned.
- FIXED during test: before the network callback (ACCESS_NETWORK_STATE) it stayed on "reconnecting" 10s+ after the network came back (Bar noticed).
- Lock screen: notification posted correctly (FGS, transport, public), but Bar's phone has lock_screen_show_notifications=0, so nothing shows there. Not an app bug.
- Font-scale 1.3 screenshot failed (screen was off); autosize is in the code but unverified.
- Not tested by adb: swipe from Recents, WhatsApp voice note (transient focus), YouTube (permanent focus), unplugging headphones.
- Old v1 app (com.radioapp) uninstalled from Bar's phone at his request.

## 2026-11-12 - v2.0 published

- Commit d3a0bfe pushed to origin/main (Bar ran the push; dcg blocks agent pushes to main).
- GitHub Pages live (build_type workflow): https://brchn6.github.io/radio-kol-hashfela/ - page, og.png, logo.png, app.png, favicon.png all HTTP 200, og tags present.
- Release v2.0 created (Latest) with asset KolHashfela.apk (2,217,297 bytes). The page's download link `releases/latest/download/KolHashfela.apk` returns 200 and the file's sha256 matches the local build. APK: io.github.brchn6.kolhashfela 2/2.0, signed with cert 4A:83:6E...4B:3F.
- Old "Latest APK" release (tag `latest`, radio.apk) deleted. v1.0.0 and v1.0.1 remain.
- Open: CI `build.yml` fails at "Set up Android SDK": android-actions/setup-android@v3 runs `sdkmanager tools`, a package that no longer exists. Not a code problem; local build is fine. Fix not yet applied.
- Facebook Sharing Debugger scrape done by Bar: og tags read correctly (fb:app_id warning is harmless). Bar posted.
- Fixed after posting: app screenshot on the page rendered squashed (missing `height: auto` on `.shot`). Verified locally at 390px: rendered ratio 0.506 = natural.
- Open: untested on phones: swipe from Recents, WhatsApp voice note, YouTube, headphone unplug, font_scale 1.3.

Next (older list, items 2 and 3 done):
1. Test on Bar's phone with `adb logcat -b crash` open: launch/spinner/live, lock-screen logo, stop from notification then reopen (button shows play), rapid stop/play, WhatsApp voice note (pause + resume), YouTube (full stop), unplug headphones (stop), airplane mode 20s (reconnect), screen off 10+ min, `font_scale 1.3`, swipe from Recents (stops). v1 can stay installed alongside (different ID).
2. Take a real app screenshot for README / download page.
3. With Bar's go: commit, push, enable Pages (Source: GitHub Actions), `gh release create v2.0 build/KolHashfela.apk --latest`, delete the old rolling `latest` release that holds the v1 APK.
