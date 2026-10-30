#!/bin/bash
# 构建可安装的 release APK（自签名），产物复制到 dist/TaskMate-1.3.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/.toolchain"

# JDK 解压后的目录层级可能不同，动态解析
if [ -x "$TC/jdk/Contents/Home/bin/java" ]; then
  export JAVA_HOME="$TC/jdk/Contents/Home"
else
  JAVA_BIN=$(find "$TC/jdk" -type f -path "*/bin/java" | head -1)
  [ -n "$JAVA_BIN" ] || { echo "找不到 JDK，先运行 scripts/setup-toolchain.sh" >&2; exit 1; }
  export JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
fi
export ANDROID_HOME="$TC/android-sdk"
export ANDROID_SDK_ROOT="$TC/android-sdk"
export ANDROID_USER_HOME="$TC/android-user"
# user.home 也指到工作区：AGP 会往 ~/.android 写目录，沙箱会拒
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TC/tmp -Duser.home=$TC/home"
export GRADLE_USER_HOME="$TC/gradle-home"
mkdir -p "$ANDROID_USER_HOME" "$TC/tmp" "$TC/gradle-home" "$TC/home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "缺少 JDK，先运行 scripts/setup-toolchain.sh" >&2
  exit 1
fi

echo "sdk.dir=$ANDROID_HOME" > "$ROOT/local.properties"

# 首次构建时生成签名密钥（之后一直复用，保住「覆盖安装」的能力）
KEYSTORE="$ROOT/keystore/taskmate.jks"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$ROOT/keystore"
  echo "==> 生成签名密钥"
  "$JAVA_HOME/bin/keytool" -genkeypair \
    -keystore "$KEYSTORE" -alias taskmate \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass taskmate2026 -keypass taskmate2026 \
    -dname "CN=TaskMate, OU=Personal, O=TaskMate, L=Shanghai, ST=Shanghai, C=CN" >/dev/null 2>&1
  cat > "$ROOT/keystore.properties" <<PROPS
storeFile=keystore/taskmate.jks
storePassword=taskmate2026
keyAlias=taskmate
keyPassword=taskmate2026
PROPS
fi

echo "==> 编译"
# 优先用便携工具链里的 Gradle；没有就用仓库自带的 wrapper
GRADLE_BIN="$TC/gradle/gradle-8.9/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  GRADLE_BIN="$ROOT/gradlew"
fi

echo "==> 编译"
"$GRADLE_BIN" -p "$ROOT" :app:assembleRelease --no-daemon --console=plain "$@"

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
mkdir -p "$ROOT/dist"
cp "$APK" "$ROOT/dist/TaskMate-1.3.apk"

echo
echo "==> 产物：$ROOT/dist/TaskMate-1.3.apk"
ls -lh "$ROOT/dist/TaskMate-1.3.apk"
echo
echo "==> 校验签名"
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --print-certs "$ROOT/dist/TaskMate-1.3.apk" | head -6