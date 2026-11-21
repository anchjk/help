#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
command -v java >/dev/null || { echo 'Missing JDK 17+' >&2; exit 1; }
command -v gradle >/dev/null || { echo 'Missing Gradle 9.6+' >&2; exit 2; }
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
[ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ] || { echo 'Set ANDROID_HOME/ANDROID_SDK_ROOT to an Android SDK with Platform 36 and Build Tools.' >&2; exit 3; }
cd "$ROOT"
gradle --no-daemon clean assembleRelease
APK="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$APK" ] || { echo "Expected APK not found: $APK" >&2; exit 4; }
ls -lh "$APK"
echo 'NOTE: configure an application-owned signingConfig before production distribution.'
