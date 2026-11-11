#!/usr/bin/env bash
# One-time: create the private release signing key OUTSIDE the repo.
#
#   scripts/make-release-key.sh
#
# Writes ~/.secrets/radio-kol-hashfela/release.keystore and keystore.pass
# (mode 0600). The password is random and never printed.
# BACK BOTH FILES UP. If they are lost, phones can never install an update
# over the existing app; everyone would have to uninstall and reinstall.
set -euo pipefail
JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"

DIR="${RADIO_SECRETS_DIR:-$HOME/.secrets/radio-kol-hashfela}"
KS="$DIR/release.keystore"
PASS="$DIR/keystore.pass"

if [ -e "$KS" ] || [ -e "$PASS" ]; then
    echo "Refusing to overwrite an existing key in $DIR" >&2
    exit 1
fi

umask 077
mkdir -p "$DIR"
openssl rand -base64 32 | tr -d '\n' > "$PASS"
chmod 600 "$PASS"

keytool -genkeypair \
    -keystore "$KS" -storetype PKCS12 \
    -storepass:file "$PASS" \
    -alias kolhashfela \
    -keyalg RSA -keysize 4096 -validity 36500 \
    -dname "CN=Kol Hashfela fan app, O=brchn6, C=IL" >/dev/null 2>&1
chmod 600 "$KS"

echo "Release key created in $DIR"
echo "Certificate SHA-256 fingerprint (publish it so people can verify the APK):"
keytool -list -v -keystore "$KS" -storepass:file "$PASS" -alias kolhashfela 2>/dev/null \
    | grep -E 'SHA256:' | head -1
echo ""
echo "Back up both files in $DIR somewhere safe (password manager / encrypted drive)."
