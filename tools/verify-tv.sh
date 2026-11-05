#!/usr/bin/env bash
set -uo pipefail
bash ./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=2
tv_test_status=$?
mkdir -p work/tv-screenshots
adb pull /sdcard/Android/data/com.gathin.tvhome/files/screenshots/. work/tv-screenshots/
tv_pull_status=$?
if [ "$tv_test_status" -ne 0 ]; then exit "$tv_test_status"; fi
if [ "$tv_pull_status" -ne 0 ]; then exit "$tv_pull_status"; fi
test -s work/tv-screenshots/home-4.0.png
