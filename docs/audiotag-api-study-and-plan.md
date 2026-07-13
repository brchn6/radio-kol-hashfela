# AudioTag API study and integration plan

Branch: `dev/audiotag`

## Request

Study AudioTag (`https://audiotag.info/faq` and API area) and plan how to integrate real song recognition into the Android app.

## Public documentation studied

Pages checked:

- `https://audiotag.info/faq`
- `https://audiotag.info/apisection`
- `https://audiotag.info/api`
- `https://audiotag.info/xlink`
- `https://user.audiotag.info`
- `https://user.audiotag.info/register`

## Findings

### What AudioTag can do

From the FAQ:

- AudioTag is a music recognition/fingerprinting service.
- It can identify tracks from short audio fragments.
- Recommended fragment length: **10–30 seconds**.
- It supports many file formats, including WAV, MP3, OGG, FLAC, AMR, MP4 and many others.
- It can return title, artist, album and possible multiple matches/time intervals.

### API availability

From `https://audiotag.info/apisection`:

- AudioTag has an API for automated recognition.
- It has a free tier: up to **3 hours of audio per month**, roughly **up to 1000 track recognitions** if each sample is about 10 seconds.
- API access requires signing up for an AudioTag account.
- The public API endpoint without credentials returns:

```json
{"success":false,"error":"invalid command or parameter"}
```

### Public web flow limitations

From `https://audiotag.info/xlink` and the website UI:

- The web flow includes a human/captcha step.
- Direct-link recognition is currently marked as temporarily disabled/maintenance.
- Automating the public website flow is not reliable or appropriate.

### Current station metadata

The station stream supports ICY metadata:

- Header: `icy-metaint: 16000`
- Current metadata observed: `Streaming Powered By Multix`

This is not real artist/title data, so metadata alone cannot solve song identification unless the station improves what it sends.

## Integration constraints

### No API credentials in APK

Do **not** hardcode an AudioTag API token/key into the Android APK. This repo is public and APKs can be decompiled. If a key is embedded in the app, anyone can steal it and spend the quota.

### Android app has no Gradle/dependencies

Current Android build is intentionally minimal: `aapt2`, `javac`, `d8`, `apksigner`, no Gradle and no external libraries. Integration should keep that style where possible.

### Recognition sample source

The app cannot reliably record its own playback output from `MediaPlayer` without extra permissions/API complexity. The clean approach is:

1. Open a separate HTTP connection to the station stream.
2. Read about 10–20 seconds of audio bytes.
3. Save that as a temporary sample file/cache payload.
4. Submit that sample for recognition.

This avoids microphone permission and avoids recording the user's environment.

## Recommended architecture

### Option A — safest: small backend proxy

Use a tiny server-side proxy that owns the AudioTag API credentials.

Android app flow:

1. User taps **AudioTag** / **Identify**.
2. App downloads a 10–20 second sample from `https://radio.streamgates.net/stream/1036kh`.
3. App uploads the sample to our backend endpoint, e.g. `https://your-domain.example/recognize`.
4. Backend calls AudioTag API with private credentials.
5. Backend returns normalized JSON:

```json
{
  "success": true,
  "title": "Song title",
  "artist": "Artist",
  "album": "Album",
  "confidence": 0.92
}
```

6. App displays result in the status text and notification.

Pros:

- Keeps API credentials secret.
- Allows rate limiting, caching, logging and quota protection.
- Lets us adapt to AudioTag API changes without updating the APK.

Cons:

- Requires hosting a small backend.

### Option B — app calls AudioTag directly

Only acceptable for a private/internal build where the token is not public.

Android app flow:

1. Add `res/values/secrets.xml` locally, ignored by git.
2. App records/downloads 10–20 second stream sample.
3. App calls AudioTag API directly.
4. App parses response and displays track.

Pros:

- No server to host.

Cons:

- Unsafe for public/open-source APKs.
- Token can be extracted from APK.
- Harder to rate limit.

Recommendation: **do not use this for public releases**.

### Option C — manual AudioTag web shortcut

Current safe no-key behavior:

- App has an **AudioTag** button that opens `https://audiotag.info/`.
- User manually interacts with the website.

Pros:

- No API key, no backend, no scraping.

Cons:

- Not integrated.
- Does not write song result back into the app.
- User must handle upload/captcha manually.

This is only a placeholder, not real integration.

## Android implementation plan for real integration

### Phase 1 — UI/UX

Add a visible **Identify song** button or reuse the existing **AudioTag** button.

States:

