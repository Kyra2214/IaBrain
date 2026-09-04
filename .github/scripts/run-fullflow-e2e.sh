#!/usr/bin/env bash

# O android-emulator-runner executa o valor de `script` por comando; manter a
# lógica multilinear neste arquivo evita que subshells/if sejam truncados.
set +e

mkdir -p artifacts/emulator-fullflow
adb wait-for-device

mkdir -p artifacts/emulator-fullflow/live-screenshots
timeout 5s adb logcat -c \
  > artifacts/emulator-fullflow/logcat-interval-clear.txt \
  2> artifacts/emulator-fullflow/logcat-interval-clear.stderr.txt
printf '%s\n' "$?" > artifacts/emulator-fullflow/logcat-interval-clear.exit-code
adb logcat -v threadtime \
  > artifacts/emulator-fullflow/logcat-interval.txt \
  2> artifacts/emulator-fullflow/logcat-interval.stderr.txt &
logcat_pid=$!

(
  n=0
  while true; do
    stamp=$(date -u +%Y%m%dT%H%M%S)
    printf '%s\n' "$stamp" >> artifacts/emulator-fullflow/live-adb-state.log
    adb get-state >> artifacts/emulator-fullflow/live-adb-state.log 2>&1

    screenshot="artifacts/emulator-fullflow/live-screenshots/${n}-${stamp}.png"
    if timeout 5s adb exec-out screencap -p > "$screenshot" 2> "${screenshot%.png}.stderr"; then
      [ -s "$screenshot" ] || rm -f "$screenshot"
    else
      rm -f "$screenshot"
    fi

    n=$((n + 1))
    sleep 2
done
) &
monitor_pid=$!

./gradlew connectedDebugAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.class=com.aibrain.app.AIBrainFullFlowE2ETest
test_exit=$?

kill "$monitor_pid" 2>/dev/null
wait "$monitor_pid" 2>/dev/null
kill "$logcat_pid" 2>/dev/null
wait "$logcat_pid" 2>/dev/null

printf '%s\n' "$test_exit" > artifacts/emulator-fullflow/test-exit-code.txt

mkdir -p artifacts/emulator-fullflow/evidence artifacts/emulator-fullflow/test-output
timeout 20s adb pull /sdcard/Android/media/com.aibrain.app/additionalTestOutputDir/e2e-evidence \
  artifacts/emulator-fullflow/evidence \
  > artifacts/emulator-fullflow/evidence-pull.txt 2>&1
printf '%s\n' "$?" > artifacts/emulator-fullflow/evidence-pull.exit-code
timeout 20s adb pull /sdcard/Android/media/com.aibrain.app/additionalTestOutputDir \
  artifacts/emulator-fullflow/test-output \
  > artifacts/emulator-fullflow/test-output-pull.txt 2>&1
printf '%s\n' "$?" > artifacts/emulator-fullflow/test-output-pull.exit-code

# AGP/UTP may also materialize PlatformTestStorage output directly on the
# host. Copy any such directory into the artifact before emulator teardown.
mkdir -p artifacts/emulator-fullflow/test-storage
find app/build -type d \( \
  -name managed_device_android_test_additional_output -o \
  -name additional_test_output -o \
  -name additionalTestOutputDir \
\) -print0 2>/dev/null | while IFS= read -r -d '' output_dir; do
  cp -R "$output_dir"/. artifacts/emulator-fullflow/test-storage/
done

mkdir -p artifacts/emulator-fullflow/private-screenshots
timeout 20s adb exec-out run-as com.aibrain.app tar -cf - -C app_e2e-screenshots . \
  > /tmp/fullflow-screenshots.tar \
  2> artifacts/emulator-fullflow/private-screenshots-export.stderr
private_screenshots_adb_exit=$?
if [ "$private_screenshots_adb_exit" -eq 0 ]; then
  tar -xf /tmp/fullflow-screenshots.tar -C artifacts/emulator-fullflow/private-screenshots \
    2> artifacts/emulator-fullflow/private-screenshots-export.tar.stderr
  private_screenshots_tar_exit=$?
else
  private_screenshots_tar_exit=$private_screenshots_adb_exit
fi
printf '%s\n' "$private_screenshots_tar_exit" > artifacts/emulator-fullflow/screenshots-export.exit-code

# Estas capturas são deliberadamente feitas antes do retorno ao runner, que
# então encerra o emulador e pode deixar o ADB offline.
timeout 20s adb exec-out screencap -p \
  > artifacts/emulator-fullflow/screenshot-before-teardown.png \
  2> artifacts/emulator-fullflow/screenshot-before-teardown.stderr
screenshot_exit=$?
[ -s artifacts/emulator-fullflow/screenshot-before-teardown.png ] || rm -f artifacts/emulator-fullflow/screenshot-before-teardown.png
printf '%s\n' "$screenshot_exit" > artifacts/emulator-fullflow/screenshot-before-teardown.exit-code

timeout 20s adb shell uiautomator dump /sdcard/iabrain-before-teardown.xml \
  > artifacts/emulator-fullflow/ui-hierarchy-before-teardown.dump.txt \
  2> artifacts/emulator-fullflow/ui-hierarchy-before-teardown.dump.stderr
hierarchy_exit=$?
printf '%s\n' "$hierarchy_exit" > artifacts/emulator-fullflow/ui-hierarchy-before-teardown.dump.exit-code
if [ "$hierarchy_exit" -eq 0 ]; then
  timeout 20s adb exec-out cat /sdcard/iabrain-before-teardown.xml \
    > artifacts/emulator-fullflow/ui-hierarchy-before-teardown.xml \
    2> artifacts/emulator-fullflow/ui-hierarchy-before-teardown.stderr
  printf '%s\n' "$?" > artifacts/emulator-fullflow/ui-hierarchy-before-teardown.exit-code
fi

exit "$test_exit"
