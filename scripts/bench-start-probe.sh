#!/usr/bin/env bash
# Instrumentation run for module installation time: installs start-a.apk and start-b.apk from the
# current directory in alternating order and restarts the launcher several times per block,
# collecting the PLEStart phase/feature timings. Build each variant with
# scripts/instrumentation/start-probe.patch applied (`git apply`), which logs the time of each phase
# of installLauncher and of each feature install. Instrumentation builds only; see PERFORMANCE.md.
# usage: bench-start-probe.sh <out.txt> [rounds] [restarts]
set -euo pipefail
L=com.google.android.apps.nexuslauncher
OUT=$1; ROUNDS=${2:-6}; RESTARTS=${3:-6}
: > "$OUT"
trap 'adb shell input keyevent KEYCODE_HOME; adb shell svc power stayon false' EXIT
adb shell svc power stayon true; adb shell input keyevent KEYCODE_WAKEUP
for r in $(seq $ROUNDS); do
  order="a b"; [ $((r % 2)) -eq 0 ] && order="b a"
  for v in $order; do
    adb install -r start-$v.apk >/dev/null
    for i in $(seq $RESTARTS); do
      adb logcat -c
      adb shell su -c "am force-stop $L"; sleep 1
      adb shell input keyevent KEYCODE_HOME; sleep 5
      adb logcat -d -s PLEStart:I | grep PLEStart | sed "s/^/$r $v $i /" >> "$OUT"
    done
  done
done
