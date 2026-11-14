#!/usr/bin/env bash
# Builds FazailEAmaal.apk with the plain Android SDK tools (no Gradle).
# Needs: ANDROID_HOME, JDK 17+, python3 + pypdf, pdftotext, zip. Book PDF at ./book.pdf
# Optional text version: OCR_CACHE=<folder>, plus tesseract (eng+ara), pillow, pyspellchecker.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?set ANDROID_HOME}}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
JAR="$(ls -d "$SDK"/platforms/android-[0-9]* | sort -V | tail -1)/android.jar"
echo "build-tools: $BT"; echo "platform: $JAR"
VC="${VERSION_CODE:-1}"

rm -rf build && mkdir -p build/assets build/classes build/dex
# Text version: needs the OCR cache made by tools/text/ocr.py (see README). Without it the
# app is built with scanned pages only.
if [ -n "${OCR_CACHE:-}" ] && [ -d "$OCR_CACHE" ]; then
  python3 tools/text/build.py book.pdf "$OCR_CACHE" build/assets/text
  mkdir -p build/assets/fonts
  cp fonts/*.ttf build/assets/fonts/
  cp web/reader.css web/reader.js build/assets/
fi
python3 tools/prepare.py book.pdf build/assets

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code "$VC" --version-name "1.$VC" -A build/assets build/res.zip

javac --release 8 -cp "$JAR" -Xlint:-options -nowarn \
  -d build/classes $(find src -name '*.java')
find build/classes -name '*.class' -print0 | xargs -0 "$BT/d8" --release --min-api 21 \
  --lib "$JAR" --output build/dex
(cd build/dex && zip -q ../base.apk classes.dex)

"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk
"$BT/apksigner" sign --ks release.keystore --ks-key-alias fazail \
  --ks-pass pass:fazail123 --key-pass pass:fazail123 \
  --out FazailEAmaal.apk build/aligned.apk
"$BT/apksigner" verify FazailEAmaal.apk
ls -la FazailEAmaal.apk
