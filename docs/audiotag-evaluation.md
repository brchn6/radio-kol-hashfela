# AudioTag integration evaluation

Branch: `dev/audiotag`

## Goal

Evaluate no-paid/no-API-key song identification for the Android app, using AudioTag where possible, plus the station ICY metadata.

## What was verified

- `https://audiotag.info/faq` says AudioTag can recognize 10–30 second audio fragments and can recognize direct links.
- `https://audiotag.info/apisection` says API access exists, but **requires signing up for an account**. It has a free monthly budget, but it is still an authenticated API flow.
- `https://audiotag.info/xlink` currently says direct-link recognition is **temporarily closed for maintenance** and also uses a human/captcha step.
- The station stream exposes ICY metadata with `icy-metaint: 16000`, but current metadata is only `Streaming Powered By Multix`, not actual song title/artist.

## Conclusion

A fully automatic in-app AudioTag/Shazam-like recognizer **cannot be implemented correctly with no API key/account and no paid service** using AudioTag right now:

1. AudioTag API requires an account/API access.
2. Public web upload/direct-link flow requires human/captcha interaction.
3. Direct-link recognition page is currently disabled for maintenance.
4. Automating/scraping the public web flow would be brittle and likely inappropriate.
5. Shazam does not provide a public Android intent/API that returns the recognized song title back into this app.

## Decision

Removed the Shazam/Identify handoff UI. It only opened Shazam or the Play Store and could not write the recognized song back inside this app, so it was misleading.

Kept/added the useful no-key parts:

- In-app ICY metadata reader.
- Metadata appears in the app/notification when the station provides it.
- Visible **AudioTag** button that opens `https://audiotag.info/` for manual recognition. This does not pretend to identify songs inside the app.

## Test status

- Android build passes with `./build.sh`.
