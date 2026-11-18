#!/usr/bin/env bash
# Pubblica una nuova versione di PingPro:
#   1. compila l'APK di release firmato
#   2. lo copia in release/PingPro-<versione>.apk (rimuovendo i vecchi APK)
#   3. scrive release/latest.json, letto dall'app per l'aggiornamento automatico
# Uso: scripts/release.sh "Novità della versione"
set -euo pipefail
cd "$(dirname "$0")/.."
NOTES="${1:-Miglioramenti e correzioni.}"
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
NAME=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
./gradlew assembleRelease --console=plain -q
mkdir -p release
find release -name 'PingPro-*.apk' ! -name "PingPro-$NAME.apk" -exec git rm -q --ignore-unmatch {} \; -exec rm -f {} \;
mkdir -p release
cp app/build/outputs/apk/release/app-release.apk "release/PingPro-$NAME.apk"
SHA=$(sha256sum "release/PingPro-$NAME.apk" | cut -d' ' -f1)
SIZE=$(stat -c %s "release/PingPro-$NAME.apk")
python3 - "$CODE" "$NAME" "$SHA" "$SIZE" "$NOTES" <<'PY'
import json, sys
code, name, sha, size, notes = sys.argv[1:]
json.dump({
    "versionCode": int(code), "versionName": name,
    "apkUrl": f"https://raw.githubusercontent.com/taipia925-sketch/Ping/HEAD/release/PingPro-{name}.apk",
    "sha256": sha, "sizeBytes": int(size), "notes": notes,
}, open("release/latest.json", "w"), ensure_ascii=False, indent=2)
PY
echo "Pubblicata $NAME ($CODE) sha256=$SHA"
