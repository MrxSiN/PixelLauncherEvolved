#!/usr/bin/env bash
# Instrumentation run for double tap to sleep, with a person unlocking the phone after every tap.
#
# usage: bench-sleep-probe.sh <out.txt> [rounds] [taps-per-block]
#   Installs tprobe-b.apk (baseline) and tprobe-c.apk (candidate) from the current directory in
#   alternating order. For each tap it waits until the screen is on, unlocked and showing the
#   launcher, goes home, double-taps an empty spot, and waits for the screen to go off. The probe
#   logs "sleep handoff=<ns> call=<ns>" per tap; the first two taps of every block are warm-up (the
#   module's own restart-after-update also happens on the first screen-off).
#   TAP_X / TAP_Y: an empty spot on the home page (default: under the At a Glance widget).
set -euo pipefail
L=com.google.android.apps.nexuslauncher
OUT=$1; ROUNDS=${2:-2}; TAPS=${3:-12}
X=${TAP_X:-675}; Y=${TAP_Y:-825}
: > "$OUT"

awake_home() {
    adb shell 'dumpsys power | grep -q "mWakefulness=Awake" &&
               dumpsys window | grep -q "isKeyguardShowing=false" &&
               dumpsys window | grep -q "mCurrentFocus=.*nexuslauncher"' 2>/dev/null
}
asleep() { ! adb shell 'dumpsys power | grep -q "mWakefulness=Awake"' 2>/dev/null; }  # Asleep or Dozing (always-on display)

for r in $(seq "$ROUNDS"); do
  order="b c"; [ $((r % 2)) -eq 0 ] && order="c b"
  for v in $order; do
    adb install -r "tprobe-$v.apk" >/dev/null
    adb shell su -c "am force-stop $L"; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 8
    adb logcat -c
    for t in $(seq "$TAPS"); do
      until awake_home; do sleep 0.5; done
      adb shell input keyevent KEYCODE_HOME; sleep 1.5
      until awake_home; do sleep 0.5; done
      echo "round $r $v tap $t: double tapping; unlock the phone when it goes dark" >&2
      adb shell "input tap $X $Y & sleep 0.1; input tap $X $Y; wait"
      for _ in $(seq 20); do asleep && break; sleep 0.5; done
      asleep || echo "round $r $v tap $t: screen did not turn off" >&2
      sleep 1
    done
    adb logcat -d -s PLEProbe:I | grep "sleep handoff=" | sed "s/^/$r $v /" | tee -a "$OUT"
  done
done
