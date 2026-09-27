#!/usr/bin/env bash
# Instrumentation run: installs probe-base.apk and probe-cand.apk from the current directory in
# alternating order, triggers drawer rebuilds, and collects the PLEProbe batch summaries.
# usage: bench-probe.sh <out.txt> [rounds] [cycles]
set -euo pipefail
L=com.google.android.apps.nexuslauncher; T=com.google.android.calculator
OUT=$1; ROUNDS=${2:-4}; CYCLES=${3:-60}
: > "$OUT"
trap 'adb shell su -c "pm unsuspend $T" >/dev/null; adb shell svc power stayon false' EXIT
adb shell svc power stayon true; adb shell input keyevent KEYCODE_WAKEUP
for r in $(seq $ROUNDS); do
  order="base cand"; [ $((r % 2)) -eq 0 ] && order="cand base"
  for v in $order; do
    adb install -r probe-$v.apk >/dev/null
    adb shell su -c "am force-stop $L"; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 12
    adb logcat -c
    for _ in $(seq $CYCLES); do
      adb shell su -c "pm suspend $T" >/dev/null; sleep 0.7
      adb shell su -c "pm unsuspend $T" >/dev/null; sleep 0.7
    done
    adb logcat -d -s PLEProbe:I | grep "drawer n=" | sed "s/^/$r $v /" | tee -a "$OUT"
  done
done
