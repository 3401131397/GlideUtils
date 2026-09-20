#!/bin/bash
set -e

WORKSPACE="$(cd "$(dirname "$0")" && pwd)"
TOOLS_DIR="$WORKSPACE/.tools"
BUILD_DIR="/tmp/chess_build_$$"

echo "=== 初始化构建环境 ==="
mkdir -p "$BUILD_DIR" "$TOOLS_DIR"

# 下载 JDK 17 (如果本地没有)
if [ ! -f "$TOOLS_DIR/jdk17.tar.gz" ]; then
    echo "下载 JDK 17..."
    curl -L -o "$TOOLS_DIR/jdk17.tar.gz" "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz"
fi

# 下载 Gradle 8.0 (如果本地没有)
if [ ! -f "$TOOLS_DIR/gradle.zip" ]; then
    echo "下载 Gradle 8.0..."
    curl -L -o "$TOOLS_DIR/gradle.zip" "https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.0-bin.zip"
fi

# 下载 Android cmdline-tools (如果本地没有)
if [ ! -f "$TOOLS_DIR/cmdline-tools.zip" ]; then
    echo "下载 Android SDK 命令行工具..."
    curl -L -o "$TOOLS_DIR/cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
fi

echo "解压工具..."
mkdir -p "$BUILD_DIR/jdk" "$BUILD_DIR/gradle" "$BUILD_DIR/android-sdk/cmdline-tools"
cd "$BUILD_DIR/jdk" && tar xzf "$TOOLS_DIR/jdk17.tar.gz" && cd -
cd "$BUILD_DIR/gradle" && unzip -o -q "$TOOLS_DIR/gradle.zip" && cd -
cd "$BUILD_DIR/android-sdk/cmdline-tools" && unzip -o -q "$TOOLS_DIR/cmdline-tools.zip" && cd -

# 设置环境变量
JDK_DIR=$(find "$BUILD_DIR/jdk" -maxdepth 1 -type d -name "jdk-17*" | head -1)
GRADLE_DIR=$(find "$BUILD_DIR/gradle" -maxdepth 1 -type d -name "gradle-8*" | head -1)

export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$BUILD_DIR/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$GRADLE_DIR/bin:$ANDROID_HOME/cmdline-tools/cmdline-tools/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "接受 SDK 许可..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

echo "安装 SDK 组件..."
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" > /dev/null 2>&1

echo "=== 开始构建 APK ==="
cd "$WORKSPACE"
echo "sdk.dir=$ANDROID_HOME" > local.properties

GRADLE_OPTS="-Xmx1g -XX:+HeapDumpOnOutOfMemoryError"
$GRADLE_DIR/bin/gradle assembleDebug --no-daemon 2>&1 | tail -20

APK_PATH="$WORKSPACE/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "=== 构建成功 ==="
    echo "APK: $APK_PATH"
    ls -lh "$APK_PATH"
else
    echo "=== 构建失败 ==="
    exit 1
fi

# 清理构建缓存
rm -rf "$BUILD_DIR"

echo "=== 完成 ==="
