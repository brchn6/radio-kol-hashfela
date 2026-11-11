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
