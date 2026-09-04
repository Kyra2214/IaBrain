#!/usr/bin/env bash
set +e

mkdir -p artifacts/emulator-smoke-database
adb wait-for-device
./gradlew connectedDebugAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.class=com.aibrain.app.MainActivitySmokeTest,com.aibrain.app.data.local.AppDatabaseTest
test_exit=$?

timeout 20s adb exec-out screencap -p \
  > artifacts/emulator-smoke-database/screenshot-during-test.png \
  2> artifacts/emulator-smoke-database/screenshot-during-test.stderr
capture_exit=$?
[ -s artifacts/emulator-smoke-database/screenshot-during-test.png ] || \
  rm -f artifacts/emulator-smoke-database/screenshot-during-test.png
printf '%s\n' "$capture_exit" > artifacts/emulator-smoke-database/screenshot-during-test.exit-code
printf '%s\n' "$test_exit" > artifacts/emulator-smoke-database/test-exit-code.txt

exit "$test_exit"
