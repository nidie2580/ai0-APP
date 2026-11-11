#!/bin/sh
# AI0 助手 · 无 Gradle 构建脚本
# 用法：sh build.sh
# 环境变量：
#   AI0APP_KS / AI0APP_KS_PASS —— 签名 keystore 与密码（默认 ~/.ai0app/）
set -e
PROJ=$(cd "$(dirname "$0")" && pwd)
BUILD=/tmp/ai0app_build
SDK=/tmp/sdk
AAPT2="qemu-x86_64 -L /opt/x86 $SDK/build-tools/35.0.0/aapt2"
ZIPALIGN="qemu-x86_64 -L /opt/x86 $SDK/build-tools/35.0.0/zipalign"
ANDROID_JAR=$SDK/platforms/android-35/android.jar
D8=/tmp/android-14/lib/d8.jar
APKSIGNER=/tmp/android-14/lib/apksigner.jar
ZXING=${ZXING_JAR:-/tmp/zxing-core.jar}

KS=${AI0APP_KS:-$HOME/.ai0app/ai0app.keystore}
KSPASS=${AI0APP_KS_PASS:-$(cat "$(dirname "$KS")/ai0app.pass" 2>/dev/null || true)}
[ -f "$KS" ] || { echo "缺少 keystore: $KS（设置 AI0APP_KS）"; exit 1; }
[ -n "$KSPASS" ] || { echo "缺少签名密码（设置 AI0APP_KS_PASS）"; exit 1; }

rm -rf $BUILD && mkdir -p $BUILD/classes
cd $BUILD

echo "[1/6] 编译资源"
$AAPT2 compile --dir $PROJ/res -o res.zip

echo "[2/6] 链接清单"
$AAPT2 link -o base.apk -I $ANDROID_JAR \
  --manifest $PROJ/AndroidManifest.xml res.zip \
  --java gen --min-sdk-version 26 --target-sdk-version 33

echo "[3/6] javac"
find $PROJ/src -name '*.java' > srcs.txt
[ -d gen ] && find $BUILD/gen -name '*.java' >> srcs.txt
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$SDK/build-tools/35.0.0/core-lambda-stubs.jar" -cp "$ZXING" -d classes @srcs.txt 2>&1 | grep -v 'bootstrap class path' > javac.log || true
if grep -q "error:" javac.log 2>/dev/null; then echo "!! javac 报错："; head -15 javac.log; exit 1; fi
[ -f classes/cn/ai0/app/MainActivity.class ] || { echo "!! 缺少 MainActivity.class"; head -20 javac.log; exit 1; }

echo "[4/6] d8"
java -cp $D8 com.android.tools.r8.D8 --min-api 26 --lib $ANDROID_JAR \
  --output . $(find classes -name '*.class') "$ZXING"

echo "[5/6] 打包 + 对齐"
cp base.apk app.apk
zip -q app.apk classes*.dex
$ZIPALIGN -f -p 4 app.apk aligned.apk

echo "[6/6] 签名"
java -jar $APKSIGNER sign --ks "$KS" --ks-pass "pass:$KSPASS" --key-pass "pass:$KSPASS" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$PROJ/ai0-app.apk" aligned.apk

ls -la "$PROJ/ai0-app.apk" | awk '{print "产物:", $5, "bytes"}'
