#!/usr/bin/env bash
# Instrumentation run for the drawer search filter: installs sprobe-b.apk (baseline) and sprobe-c.apk
# (candidate) from the current directory (override with A=<x> B=<y> for sprobe-<x>.apk) in alternating order, types queries into the drawer search
# field and collects the PLEProbe "search" batch summaries.
# usage: bench-search-probe.sh <out.txt> [rounds] [cycles]
set -euo pipefail
L=com.google.android.apps.nexuslauncher
OUT=$1; ROUNDS=${2:-4}; CYCLES=${3:-60}
WORDS=(cal set pho map clo mes cam you chr pla)
: > "$OUT"
trap 'adb shell input keyevent KEYCODE_HOME; adb shell svc power stayon false' EXIT
adb shell svc power stayon true; adb shell input keyevent KEYCODE_WAKEUP
size=$(adb shell wm size | tr -d '\r' | awk '{print $3}'); w=${size%x*}; h=${size#*x}
for r in $(seq $ROUNDS); do
  order="${A:-b} ${B:-c}"; [ $((r % 2)) -eq 0 ] && order="${B:-c} ${A:-b}"
  for v in $order; do
    adb install -r sprobe-$v.apk >/dev/null
    adb shell su -c "am force-stop $L"; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 12
    adb shell input swipe $((w / 2)) $((h * 87 / 100)) $((w / 2)) $((h * 27 / 100)) 250; sleep 1.5
    adb shell input tap $((w * 40 / 100)) $((h * 12 / 100)); sleep 1
    adb logcat -c
    for i in $(seq $CYCLES); do
      word=${WORDS[$((i % ${#WORDS[@]}))]}
      adb shell input text "$word"; sleep 1.2
      adb shell input keyevent KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL; sleep 0.6
    done
    adb logcat -d -s PLEProbe:I | grep "search n=" | sed "s/^/$r $v /" | tee -a "$OUT"
    adb shell input keyevent KEYCODE_HOME
  done
done
