#!/usr/bin/env bash
set -euo pipefail

# ─── Paths ────────────────────────────────────────────────────────────────
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="com.radioapp"
PKG_PATH="com/radioapp"

BUILD_DIR="$PROJECT_DIR/build"
SRC_DIR="$PROJECT_DIR/src"
RES_DIR="$PROJECT_DIR/res"
MANIFEST="$PROJECT_DIR/AndroidManifest.xml"

AAPT2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
D8="$ANDROID_HOME/build-tools/35.0.0/d8"
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
KEYSTORE="$PROJECT_DIR/radio.keystore"
KEYSTORE_PASS="radio123"
KEY_ALIAS="radio"
APK_UNSIGNED="$BUILD_DIR/radio-unsigned.apk"
APK_SIGNED="$BUILD_DIR/radio.apk"

# ─── Clean ────────────────────────────────────────────────────────────────
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"
mkdir -p "$BUILD_DIR/dex"
mkdir -p "$BUILD_DIR/apk"
mkdir -p "$BUILD_DIR/gen"  # for generated R.java

# ─── Step 1: Compile resources → .flat files ────────────────────────────
echo "=== Compiling resources ==="
"$AAPT2" compile -o "$BUILD_DIR/apk/resources.zip" \
    "$RES_DIR/values/strings.xml"

# ─── Step 2: Link resources → APK (without dex) + generate R.java ──────
echo "=== Generating R.java and base APK ==="
"$AAPT2" link \
    -o "$APK_UNSIGNED" \
    --manifest "$MANIFEST" \
    -I "$ANDROID_JAR" \
    --java "$BUILD_DIR/gen" \
    "$BUILD_DIR/apk/resources.zip"

# ─── Step 3: Compile Java sources (including generated R.java) ─────────
echo "=== Compiling Java sources ==="
javac -source 8 -target 8 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" \
    "$SRC_DIR/$PKG_PATH/MainActivity.java" \
    "$SRC_DIR/$PKG_PATH/RadioService.java" \
    "$BUILD_DIR/gen/$PKG_PATH/R.java"

# ─── Step 4: Convert to DEX ──────────────────────────────────────────────
echo "=== Converting to DEX ==="
# Find all .class files recursively
find "$BUILD_DIR/classes" -name '*.class' -print0 | xargs -0 \
    "$D8" --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex"

# ─── Step 5: Inject DEX into APK ────────────────────────────────────────
echo "=== Adding DEX to APK ==="
cd "$BUILD_DIR/dex"
zip -q "$APK_UNSIGNED" classes.dex
cd "$PROJECT_DIR"

# ─── Step 6: Create keystore (if missing) and sign ──────────────────────
if [ ! -f "$KEYSTORE" ]; then
    echo "=== Generating keystore ==="
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$KEYSTORE_PASS" \
        -keypass "$KEYSTORE_PASS" \
        -dname "CN=RadioApp,OU=Dev,O=RadioApp,L=TelAviv,C=IL" 2>/dev/null
fi

echo "=== Signing APK ==="
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$KEYSTORE_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --out "$APK_SIGNED" \
    "$APK_UNSIGNED"

# ─── Done ────────────────────────────────────────────────────────────────
echo ""
echo "✅ APK built: $APK_SIGNED"
echo "   File size: $(du -h "$APK_SIGNED" | cut -f1)"
echo ""
echo "Install with: adb install $APK_SIGNED"
