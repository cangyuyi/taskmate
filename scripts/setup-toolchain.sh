#!/bin/bash
# 一次性安装 Android 构建工具链到 .toolchain/（JDK 17 + cmdline-tools + SDK + Gradle）
# 全部走国内可达的镜像源（GitHub 不可达，Azul CDN 太慢）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/.toolchain"
mkdir -p "$TC/jdk" "$TC/android-sdk/cmdline-tools" "$TC/gradle" "$TC/downloads"

log() { echo "==> $*"; }

# ---------- 1. JDK 17 ----------
if [ ! -x "$TC/jdk/Contents/Home/bin/java" ]; then
  log "下载 JDK 17（华为云镜像）"
  curl -fsSL --retry 2 -m 900 -o "$TC/downloads/jdk17.tar.gz" \
    "https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_macos-aarch64_bin.tar.gz"

  log "解压 JDK"
  tar -xzf "$TC/downloads/jdk17.tar.gz" -C "$TC/jdk" --strip-components=1
else
  log "JDK 已存在"
fi

# 兜底：目录结构不符时按 bin/java 反推
if [ -x "$TC/jdk/Contents/Home/bin/java" ]; then
  export JAVA_HOME="$TC/jdk/Contents/Home"
else
  JAVA_BIN=$(find "$TC/jdk" -type f -path "*/bin/java" | head -1)
  if [ -z "$JAVA_BIN" ]; then echo "JDK 解压后找不到 java" >&2; exit 1; fi
  export JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
fi
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version

# ---------- 2. Android command-line tools ----------
if [ ! -x "$TC/android-sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "选择 cmdline-tools 版本"
  # 官方 repository2-1.xml 只列到 2022 年的旧版本（旧 sdkmanager 拉不到 manifest），
  # 这里按已知的新版本编号从新到旧探测。
  ZIP=""
  for v in 13114758 12266719 11076708; do
    if curl -fsI -m 20 "https://dl.google.com/android/repository/commandlinetools-mac-${v}_latest.zip" > /dev/null 2>&1; then
      ZIP="commandlinetools-mac-${v}_latest.zip"
      break
    fi
  done
  if [ -z "$ZIP" ]; then
    ZIP=$(curl -fsSL -m 30 "https://dl.google.com/android/repository/repository2-1.xml" \
          | grep -o 'commandlinetools-mac-[0-9]*_latest\.zip' | sort -u | tail -1)
  fi
  echo "  选中: $ZIP"
  [ -n "$ZIP" ] || { echo "找不到 cmdline-tools 包"; exit 1; }
  curl -fsSL --retry 3 -m 900 -o "$TC/downloads/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/$ZIP"

  log "解压 cmdline-tools"
  rm -rf "$TC/android-sdk/cmdline-tools/tmp"
  mkdir -p "$TC/android-sdk/cmdline-tools/tmp"
  unzip -q "$TC/downloads/cmdline-tools.zip" -d "$TC/android-sdk/cmdline-tools/tmp"
  mv "$TC/android-sdk/cmdline-tools/tmp/cmdline-tools" "$TC/android-sdk/cmdline-tools/latest"
  rm -rf "$TC/android-sdk/cmdline-tools/tmp"
else
  log "cmdline-tools 已存在"
fi

export ANDROID_HOME="$TC/android-sdk"
export ANDROID_SDK_ROOT="$TC/android-sdk"
# 沙箱限制：sdkmanager 默认往 ~/.android 写缓存会失败，改到工作区内
export ANDROID_USER_HOME="$TC/android-user"
export ANDROID_SDK_HOME="$TC/android-user"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TC/tmp"
mkdir -p "$ANDROID_USER_HOME" "$TC/tmp"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

log "接受 SDK license"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses > /dev/null 2>&1 || true

log "安装 platform-tools / platforms;android-34 / build-tools;34.0.0"
yes | sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 | tail -4

# ---------- 3. Gradle ----------
if [ ! -x "$TC/gradle/gradle-8.9/bin/gradle" ]; then
  log "下载 Gradle 8.9（腾讯镜像）"
  if ! curl -fsSL --retry 2 -m 600 -o "$TC/downloads/gradle.zip" \
      "https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip"; then
    curl -fsSL --retry 3 -m 600 -o "$TC/downloads/gradle.zip" \
      "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  fi
  unzip -q "$TC/downloads/gradle.zip" -d "$TC/gradle"
else
  log "Gradle 已存在"
fi

log "版本确认"
"$TC/gradle/gradle-8.9/bin/gradle" --version 2>&1 | head -8
sdkmanager --sdk_root="$ANDROID_HOME" --list_installed 2>/dev/null | head -8 || true
log "工具链安装完成"