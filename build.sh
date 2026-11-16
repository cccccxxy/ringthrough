#!/usr/bin/env bash
# 纯命令行打包（不用 Gradle）：aapt(v1) -> javac -> d8 -> zipalign -> apksigner
# 本机是 arm64，Android SDK 自带的 aapt2/d8 是 x86_64 跑不了，
# 所以用 Ubuntu 的 arm64 版 aapt / zipalign / apksigner，d8 用 build-tools 里的 jar（Java 跨架构）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-/root/workspace/build/sdk}"
BT="$SDK/build-tools/36.0.0"
PLATFORM="$SDK/platforms/android-36/android.jar"
AAPT_PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/build"
DIST="$ROOT/dist"

for f in "$PLATFORM" "$BT/lib/d8.jar"; do
  [ -e "$f" ] || { echo "缺少构建组件: $f" >&2; exit 1; }
done
for t in aapt zipalign apksigner javac java; do
  command -v "$t" >/dev/null || { echo "缺少命令: $t" >&2; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$DIST"

echo "==> aapt: 编译资源 + 生成 R.java + 打基础包"
aapt package -f \
  -M "$ROOT/AndroidManifest.xml" \
  -S "$ROOT/res" \
  -I "$AAPT_PLATFORM" \
  -J "$OUT/gen" \
  -F "$OUT/base.apk" \
  --version-code 2 --version-name 1.1

echo "==> javac"
find "$ROOT/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$PLATFORM" -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --min-api 29 --lib "$PLATFORM" --output "$OUT/dex" @"$OUT/classes.txt"

echo "==> 合并 dex 并签名"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes.dex )
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$ROOT/debug.keystore"
if [ ! -f "$KS" ]; then
  echo "==> 生成签名证书"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias ringthrough -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=RingThrough, OU=DSH, O=DSH, L=Shanghai, C=CN" >/dev/null 2>&1
fi

apksigner sign --ks "$KS" --ks-key-alias ringthrough \
  --ks-pass pass:android --key-pass pass:android \
  --out "$DIST/ringthrough.apk" "$OUT/aligned.apk"
apksigner verify --print-certs "$DIST/ringthrough.apk" | head -3

ls -l "$DIST/ringthrough.apk"
