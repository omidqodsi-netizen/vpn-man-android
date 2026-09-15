#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/app/libs" "$ROOT/app/src/main/res/font" "$ROOT/app/src/main/assets/licenses"

echo "Downloading latest AndroidLibXrayLite AAR..."
RELEASE_JSON="$(curl -fsSL --retry 3 https://api.github.com/repos/2dust/AndroidLibXrayLite/releases/latest)"
AAR_URL="$(printf '%s' "$RELEASE_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(next(a["browser_download_url"] for a in d["assets"] if a["name"]=="libv2ray.aar"))')"
curl -fL --retry 3 "$AAR_URL" -o "$ROOT/app/libs/libv2ray.aar"

echo "Downloading Vazirmatn fonts for build..."
BASE="https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/ttf"
curl -fL --retry 3 "$BASE/Vazirmatn-Regular.ttf" -o "$ROOT/app/src/main/res/font/vazirmatn_regular.ttf"
curl -fL --retry 3 "$BASE/Vazirmatn-Medium.ttf" -o "$ROOT/app/src/main/res/font/vazirmatn_medium.ttf"
curl -fL --retry 3 "$BASE/Vazirmatn-Bold.ttf" -o "$ROOT/app/src/main/res/font/vazirmatn_bold.ttf"

echo "Downloading third-party license texts..."
curl -fL --retry 3 https://raw.githubusercontent.com/2dust/AndroidLibXrayLite/main/LICENSE -o "$ROOT/app/src/main/assets/licenses/AndroidLibXrayLite-LGPL-3.0.txt"
curl -fL --retry 3 https://raw.githubusercontent.com/XTLS/Xray-core/main/LICENSE -o "$ROOT/app/src/main/assets/licenses/Xray-core-MPL-2.0.txt"

echo "Dependencies prepared."
