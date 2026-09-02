#!/usr/bin/env bash
# Restores the JVM/Android toolchain that sandbox resets wipe from ~/.cache.
# See docs/development/sandbox-toolchain.md. This script does NOT touch git
# remotes or SSH — in this environment GitHub is used over https.
#
# After it finishes, builds need:
#   export JAVA_HOME="$HOME/.cache/toolchain/jdk17"
#   export PATH="$JAVA_HOME/bin:$HOME/.cache/toolchain/gradle/bin:$PATH"
#   export GRADLE_USER_HOME="$HOME/.cache/gradle-home"
#   export ANDROID_HOME="$HOME/.cache/android-sdk"
set -uo pipefail

TOOLCHAIN="$HOME/.cache/toolchain"
SDK="$HOME/.cache/android-sdk"
ARCH="$(uname -m)"
[ "$ARCH" = "x86_64" ] && AARCH="x64" || AARCH="aarch64"

log() { echo "[restore $(date +%H:%M:%S)] $*"; }

mkdir -p "$TOOLCHAIN" "$SDK"

# --- 1) Temurin 17 JDK -----------------------------------------------------
if [ -x "$TOOLCHAIN/jdk17/bin/java" ]; then
  log "JDK 17 already present: $("$TOOLCHAIN/jdk17/bin/java" -version 2>&1 | head -1)"
else
  log "downloading Temurin 17 ($AARCH)…"
  if curl -fL --retry 3 --retry-delay 2 -o /tmp/jdk17.tar.gz \
      "https://api.adoptium.net/v3/binary/latest/17/ga/linux/${AARCH}/jdk/hotspot/normal/eclipse"; then
    mkdir -p "$TOOLCHAIN/jdk17"
    tar -xzf /tmp/jdk17.tar.gz -C "$TOOLCHAIN/jdk17" --strip-components=1
    rm -f /tmp/jdk17.tar.gz
    log "JDK installed: $("$TOOLCHAIN/jdk17/bin/java" -version 2>&1 | head -1)"
  else
    log "ERROR: JDK download failed"
  fi
fi

# --- 2) Gradle 8.14.2 ------------------------------------------------------
if [ -x "$TOOLCHAIN/gradle/bin/gradle" ]; then
  log "Gradle already present"
else
  log "downloading Gradle 8.14.2…"
  if curl -fL --retry 3 --retry-delay 2 -o /tmp/gradle.zip \
      "https://services.gradle.org/distributions/gradle-8.14.2-bin.zip"; then
    rm -rf /tmp/gradle-unzip && mkdir -p /tmp/gradle-unzip
    unzip -q /tmp/gradle.zip -d /tmp/gradle-unzip
    rm -rf "$TOOLCHAIN/gradle"
    mv /tmp/gradle-unzip/gradle-8.14.2 "$TOOLCHAIN/gradle"
    rm -rf /tmp/gradle.zip /tmp/gradle-unzip
    log "Gradle installed at $TOOLCHAIN/gradle"
  else
    log "ERROR: Gradle download failed"
  fi
fi

export JAVA_HOME="$TOOLCHAIN/jdk17"
export PATH="$JAVA_HOME/bin:$TOOLCHAIN/gradle/bin:$PATH"

# --- 3) Android SDK --------------------------------------------------------
mkdir -p "$SDK/licenses" "$SDK/cmdline-tools"
# Standard license hashes (accepted by sdkmanager without prompts)
printf '%s\n' \
  "8933bad161af4178b1185d1a37fbf41ea5269c55" \
  "24333f8a63b6825ea38c56a1f85f91e9" \
  > "$SDK/licenses/android-sdk-license"
echo "d56f5187479451eabf01fb78af6dfcb131a6481e" > "$SDK/licenses/android-sdk-preview-license"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "downloading Android cmdline-tools 11076708…"
  if curl -fL --retry 3 --retry-delay 2 -o /tmp/cmdtools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"; then
    rm -rf /tmp/cmdtools && mkdir -p /tmp/cmdtools
    unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools
    rm -rf "$SDK/cmdline-tools/latest"
    mv /tmp/cmdtools/cmdline-tools "$SDK/cmdline-tools/latest"
    rm -rf /tmp/cmdtools.zip /tmp/cmdtools
    log "cmdline-tools installed"
  else
    log "ERROR: cmdline-tools download failed"
  fi
fi

if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  if [ ! -d "$SDK/platforms/android-35" ] || [ ! -d "$SDK/build-tools/35.0.0" ]; then
    log "installing platforms;android-35 + build-tools;35.0.0 (this is the slow part)…"
    yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" \
        "platforms;android-35" "build-tools;35.0.0" > /tmp/sdkmanager.log 2>&1
    tail -2 /tmp/sdkmanager.log
  fi
  AAPT="$SDK/build-tools/35.0.0/aapt"
  if [ -x "$AAPT" ]; then
    log "aapt OK: $("$AAPT" version 2>&1 | head -1)"
  else
    log "ERROR: aapt missing — check /tmp/sdkmanager.log"
  fi
else
  log "ERROR: sdkmanager missing — cannot install SDK packages"
fi

log "DONE."
log "use: export JAVA_HOME=$TOOLCHAIN/jdk17"
log "     export PATH=\"\$JAVA_HOME/bin:$TOOLCHAIN/gradle/bin:\$PATH\""
log "     export GRADLE_USER_HOME=\$HOME/.cache/gradle-home"
log "     export ANDROID_HOME=$SDK"
