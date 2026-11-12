# Lessons

## 2026-11-11 - localStorage is shared by every GitHub Pages project site of one account

**What went wrong:** the download page's dark mode didn't apply in testing; a `theme` value saved by another local page was read instead.
**Why:** `file://` pages share one localStorage, and so do all `brchn6.github.io/<repo>` sites (same origin), e.g. radio-playlist-dashboard.
**Correct approach:** namespace keys per site (`kolhashfela-theme`).
**Files/commands involved:** `site/index.html`.

## 2026-11-11 - dcg blocks heredocs that contain redirects to variable paths

**What went wrong:** writing `scripts/make-release-key.sh` with `cat > file <<'EOF'` was blocked because the script body contained `> "$PASS"`.
**Why:** dcg scans the whole command text, including quoted heredoc bodies.
**Correct approach:** create script files with the Write tool, then `chmod +x`.

## 2026-11-12 - img width/height attributes + CSS width without height:auto squashes the image

**What went wrong:** the live download page showed the app screenshot squeezed to half its width (Bar: "thin, looks sketchy"). `<img width="480" height="949">` plus `.shot { width: 240px }` rendered 240x949.
**Why:** the HTML height attribute acts as a CSS height; overriding only the width leaves the height fixed. The earlier phone-width check was done before the attributes were added, and nobody looked at the screenshot block after.
**Correct approach:** whenever CSS sets an image's width, also set `height: auto`. Check rendered vs natural ratio: `el.getBoundingClientRect()` width/height vs `naturalWidth/naturalHeight`.
**Files/commands involved:** `site/index.html` (.shot).
