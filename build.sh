#!/bin/bash
set -e
cd /workspaces/media-saver-app

# Install Java 17
sudo apt-get install -y openjdk-17-jdk -qq
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Install Android SDK
mkdir -p /workspaces/android-sdk/cmdline-tools
cd /workspaces/android-sdk
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline.zip
unzip -q cmdline.zip -d cmdline-tools
mv cmdline-tools/cmdline-tools cmdline-tools/latest
export ANDROID_HOME=/workspaces/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

yes | sdkmanager --licenses > /dev/null 2>&1
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null 2>&1

# Point project to SDK
cd /workspaces/media-saver-app
echo "sdk.dir=/workspaces/android-sdk" > local.properties

# Build
./gradlew assembleDebug -Dorg.gradle.java.home=$JAVA_HOME
echo "BUILD FINISHED"
