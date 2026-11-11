#!/usr/bin/env bash
# Build the Kol Hashfela APK with plain SDK tools (aapt2, javac, d8, apksigner).
#
#   ./build.sh              signed release APK  -> build/KolHashfela.apk
#   UNSIGNED=1 ./build.sh   compile-only check  -> build/KolHashfela-unsigned.apk (CI)
#
# Signing uses the private release key outside the repo (create it once with
# scripts/make-release-key.sh). Override the locations with RADIO_KEYSTORE /
# RADIO_KEYSTORE_PASS_FILE. The build refuses to sign with anything else, so a
# fresh clone can never publish an APK that won't update over the real one.
set -euo pipefail

# ─── Paths ────────────────────────────────────────────────────────────────
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
R_PKG="com.radioapp"          # Java package for the generated R class
R_PKG_PATH="com/radioapp"

BUILD_DIR="$PROJECT_DIR/build"
SRC_DIR="$PROJECT_DIR/src"
RES_DIR="$PROJECT_DIR/res"
MANIFEST="$PROJECT_DIR/AndroidManifest.xml"

BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"

SECRETS_DIR="$HOME/.secrets/radio-kol-hashfela"
KEYSTORE="${RADIO_KEYSTORE:-$SECRETS_DIR/release.keystore}"
KEYSTORE_PASS_FILE="${RADIO_KEYSTORE_PASS_FILE:-$SECRETS_DIR/keystore.pass}"
KEY_ALIAS="kolhashfela"

APK_UNSIGNED="$BUILD_DIR/KolHashfela-unsigned.apk"
APK_ALIGNED="$BUILD_DIR/KolHashfela-aligned.apk"
APK_SIGNED="$BUILD_DIR/KolHashfela.apk"

if [ "${UNSIGNED:-0}" != "1" ] && { [ ! -f "$KEYSTORE" ] || [ ! -f "$KEYSTORE_PASS_FILE" ]; }; then
    echo "ERROR: release key not found." >&2
    echo "  expected keystore: $KEYSTORE" >&2
    echo "  expected password: $KEYSTORE_PASS_FILE" >&2
    echo "Create it once with: scripts/make-release-key.sh" >&2
    echo "(or run UNSIGNED=1 ./build.sh for a compile-only check)" >&2
    exit 1
fi

# ─── Clean ────────────────────────────────────────────────────────────────
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/gen"

# ─── Step 1: Compile every resource under res/ ─────────────────────────
echo "=== Compiling resources ==="
"$AAPT2" compile --dir "$RES_DIR" -o "$BUILD_DIR/resources.zip"

# ─── Step 2: Link resources + manifest, generate R.java ─────────────────
echo "=== Linking resources ==="
"$AAPT2" link \
    -o "$APK_UNSIGNED" \
    --manifest "$MANIFEST" \
    -I "$ANDROID_JAR" \
    --java "$BUILD_DIR/gen" \
    --custom-package "$R_PKG" \
    "$BUILD_DIR/resources.zip"

# ─── Step 3: Compile all Java sources ───────────────────────────────────
echo "=== Compiling Java sources ==="
LIBS=$(find "$PROJECT_DIR/libs" -name '*.jar' | sort | tr '\n' ':')
find "$SRC_DIR" "$BUILD_DIR/gen" -name '*.java' > "$BUILD_DIR/sources.txt"
javac -source 8 -target 8 -Xlint:-options \
    -cp "$ANDROID_JAR:$LIBS" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt"

# ─── Step 4: Convert classes + library JARs to DEX ─────────────────────
echo "=== Converting to DEX ==="
{
    find "$BUILD_DIR/classes" -name '*.class' -print0
    find "$PROJECT_DIR/libs" -name '*.jar' -print0
} | xargs -0 "$D8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex"

# ─── Step 5: Add DEX to the APK ─────────────────────────────────────────
echo "=== Adding DEX to APK ==="
(cd "$BUILD_DIR/dex" && zip -q "$APK_UNSIGNED" classes*.dex)

if [ "${UNSIGNED:-0}" = "1" ]; then
    echo ""
    echo "✅ Unsigned APK built (compile check): $APK_UNSIGNED"
    exit 0
fi

# ─── Step 6: Align and sign with the private release key ────────────────
echo "=== Aligning and signing ==="
"$ZIPALIGN" -f -p 4 "$APK_UNSIGNED" "$APK_ALIGNED"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass "file:$KEYSTORE_PASS_FILE" \
    --ks-key-alias "$KEY_ALIAS" \
    --out "$APK_SIGNED" \
    "$APK_ALIGNED"
"$APKSIGNER" verify "$APK_SIGNED"

# ─── Done ────────────────────────────────────────────────────────────────
echo ""
echo "✅ APK built: $APK_SIGNED"
echo "   File size: $(du -h "$APK_SIGNED" | cut -f1)"
echo ""
echo "Install with: adb install -r $APK_SIGNED"
