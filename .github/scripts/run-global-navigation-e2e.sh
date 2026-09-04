#!/usr/bin/env bash
set +e

mkdir -p artifacts/emulator-navigation
adb wait-for-device
./gradlew connectedDebugAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.class=com.aibrain.app.GlobalNavigationE2ETest
test_exit=$?

timeout 20s adb exec-out screencap -p \
  > artifacts/emulator-navigation/screenshot-during-test.png \
  2> artifacts/emulator-navigation/screenshot-during-test.stderr
capture_exit=$?
[ -s artifacts/emulator-navigation/screenshot-during-test.png ] || \
  rm -f artifacts/emulator-navigation/screenshot-during-test.png
printf '%s\n' "$capture_exit" > artifacts/emulator-navigation/screenshot-during-test.exit-code
printf '%s\n' "$test_exit" > artifacts/emulator-navigation/test-exit-code.txt

exit "$test_exit"
