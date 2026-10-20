#!/bin/sh
# Bump versionCode, build, and serve the APK over Tailscale for Downloader on the projector.
set -e
cd "$(dirname "$0")"
perl -pi -e 's/versionCode (\d+)/"versionCode ".($1+1)/e' app/build.gradle
grep versionCode app/build.gradle
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug -q
cd app/build/outputs/apk/debug && cp app-debug.apk vidsrc.apk
echo "Serving at http://100.118.231.16:8000/vidsrc.apk (Ctrl+C to stop)"
python3 -m http.server 8000
