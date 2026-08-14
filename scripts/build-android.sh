#!/bin/zsh
set -euo pipefail

project_dir="${0:A:h:h}"
android_dir="$project_dir/android"
jdk_home="${JAVA_HOME:-}"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
apk_source="$android_dir/app/build/outputs/apk/debug/app-debug.apk"
apk_target="$project_dir/outputs/codex-pocket-0.16.10.apk"

portable_jdk="$project_dir/work/toolchain/jdk-17.0.20+8/Contents/Home"
if [[ -z "$jdk_home" && -x "$portable_jdk/bin/java" ]]; then
    jdk_home="$portable_jdk"
fi
if [[ -z "$jdk_home" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    jdk_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
if [[ -z "$sdk_root" && -d "$project_dir/work/android-sdk" ]]; then
    sdk_root="$project_dir/work/android-sdk"
fi
if [[ -z "$jdk_home" || ! -x "$jdk_home/bin/java" ]]; then
    echo "JDK 17 is required. Set JAVA_HOME before building." >&2
    exit 1
fi

mkdir -p "$project_dir/outputs"

if [[ -n "$sdk_root" ]]; then
    env JAVA_HOME="$jdk_home" ANDROID_SDK_ROOT="$sdk_root" \
        "$android_dir/gradlew" --project-dir "$android_dir" --no-daemon lintDebug assembleDebug
else
    env JAVA_HOME="$jdk_home" \
        "$android_dir/gradlew" --project-dir "$android_dir" --no-daemon lintDebug assembleDebug
fi

cp "$apk_source" "$apk_target"
echo "APK: $apk_target"
