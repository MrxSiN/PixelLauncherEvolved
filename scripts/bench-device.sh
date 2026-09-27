#!/usr/bin/env bash
# Interleaved baseline/candidate benchmark of the module inside Pixel Launcher.
#
# usage: bench-device.sh <baseline.apk> <candidate.apk> <out-dir> [rounds] [cycles]
#   WORKLOADS="overview drawer" (default) chooses which workloads run, in order.
#
# Needs a rooted device with the module enabled in LSPosed for Pixel Launcher and
# both APKs signed with the installed module's key (installed with `install -r`).
# Each round installs one variant, restarts the launcher, warms it up, then runs:
#   overview: KEYCODE_APP_SWITCH from home, wait, KEYCODE_HOME, wait   (x cycles)
#   drawer:   suspend/unsuspend a stock app, which rebuilds the app list (x cycles)
#   home:     swipe between home pages, open the drawer, fling it, go home  (x cycles)
# Per workload it records: ART cumulative bytes allocated / GC count / GC time
# (from the SIGQUIT dump, MB granularity), launcher main-thread CPU ns
# (/proc/<pid>/task/<pid>/schedstat) and gfxinfo frame stats for the launcher.
set -euo pipefail

BASE=$1 CAND=$2 OUT=$3 ROUNDS=${4:-3} CYCLES=${5:-60}
LAUNCHER=com.google.android.apps.nexuslauncher
MODULE=io.github.mrxsin.pixellauncherevolved
TOGGLE_APP=com.google.android.calculator
mkdir -p "$OUT"
CSV="$OUT/results.csv"
echo "round,variant,workload,cycles,alloc_mb,gc_count,gc_ms,main_cpu_ns,frames,janky,p50_ms,p90_ms,p95_ms,p99_ms,thermal" > "$CSV"

sh_() { adb shell "$@" | tr -d '\r'; }
su_() { adb shell "su -c '$*'" | tr -d '\r'; }

pid() { sh_ pidof $LAUNCHER; }

# Cumulative ART counters of the launcher: "<alloc_mb> <gc_count> <gc_ms>".
art_stats() {
    local p=$1 before
    before=$(su_ "ls -t /data/anr | head -1")
    su_ "kill -3 $p"
    local f
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        sleep 1
        f=$(su_ "ls -t /data/anr | head -1")
        [ "$f" != "$before" ] && break
    done
    sleep 1
    su_ "cat /data/anr/$f" | awk -v pid="$p" '
        /^----- pid / { mine = ($3 == pid) }
        mine && /^Total bytes allocated/ { v=$4; u=$4; sub(/[A-Za-z]+$/, "", v); sub(/^[0-9.]+/, "", u);
            m = (u=="GB") ? v*1024 : (u=="MB") ? v : (u=="KB") ? v/1024 : v/1048576; a=m }
        mine && /^Total GC count:/ { c=$4 }
        mine && /^Total GC time:/ { t=$4; if (t ~ /ms$/) { sub(/ms$/,"",t) } else if (t ~ /us$/) { sub(/us$/,"",t); t/=1000 } else { sub(/s$/,"",t); t*=1000 } }
        END { printf "%s %s %s\n", a, c, t }'
}

main_cpu() { sh_ cat /proc/$1/task/$1/schedstat | awk '{print $1}'; }

gfx() {
    sh_ dumpsys gfxinfo $LAUNCHER | awk '
        /^Total frames rendered:/ && !f { f=$4 }
        /^Janky frames:/ && !j { j=$3 }
        /^50th percentile:/ && !a { a=$3 } /^90th percentile:/ && !b { b=$3 }
        /^95th percentile:/ && !c { c=$3 } /^99th percentile:/ && !d { d=$3 }
        END { gsub(/ms/,"",a); gsub(/ms/,"",b); gsub(/ms/,"",c); gsub(/ms/,"",d); printf "%s %s %s %s %s %s\n", f, j, a, b, c, d }'
}

thermal() { sh_ dumpsys thermalservice | awk '/^Thermal Status:/ {print $3; exit}'; }

overview() {
    for _ in $(seq "$CYCLES"); do
        sh_ input keyevent KEYCODE_APP_SWITCH; sleep 1.5
        sh_ input keyevent KEYCODE_HOME; sleep 1.0
    done
}

home() {
    local size w h
    size=$(sh_ wm size | awk '{print $3}'); w=${size%x*}; h=${size#*x}
    for _ in $(seq "$CYCLES"); do
        sh_ input swipe $((w * 8 / 10)) $((h / 2)) $((w * 2 / 10)) $((h / 2)) 200; sleep 0.6
        sh_ input swipe $((w * 2 / 10)) $((h / 2)) $((w * 8 / 10)) $((h / 2)) 200; sleep 0.6
        sh_ input swipe $((w / 2)) $((h * 9 / 10)) $((w / 2)) $((h * 3 / 10)) 200; sleep 0.8
        sh_ input swipe $((w / 2)) $((h * 8 / 10)) $((w / 2)) $((h * 3 / 10)) 100; sleep 1.0
        sh_ input keyevent KEYCODE_HOME; sleep 0.8
    done
}

drawer() {
    for _ in $(seq "$CYCLES"); do
        su_ "pm suspend $TOGGLE_APP" >/dev/null; sleep 0.7
        su_ "pm unsuspend $TOGGLE_APP" >/dev/null; sleep 0.7
    done
}

measure() { # round variant workload
    local p=$1 before after cpu0 cpu1
    shift
    sh_ dumpsys gfxinfo $LAUNCHER reset >/dev/null
    before=$(art_stats "$p"); cpu0=$(main_cpu "$p")
    "$3"
    cpu1=$(main_cpu "$p"); after=$(art_stats "$p")
    [ "$(pid)" = "$p" ] || { echo "launcher restarted during $3" >&2; exit 1; }
    read -r a0 c0 t0 <<<"$before"; read -r a1 c1 t1 <<<"$after"
    read -r fr jk p50 p90 p95 p99 <<<"$(gfx)"
    echo "$1,$2,$3,$CYCLES,$(awk "BEGIN{print $a1-$a0}"),$((c1-c0)),$(awk "BEGIN{print $t1-$t0}"),$((cpu1-cpu0)),$fr,$jk,$p50,$p90,$p95,$p99,$(thermal)" | tee -a "$CSV"
}

cleanup() { su_ "pm unsuspend $TOGGLE_APP" >/dev/null || true; sh_ svc power stayon false || true; }
trap cleanup EXIT
sh_ svc power stayon true
sh_ input keyevent KEYCODE_WAKEUP

for r in $(seq "$ROUNDS"); do
    # Alternate which variant goes first so drift does not favour one side.
    order="baseline candidate"; [ $((r % 2)) -eq 0 ] && order="candidate baseline"
    for v in $order; do
        apk=$BASE; [ $v = candidate ] && apk=$CAND
        adb install -r "$apk" >/dev/null
        su_ "am force-stop $LAUNCHER"
        sleep 2
        sh_ input keyevent KEYCODE_HOME
        sleep 15
        p=$(pid)
        # Warm-up so JIT state is comparable: a few of each workload, unmeasured.
        CYCLES_SAVED=$CYCLES; CYCLES=5
        for w in ${WORKLOADS:-overview drawer}; do "$w"; done
        CYCLES=$CYCLES_SAVED
        for w in ${WORKLOADS:-overview drawer}; do measure "$p" "$r" "$v" "$w"; done
    done
done
echo "done: $CSV"
