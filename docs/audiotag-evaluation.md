# AudioTag integration evaluation — maximal product reference

## Goal

Evaluate song identification options for the minimal radio app. The
minimal app itself has **zero** recognition — this doc is the reference
for adding a backend-driven "maximal" product.

## ICY metadata (always available, station-dependent)

The stream exposes ICY metadata (`icy-metaint: 16000`), but currently
only sends `Streaming Powered By Multix` — not real song info. When
the station sends real artist/title data, the minimal app displays it
automatically.

## Recognition services evaluated

### AudioTag (`audiotag.info`)

| Item | Detail |
|------|--------|
| **Account needed?** | Yes — free tier: ~10,800 sec/month |
| **API cost** | Charged by analyzed audio seconds (~50 sec/recognition) |
| **Prototype status** | Proven. Direct Android call works with API key. ~384 KB AAC+ sample needed (raw AAC+ rejected below that). |
| **Production path** | API key must stay on a backend proxy, never in the APK |
| **Quota math** | ~216 recognitions/free period at 50 sec/sample |
| **Limitation** | No song-change events from station; timer-based polling can lag |

### ShazamIO (`shazamio`)

| Item | Detail |
|------|--------|
| **Key needed?** | No — reverse-engineered Shazam internal API |
| **Prototype status** | Works as Python backend (tested externally) |
| **Production path** | Run as backend proxy, not in APK (needs Python/Rust native deps) |
| **Notes** | Best option for zero-cost recognition if you run a proxy |

### ACRCloud

| Item | Detail |
|------|--------|
| **Key needed?** | Yes — free tier available |
| **Prototype status** | Credential plumbing existed in git history; never tested on live stream |
| **Production path** | Backend proxy like the others |

### GitHub `shazam-like` ecosystem

Checked `github.com/topics/shazam-like`:

- `SeaDve/Mousai` — uses AudD (needs API token)
- `aleksey-saenko/MusicRecognizer` / Audile — supports AudD, ACRCloud,
  and an unofficial Shazam path (native SongRec fingerprint — not a
  Java-only drop-in)
- `shazamio/ShazamIO` — best zero-cost option as backend proxy

## Recommendation for a maximal product

1. Keep the minimal APK clean (no keys, no recognition code).
2. Run a separate backend proxy (Python with `shazamio` or a
   lightweight server with an AudioTag key).
3. Add a settings toggle or gesture in the app that calls the proxy
   for recognition when the user wants it.

## What was removed / why

- **Identify/Shazam button** — only opened Shazam externally, couldn't
  write results back into the app. Misleading.
- **Direct AudioTag in APK** — API key extracted from decompiled APK.
  Fine for local testing, not for release.
- **Automatic timer-based recognition** — burned quota even when nobody
  was looking at the app.
