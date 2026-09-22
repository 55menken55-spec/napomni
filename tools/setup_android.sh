#!/usr/bin/env bash
# Установка инструментов для сборки Android-приложения (JDK 17, Gradle, Android SDK).
# Всё ставится в /home/user/.local/opt — каталог исключён из снапшотов рабочей области.
set -u
export DEBIAN_FRONTEND=noninteractive
TOOLROOT=/home/user/.local/opt
mkdir -p "$TOOLROOT"

echo "== [1/5] JDK 17 =="
JDK_DIR=$(ls -d /usr/lib/jvm/*17* /usr/lib/jvm/*java-17* 2>/dev/null | head -1 || true)
if [ -z "$JDK_DIR" ]; then
  apt-get update -y >/tmp/setup_apt.log 2>&1
  apt-get install -y openjdk-17-jdk unzip wget >/tmp/setup_apt2.log 2>&1 || echo "APT_FAILED (пробуем fallback)"
  JDK_DIR=$(ls -d /usr/lib/jvm/*17* /usr/lib/jvm/*java-17* 2>/dev/null | head -1 || true)
fi
if [ -z "$JDK_DIR" ]; then
  echo "Качаем Temurin JDK 17..."
  wget -q "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -O /tmp/jdk17.tar.gz
  mkdir -p "$TOOLROOT/jdk17"
  tar -xzf /tmp/jdk17.tar.gz -C "$TOOLROOT/jdk17" --strip-components=1
  JDK_DIR="$TOOLROOT/jdk17"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME"
java -version 2>&1 | head -2

echo "== [2/5] Gradle 8.9 =="
if [ ! -d "$TOOLROOT/gradle-8.9" ]; then
  wget -q https://services.gradle.org/distributions/gradle-8.9-bin.zip -O /tmp/gradle-8.9-bin.zip
  unzip -q -o /tmp/gradle-8.9-bin.zip -d "$TOOLROOT"
fi
"$TOOLROOT/gradle-8.9/bin/gradle" --version 2>&1 | grep -iE "gradle|jvm:" | head -3

echo "== [3/5] Android cmdline-tools =="
SDK="$TOOLROOT/android-sdk"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  mkdir -p "$SDK/cmdline-tools"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip
  unzip -q -o /tmp/cmdtools.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

echo "== [4/5] Лицензии и пакеты SDK =="
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses > /tmp/setup_lic.log 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0" > /tmp/setup_sdk.log 2>&1
tail -3 /tmp/setup_sdk.log

echo "== [5/5] DONE_SETUP =="