- Idle: `Identify song`
- Capturing: `Capturing 15s sample…`
- Uploading: `Identifying…`
- Success: `Artist — Title`
- No match: `Song not found`
- Error: `Identification failed`

### Phase 2 — stream sample capture

Create a new Java class, e.g.:

`src/com/radioapp/StreamSampleRecorder.java`

Responsibilities:

- Connect to the station stream with `HttpURLConnection`.
- Set reasonable connect/read timeouts.
- Read enough bytes for ~15 seconds.
  - Stream is 64 kbps ≈ 8 KB/s.
  - 15 seconds ≈ 120 KB plus overhead.
- Save sample to `getCacheDir()/sample.aac` or hold bytes in memory.
- Avoid microphone permission.

Potential issue:

- Raw AAC+ stream may need a container/header depending on AudioTag API. If raw AAC is rejected, backend can transcode/wrap server-side.

### Phase 3 — backend API contract

Backend endpoint:

`POST /recognize`

Request:

- `multipart/form-data`
- field: `file=@sample.aac`
- optional metadata:
  - station: `Radio Kol Hashfela`
  - stream_url: `https://radio.streamgates.net/stream/1036kh`

Response:

```json
{
  "success": true,
  "matches": [
    {
      "artist": "...",
      "title": "...",
      "album": "...",
      "score": 0.92
    }
  ]
}
```

### Phase 4 — Android client

Create a new Java class, e.g.:

`src/com/radioapp/AudioTagClient.java`

Responsibilities:

- Upload sample to backend.
- Parse JSON response manually or with minimal string parsing to avoid external deps.
- Return a simple result object.

### Phase 5 — display result

- Update `statusText` in `MainActivity`.
- Broadcast result to `RadioService` so the notification can show the track.
- Optionally keep last successful recognition until the next result.

### Phase 6 — quota and safety

- Disable button while request is running.
- Add cooldown, e.g. no more than one recognition every 30–60 seconds.
- Backend should rate-limit per IP/device.
- Backend should cap uploaded file size.

## Tests/evaluation plan

### Local Android tests

- Build: `./build.sh`
- Install: `adb install -r build/radio.apk`
- Verify app still streams normally.
- Press Identify and confirm UI state changes.
- Turn network off during identify and confirm graceful error.

### Backend/API tests

After AudioTag account/API access is available:

1. Use `curl` with a known 10–20 second sample file.
2. Verify AudioTag response format.
3. Document exact request parameters and response JSON.
4. Add backend adapter for exact AudioTag command names.
5. Test quota/rate-limit behavior.

### Real station tests

- Capture 10s, 15s, 30s samples from the station.
- Compare recognition success rate.
- Evaluate if station speech/ads/jingles reduce accuracy.
- Choose default sample duration based on success/latency.

## Blockers before real automatic integration

1. Need AudioTag account/API access.
2. Need exact API command docs from inside the account area.
3. Need decide whether to host backend proxy.
4. Need test whether AudioTag accepts raw AAC+ samples or whether backend transcoding is required.

## Prototype implementation on `dev/audiotag`

After receiving the exact API documentation and testing the key locally, the branch now includes a direct Android prototype:

1. `build.sh` reads local `.env` if present.
2. It generates `build/generated-res/values/secrets.xml` with `audiotag_api_key`.
3. `.env` is ignored by git; `.env.example` is safe to commit.
4. Pressing **AudioTag** in the app:
   - captures about 15 seconds from the radio stream using a separate HTTP connection,
   - uploads the captured AAC sample to `https://audiotag.info/api` with `action=identify`, `apikey`, and `time_len=15`,
   - polls `action=get_result`,
   - displays the best `Artist — Track` match in the app.

Local API checks passed:

- `action=info` returned success with API version `2.6`.
- `action=stat` returned the account/free-seconds data.
- A captured station sample was recognized by AudioTag successfully.

## Security note

This direct prototype works for testing but embeds the API key inside the locally built APK. APKs can be decompiled, so this should not be used for public releases with a private key.

For a public/release-grade implementation, use the backend proxy architecture described above so the AudioTag key stays server-side.

## Local token handling update

A local `.env` file is now used for AudioTag credentials and is ignored by git.

Expected local variables:

```bash
AUDIOTAG_API_TOKEN=...
AUDIOTAG_API_BASE_URL=https://audiotag.info/api
```

Initial unauthenticated/auth-header probes against `https://audiotag.info/api` still returned `invalid command or parameter`, which means the token alone is not enough to infer the API command format from the public endpoint. The exact command names and upload flow still need the authenticated AudioTag account API docs.
